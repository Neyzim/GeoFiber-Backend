package com.geofiber.geofiber_backend.customer;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CustomerStatusIT extends IntegrationTest {
    private static final String API = "/api/v1";

    private String servicePortsUrl;
    private List<String> ports;
    private List<String> feedingOutputs;

    @BeforeEach
    void ctoWithSplitter() {
        UUID cto = fx.box("CTO", 8, null);
        servicePortsUrl = API + "/boxes/" + cto + "/service-ports";
        fx.postJson(API + "/boxes/" + cto + "/splitters", "{\"ratio\":\"RATIO_1_2\",\"balanced\":true}", 201);
        String list = fx.getJson(servicePortsUrl, 200);
        ports = JsonPath.read(list, "$[*].id");
        feedingOutputs = JsonPath.read(list, "$[*].splitterPortId");
    }

    private static String customerBody(String status, String servicePortId) {
        return """
            {"name":"Cliente","contractCode":"C-1","location":{"longitude":-46.62,"latitude":-23.54},
             "status":%s,"servicePortId":%s}""".formatted(quoted(status), quoted(servicePortId));
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String createCustomerOn(String port) {
        return JsonPath.read(fx.postJson(API + "/customers", customerBody(null, port), 201), "$.id");
    }

    private String portStatus(int index) {
        return JsonPath.read(fx.getJson(servicePortsUrl, 200), "$[" + index + "].status");
    }

    private String updatePort(int index, String status, int expected) {
        return fx.putJson(servicePortsUrl + "/" + ports.get(index), """
            {"portNumber":%d,"splitterPortId":"%s","status":%s}"""
            .formatted(index + 1, feedingOutputs.get(index), quoted(status)), expected);
    }

    @Test
    void newCustomerIsActiveAndHistoryStarts() {
        String created = fx.postJson(API + "/customers", customerBody(null, ports.get(0)), 201);
        String id = JsonPath.read(created, "$.id");

        assertEquals("ACTIVE", JsonPath.read(created, "$.status"));
        String history = fx.getJson(API + "/customers/" + id + "/history", 200);
        assertEquals(List.of("ACTIVE"), JsonPath.read(history, "$[*].status"));
        assertEquals(ports.get(0), JsonPath.read(history, "$[0].servicePortId"));
    }

    @Test
    void blockedCustomerKeepsPort() {
        String id = createCustomerOn(ports.get(0));

        String blocked = fx.putJson(API + "/customers/" + id, customerBody("BLOCKED", ports.get(0)), 200);

        assertEquals("BLOCKED", JsonPath.read(blocked, "$.status"));
        assertEquals(ports.get(0), JsonPath.read(blocked, "$.servicePortId"));
        assertEquals("OCCUPIED", portStatus(0));
    }

    @Test
    void cancellingReleasesPortButKeepsCustomerAndHistory() {
        String id = createCustomerOn(ports.get(0));
        fx.putJson(API + "/customers/" + id, customerBody("BLOCKED", ports.get(0)), 200);

        String cancelled = fx.putJson(API + "/customers/" + id, customerBody("CANCELLED", ports.get(0)), 200);

        assertEquals("CANCELLED", JsonPath.read(cancelled, "$.status"));
        assertNull(JsonPath.read(cancelled, "$.servicePortId"));
        assertEquals("FREE", portStatus(0));
        assertEquals("CANCELLED", JsonPath.read(fx.getJson(API + "/customers/" + id, 200), "$.status"));
        String history = fx.getJson(API + "/customers/" + id + "/history", 200);
        assertEquals(List.of("ACTIVE", "BLOCKED", "CANCELLED"), JsonPath.read(history, "$[*].status"));
        assertEquals(Arrays.asList(ports.get(0), ports.get(0), null), JsonPath.read(history, "$[*].servicePortId"));
    }

    @Test
    void cancelledCustomerCannotTakeAPort() {
        String id = createCustomerOn(ports.get(0));
        fx.putJson(API + "/customers/" + id, customerBody("CANCELLED", null), 200);

        String body = fx.putJson(API + "/customers/" + id, customerBody("CANCELLED", ports.get(1)), 409);

        assertEquals("A cancelled customer cannot use a service port", JsonPath.read(body, "$.detail"));
        fx.postJson(API + "/customers", customerBody("CANCELLED", ports.get(1)).replace("C-1", "C-2"), 409);
    }

    @Test
    void reactivatedCustomerTakesAPortAgain() {
        String id = createCustomerOn(ports.get(0));
        fx.putJson(API + "/customers/" + id, customerBody("CANCELLED", null), 200);

        String active = fx.putJson(API + "/customers/" + id, customerBody("ACTIVE", ports.get(1)), 200);

        assertEquals(ports.get(1), JsonPath.read(active, "$.servicePortId"));
        assertEquals("OCCUPIED", portStatus(1));
    }

    @Test
    void portServingCustomerCanBeFaultyAndRecover() {
        String id = createCustomerOn(ports.get(0));

        String faulty = updatePort(0, "FAULTY", 200);
        assertEquals("FAULTY", JsonPath.read(faulty, "$.status"));
        assertEquals(id, JsonPath.read(faulty, "$.customerId"));

        assertEquals("OCCUPIED", JsonPath.read(updatePort(0, null, 200), "$.status"));
    }

    @Test
    void faultyPortStaysFaultyWhenCustomerLeavesAndRejectsNewCustomers() {
        String id = createCustomerOn(ports.get(0));
        updatePort(0, "FAULTY", 200);

        fx.putJson(API + "/customers/" + id, customerBody("ACTIVE", ports.get(1)), 200);

        assertEquals("FAULTY", portStatus(0));
        fx.postJson(API + "/customers", customerBody(null, ports.get(0)).replace("C-1", "C-2"), 409);
    }

    @Test
    void unknownCustomerStatusIs400() {
        fx.postJson(API + "/customers", customerBody("SUSPENDED", null), 400);
    }
}
