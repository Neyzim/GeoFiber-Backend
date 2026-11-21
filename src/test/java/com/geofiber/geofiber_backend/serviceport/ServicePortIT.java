package com.geofiber.geofiber_backend.serviceport;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServicePortIT extends IntegrationTest {
    private static final String API = "/api/v1";

    private String servicePorts(UUID box) {
        return API + "/boxes/" + box + "/service-ports";
    }

    private String splitter(UUID box, String ratio) {
        return fx.postJson(API + "/boxes/" + box + "/splitters", "{\"ratio\":\"" + ratio + "\",\"balanced\":true}", 201);
    }

    private UUID fiberAttachedTo(UUID box) {
        UUID cable = fx.cable(12);
        fx.attachToBox(cable, box);
        return fx.fiber(fx.tube(cable, 1, 12), 1, 1);
    }

    private String customer(String code, Object servicePortId, int expectedStatus) {
        return fx.postJson(API + "/customers", """
            {"name":"Cliente","contractCode":"%s","location":{"longitude":-46.62,"latitude":-23.54},
             "servicePortId":%s}""".formatted(code, servicePortId == null ? "null" : "\"" + servicePortId + "\""),
            expectedStatus);
    }

    @Test
    void splitterInCtoGeneratesOneServicePortPerOutput() {
        UUID cto = fx.box("CTO", 8, null);
        String splitter = splitter(cto, "RATIO_1_8");
        List<String> outputs = JsonPath.read(splitter, "$.ports[?(@.direction == 'OUTPUT')].id");

        String ports = fx.getJson(servicePorts(cto), 200);

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), JsonPath.read(ports, "$[*].portNumber"));
        assertEquals(outputs, JsonPath.read(ports, "$[*].splitterPortId"));
    }

    @Test
    void splitterBeyondCapacityIsRejectedAtomically() {
        UUID cto = fx.box("CTO", 4, null);

        String body = fx.postJson(API + "/boxes/" + cto + "/splitters", "{\"ratio\":\"RATIO_1_8\",\"balanced\":true}", 400);

        assertEquals("Box supports at most 4 service ports", JsonPath.read(body, "$.detail"));
        assertEquals("[]", fx.getJson(API + "/boxes/" + cto + "/splitters", 200));
        assertEquals("[]", fx.getJson(servicePorts(cto), 200));
    }

    @Test
    void splitterCanSkipServicePortGeneration() {
        UUID cto = fx.box("CTO", 4, null);
        fx.postJson(API + "/boxes/" + cto + "/splitters",
            "{\"ratio\":\"RATIO_1_8\",\"balanced\":true,\"createServicePorts\":false}", 201);
        assertEquals("[]", fx.getJson(servicePorts(cto), 200));
    }

    @Test
    void servicePortsOnlyInCto() {
        UUID ceo = fx.box("CEO", null, null);
        String body = fx.postJson(servicePorts(ceo), "{\"portNumber\":1}", 400);
        assertEquals("Only CTO boxes have service ports", JsonPath.read(body, "$.detail"));
    }

    @Test
    void servicePortFedByFiber() {
        UUID cto = fx.box("CTO", 8, null);
        UUID fiber = fiberAttachedTo(cto);

        String port = fx.postJson(servicePorts(cto), "{\"portNumber\":1,\"fiberId\":\"" + fiber + "\"}", 201);

        assertEquals(fiber.toString(), JsonPath.read(port, "$.fiberId"));
        assertEquals("B", JsonPath.read(port, "$.fiberEnd"));
        assertEquals("OCCUPIED", jdbc.queryForObject("SELECT status FROM fiber WHERE id = ?", String.class, fiber));
    }

    @Test
    void servicePortRejectsBothSources() {
        UUID cto = fx.box("CTO", 8, null);
        String splitter = splitter(cto, "RATIO_1_2");
        String output = JsonPath.read(splitter, "$.ports[1].id");
        UUID fiber = fiberAttachedTo(cto);

        fx.postJson(servicePorts(cto),
            "{\"portNumber\":9,\"splitterPortId\":\"" + output + "\",\"fiberId\":\"" + fiber + "\"}", 400);
    }

    @Test
    void customerConnectsToServicePortAndMovesBetweenPorts() {
        UUID cto = fx.box("CTO", 8, null);
        splitter(cto, "RATIO_1_2");
        List<String> ports = JsonPath.read(fx.getJson(servicePorts(cto), 200), "$[*].id");

        UUID customer = UUID.fromString(JsonPath.read(customer("C-1", ports.get(0), 201), "$.id"));
        assertEquals(List.of("OCCUPIED", "FREE"), JsonPath.read(fx.getJson(servicePorts(cto), 200), "$[*].status"));

        fx.putJson(API + "/customers/" + customer, """
            {"name":"Cliente","contractCode":"C-1","location":{"longitude":-46.62,"latitude":-23.54},
             "servicePortId":"%s"}""".formatted(ports.get(1)), 200);

        String after = fx.getJson(servicePorts(cto), 200);
        assertEquals(List.of("FREE", "OCCUPIED"), JsonPath.read(after, "$[*].status"));
        assertEquals(customer.toString(), JsonPath.read(after, "$[1].customerId"));
    }

    @Test
    void customerRejectsPortWithoutSource() {
        UUID cto = fx.box("CTO", 8, null);
        String port = fx.postJson(servicePorts(cto), "{\"portNumber\":1}", 201);

        String body = customer("C-1", JsonPath.read(port, "$.id"), 409);

        assertEquals("Service port has no optical source", JsonPath.read(body, "$.detail"));
    }

    @Test
    void splitterOutputFeedingServicePortCannotTakeFiber() {
        UUID cto = fx.box("CTO", 8, null);
        String splitter = splitter(cto, "RATIO_1_2");
        String output = JsonPath.read(splitter, "$.ports[1].id");
        UUID fiber = fiberAttachedTo(cto);

        String body = fx.putJson(API + "/splitters/" + JsonPath.read(splitter, "$.id") + "/ports/" + output,
            "{\"fiberId\":\"" + fiber + "\"}", 409);

        assertEquals("Splitter port already feeds a service port", JsonPath.read(body, "$.detail"));
    }

    @Test
    void cannotChangeCtoTypeWithServicePorts() {
        UUID cto = fx.box("CTO", 8, null);
        fx.postJson(servicePorts(cto), "{\"portNumber\":1}", 201);

        fx.putJson(API + "/boxes/" + cto, """
            {"name":"CTO-1","type":"CEO","location":{"longitude":-46.62,"latitude":-23.54}}""", 409);
    }

    @Test
    void deletingServicePortWithCustomerIsRejected() {
        UUID cto = fx.box("CTO", 8, null);
        splitter(cto, "RATIO_1_2");
        String port = JsonPath.read(fx.getJson(servicePorts(cto), 200), "$[0].id");
        customer("C-1", port, 201);

        fx.delete(servicePorts(cto) + "/" + port, 409);
        fx.delete(servicePorts(cto) + "/" + JsonPath.read(fx.getJson(servicePorts(cto), 200), "$[1].id"), 204);
    }

    @Test
    void fiberFedPortServingCustomerSwitchesToSplitterOutput() {
        UUID cto = fx.box("CTO", 8, null);
        String splitter = fx.postJson(API + "/boxes/" + cto + "/splitters",
            "{\"ratio\":\"RATIO_1_2\",\"balanced\":true,\"createServicePorts\":false}", 201);
        String output = JsonPath.read(splitter, "$.ports[1].id");
        UUID fiber = fiberAttachedTo(cto);
        String port = fx.postJson(servicePorts(cto), "{\"portNumber\":1,\"fiberId\":\"" + fiber + "\"}", 201);
        String portId = JsonPath.read(port, "$.id");
        customer("C-1", portId, 201);

        String switched = fx.putJson(servicePorts(cto) + "/" + portId,
            "{\"portNumber\":1,\"splitterPortId\":\"" + output + "\"}", 200);

        assertEquals(output, JsonPath.read(switched, "$.splitterPortId"));
        assertEquals("AVAILABLE", jdbc.queryForObject("SELECT status FROM fiber WHERE id = ?", String.class, fiber));
    }
}
