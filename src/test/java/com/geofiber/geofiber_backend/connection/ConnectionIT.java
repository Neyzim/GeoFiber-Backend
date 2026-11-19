package com.geofiber.geofiber_backend.connection;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionIT extends IntegrationTest {
    private static final String API = "/api/v1";
    private UUID dio;
    private UUID first;
    private UUID second;

    @BeforeEach
    void network() {
        dio = fx.box("DIO", null, 1);
        UUID cable = fx.cable(12);
        fx.attachToBox(cable, dio);
        UUID tube = fx.tube(cable, 1, 12);
        first = fx.fiber(tube, 1, 1);
        second = fx.fiber(tube, 2, 2);
    }

    private String connections() {
        return API + "/boxes/" + dio + "/connections";
    }

    private String connect(UUID a, String endA, UUID b, String endB, int expectedStatus) {
        return fx.postJson(connections(), """
            {"fiberAId":"%s","fiberAEnd":"%s","fiberBId":"%s","fiberBEnd":"%s","attenuationDb":0.3}"""
            .formatted(a, endA, b, endB), expectedStatus);
    }

    private String fiberStatus(UUID fiber) {
        return jdbc.queryForObject("SELECT status FROM fiber WHERE id = ?", String.class, fiber);
    }

    @Test
    void connectsTwoFiberEndsWithConnector() {
        String body = connect(first, "B", second, "A", 201);

        assertEquals("CONNECTOR", JsonPath.read(body, "$.type"));
        assertEquals(dio.toString(), JsonPath.read(body, "$.boxId"));
        assertEquals(first.toString(), JsonPath.read(body, "$.fiberAId"));
        assertEquals("A", JsonPath.read(body, "$.fiberBEnd"));
        assertEquals(0.3, (Double) JsonPath.read(body, "$.attenuationDb"));
        assertEquals("OCCUPIED", fiberStatus(first));
        assertEquals("OCCUPIED", fiberStatus(second));
        assertEquals(1, (Integer) JsonPath.read(fx.getJson(connections(), 200), "$.length()"));
    }

    @Test
    void rejectsSameFiber() {
        connect(first, "A", first, "B", 400);
    }

    @Test
    void rejectsEndAlreadySpliced() {
        fx.tray(dio, 1, 12);
        fx.postJson(API + "/boxes/" + dio + "/splices",
            "{\"trayNumber\":1,\"inputFiberId\":\"" + first + "\",\"outputFiberId\":\"" + second + "\"}", 201);
        UUID third = fx.fiber(fx.tube(attachedCable(), 1, 12), 1, 1);

        String body = connect(first, "B", third, "A", 409);

        assertEquals("Fiber end B is not available", JsonPath.read(body, "$.detail"));
    }

    @Test
    void rejectsCableNotAttached() {
        UUID loose = fx.fiber(fx.tube(fx.cable(12), 1, 12), 1, 1);

        String body = connect(first, "B", loose, "A", 409);

        assertEquals("Cable is not attached to this box", JsonPath.read(body, "$.detail"));
    }

    @Test
    void deleteReleasesEnds() {
        String id = JsonPath.read(connect(first, "B", second, "A", 201), "$.id");

        fx.delete(connections() + "/" + id, 204);

        assertEquals("AVAILABLE", fiberStatus(first));
        assertEquals("AVAILABLE", fiberStatus(second));
        assertEquals("[]", fx.getJson(connections(), 200));
    }

    @Test
    void cannotDeleteFusionHere() {
        fx.tray(dio, 1, 12);
        fx.postJson(API + "/boxes/" + dio + "/splices",
            "{\"trayNumber\":1,\"inputFiberId\":\"" + first + "\",\"outputFiberId\":\"" + second + "\"}", 201);
        UUID fusion = jdbc.queryForObject("SELECT connection_id FROM splice", UUID.class);

        String body = fx.delete(connections() + "/" + fusion, 409);

        assertEquals("Fusions are removed through the splice endpoint", JsonPath.read(body, "$.detail"));
    }

    @Test
    void spliceStillWorksAndReportsBox() {
        fx.tray(dio, 1, 12);
        fx.postJson(API + "/boxes/" + dio + "/splices",
            "{\"trayNumber\":1,\"attenuationDb\":0.05,\"inputFiberId\":\"" + first + "\",\"outputFiberId\":\"" + second + "\"}",
            201);

        assertEquals(dio, jdbc.queryForObject("SELECT box_id FROM optical_connection", UUID.class));
        assertEquals("FUSION", jdbc.queryForObject("SELECT connection_type FROM optical_connection", String.class));
        assertEquals("[]", fx.getJson(connections(), 200));
    }

    private UUID attachedCable() {
        UUID cable = fx.cable(12);
        fx.attachToBox(cable, dio);
        return cable;
    }
}
