package com.geofiber.geofiber_backend.crud;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrudIT extends IntegrationTest {
    private static final String API = "/api/v1";

    private static String detail(String body) {
        return JsonPath.read(body, "$.detail");
    }

    private String tubeUrl(UUID cable, UUID tube) {
        return API + "/cables/" + cable + "/tubes/" + tube;
    }

    private String fiberUrl(UUID tube, UUID fiber) {
        return API + "/tubes/" + tube + "/fibers/" + fiber;
    }

    /** Fibra ligada a uma porta de OLT (cabo anexado à OLT). */
    private UUID connectToOlt(UUID cable, UUID fiber) {
        UUID olt = fx.olt();
        UUID box = fx.box("CEO", null, null);
        fx.putJson(API + "/cables/" + cable + "/attachments", """
            [{"role":"START","oltId":"%s"},{"role":"END","boxId":"%s"}]""".formatted(olt, box), 200);
        fx.postJson(API + "/olts/" + olt + "/ports",
            "{\"slotNumber\":1,\"portNumber\":1,\"fiberId\":\"" + fiber + "\"}", 201);
        return olt;
    }

    @Test
    void updatesTubeAndRejectsCapacityBelowFibers() {
        UUID cable = fx.cable(24);
        UUID tube = fx.tube(cable, 1, 12);
        fx.fiber(tube, 6, 6);

        String updated = fx.putJson(tubeUrl(cable, tube), "{\"tubeNumber\":2,\"capacity\":8,\"color\":\"BLUE\"}", 200);
        assertEquals(2, (Integer) JsonPath.read(updated, "$.tubeNumber"));

        assertEquals("Tube capacity is below its highest fiber number",
            detail(fx.putJson(tubeUrl(cable, tube), "{\"tubeNumber\":2,\"capacity\":5,\"color\":\"BLUE\"}", 400)));
        assertEquals("Tube capacities exceed the cable fiber capacity of 24",
            detail(fx.putJson(tubeUrl(cable, tube), "{\"tubeNumber\":2,\"capacity\":25,\"color\":\"BLUE\"}", 400)));
    }

    @Test
    void deletesTubeOnlyWithoutFibers() {
        UUID cable = fx.cable(24);
        UUID tube = fx.tube(cable, 1, 12);
        UUID fiber = fx.fiber(tube, 1, 1);

        assertEquals("Tube still has fibers", detail(fx.delete(tubeUrl(cable, tube), 409)));
        fx.delete(fiberUrl(tube, fiber), 204);
        fx.delete(tubeUrl(cable, tube), 204);
        assertEquals("[]", fx.getJson(API + "/cables/" + cable + "/tubes", 200));
    }

    @Test
    void updatesFiberAndBlocksDeletingConnectedFiber() {
        UUID cable = fx.cable(12);
        UUID tube = fx.tube(cable, 1, 12);
        UUID fiber = fx.fiber(tube, 1, 1);

        String updated = fx.putJson(fiberUrl(tube, fiber), "{\"numberInTube\":3,\"absoluteNumber\":3,\"color\":\"RED\"}", 200);
        assertEquals("RED", JsonPath.read(updated, "$.color"));
        fx.putJson(fiberUrl(tube, fiber), "{\"numberInTube\":13,\"absoluteNumber\":3,\"color\":\"RED\"}", 400);

        connectToOlt(cable, fiber);
        assertEquals("Fiber is connected", detail(fx.delete(fiberUrl(tube, fiber), 409)));
    }

    @Test
    void changesFiberStatusOnlyWhenDisconnected() {
        UUID cable = fx.cable(12);
        UUID tube = fx.tube(cable, 1, 12);
        UUID fiber = fx.fiber(tube, 1, 1);
        UUID other = fx.fiber(tube, 2, 2);

        assertEquals("BROKEN",
            JsonPath.read(fx.putJson(fiberUrl(tube, fiber) + "/status", "{\"status\":\"BROKEN\"}", 200), "$.status"));
        fx.putJson(fiberUrl(tube, fiber) + "/status", "{\"status\":\"OCCUPIED\"}", 400);
        fx.putJson(fiberUrl(tube, fiber) + "/status", "{\"status\":\"AVAILABLE\"}", 200);

        connectToOlt(cable, other);
        assertEquals("Fiber is connected",
            detail(fx.putJson(fiberUrl(tube, other) + "/status", "{\"status\":\"RESERVED\"}", 409)));
    }

    @Test
    void updatesAndDeletesTray() {
        UUID box = fx.box("CEO", null, 2);
        UUID tray = fx.tray(box, 1, 12);
        UUID cable = fx.cable(12);
        fx.attachToBox(cable, box);
        UUID tube = fx.tube(cable, 1, 12);
        fx.postJson(API + "/boxes/" + box + "/splices", """
            {"trayNumber":1,"inputFiberId":"%s","outputFiberId":"%s"}"""
            .formatted(fx.fiber(tube, 1, 1), fx.fiber(tube, 2, 2)), 201);
        String url = API + "/boxes/" + box + "/trays/" + tray;

        assertEquals(24, (Integer) JsonPath.read(fx.putJson(url, "{\"trayNumber\":1,\"capacity\":24}", 200), "$.capacity"));
        fx.putJson(url, "{\"trayNumber\":3,\"capacity\":24}", 400);
        assertEquals("Tray still has splices", detail(fx.delete(url, 409)));

        UUID empty = fx.tray(box, 2, 12);
        fx.delete(API + "/boxes/" + box + "/trays/" + empty, 204);
    }

    @Test
    void trayCapacityCannotDropBelowUsedPosition() {
        UUID box = fx.box("CEO", null, 1);
        UUID tray = fx.tray(box, 1, 12);
        UUID cable = fx.cable(12);
        fx.attachToBox(cable, box);
        UUID tube = fx.tube(cable, 1, 12);
        for (int pair = 0; pair < 2; pair++) {
            fx.postJson(API + "/boxes/" + box + "/splices", """
                {"trayNumber":1,"inputFiberId":"%s","outputFiberId":"%s"}"""
                .formatted(fx.fiber(tube, pair * 2 + 1, pair * 2 + 1), fx.fiber(tube, pair * 2 + 2, pair * 2 + 2)), 201);
        }

        assertEquals("Tray capacity is below its highest used position",
            detail(fx.putJson(API + "/boxes/" + box + "/trays/" + tray, "{\"trayNumber\":1,\"capacity\":1}", 400)));
    }

    @Test
    void updatesSplitterBalanceButNotRatio() {
        UUID box = fx.box("CEO", null, null);
        String splitter = fx.postJson(API + "/boxes/" + box + "/splitters", "{\"ratio\":\"RATIO_1_4\",\"balanced\":true}", 201);
        String url = API + "/boxes/" + box + "/splitters/" + JsonPath.read(splitter, "$.id");

        assertEquals(false, JsonPath.read(fx.putJson(url, "{\"ratio\":\"RATIO_1_4\",\"balanced\":false}", 200), "$.balanced"));
        assertEquals("Splitter ratio cannot be changed",
            detail(fx.putJson(url, "{\"ratio\":\"RATIO_1_8\",\"balanced\":false}", 400)));
    }

    @Test
    void deletingSplitterServingCustomerIsRejected() {
        UUID cto = fx.box("CTO", 8, null);
        String splitter = fx.postJson(API + "/boxes/" + cto + "/splitters", "{\"ratio\":\"RATIO_1_2\",\"balanced\":true}", 201);
        String port = JsonPath.read(fx.getJson(API + "/boxes/" + cto + "/service-ports", 200), "$[0].id");
        fx.postJson(API + "/customers", """
            {"name":"C","contractCode":"C-1","location":{"longitude":0,"latitude":0},"servicePortId":"%s"}"""
            .formatted(port), 201);

        assertEquals("Splitter still has connections",
            detail(fx.delete(API + "/boxes/" + cto + "/splitters/" + JsonPath.read(splitter, "$.id"), 409)));
    }

    @Test
    void deletingIdleSplitterRemovesItsGeneratedServicePorts() {
        UUID cto = fx.box("CTO", 8, null);
        String splitter = fx.postJson(API + "/boxes/" + cto + "/splitters", "{\"ratio\":\"RATIO_1_2\",\"balanced\":true}", 201);

        fx.delete(API + "/boxes/" + cto + "/splitters/" + JsonPath.read(splitter, "$.id"), 204);

        assertEquals("[]", fx.getJson(API + "/boxes/" + cto + "/splitters", 200));
        assertEquals("[]", fx.getJson(API + "/boxes/" + cto + "/service-ports", 200));
    }

    @Test
    void deletesOltPortOnlyWithoutFiber() {
        UUID cable = fx.cable(12);
        UUID fiber = fx.fiber(fx.tube(cable, 1, 12), 1, 1);
        UUID olt = connectToOlt(cable, fiber);
        String connected = JsonPath.read(fx.getJson(API + "/olts/" + olt + "/ports", 200), "$[0].id");
        UUID idle = fx.oltPort(olt, 1, 2);

        assertEquals("OLT port is connected", detail(fx.delete(API + "/olts/" + olt + "/ports/" + connected, 409)));
        fx.delete(API + "/olts/" + olt + "/ports/" + idle, 204);
    }

    @Test
    void deletingCableWithTubesIsRejectedAndEmptyCableDropsAttachments() {
        UUID cable = fx.cable(12);
        UUID box = fx.box("CTO", 8, null);
        fx.attachToBox(cable, box);
        UUID tube = fx.tube(cable, 1, 12);

        assertEquals("Cable still has tubes", detail(fx.delete(API + "/cables/" + cable, 409)));
        fx.delete(tubeUrl(cable, tube), 204);
        fx.delete(API + "/cables/" + cable, 204);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM cable_attachment", Integer.class));
    }

    @Test
    void deletingBoxOrOltInUseIsRejected() {
        UUID box = fx.box("CEO", null, 1);
        fx.tray(box, 1, 12);
        UUID olt = fx.olt();
        fx.oltPort(olt, 1, 1);

        assertEquals("Box still has splitters, trays, service ports, connections or attached cables",
            detail(fx.delete(API + "/boxes/" + box, 409)));
        assertEquals("OLT still has ports or attached cables", detail(fx.delete(API + "/olts/" + olt, 409)));
        fx.delete(API + "/boxes/" + fx.box("CTO", 8, null), 204);
    }

    @Test
    void capacitiesCannotDropBelowUse() {
        UUID cable = fx.cable(24);
        fx.tube(cable, 1, 12);
        fx.tube(cable, 2, 12);
        assertEquals("Cable fiber capacity is below the total capacity of its tubes", detail(fx.putJson(API + "/cables/" + cable, """
            {"name":"CABO-1","role":"FEEDER","fiberCapacity":12,
             "coordinates":[{"longitude":-46.63,"latitude":-23.55},{"longitude":-46.62,"latitude":-23.54}]}""", 400)));

        UUID cto = fx.box("CTO", 8, null);
        fx.postJson(API + "/boxes/" + cto + "/splitters", "{\"ratio\":\"RATIO_1_8\",\"balanced\":true}", 201);
        assertEquals("Box supports at most 4 service ports", detail(fx.putJson(API + "/boxes/" + cto, """
            {"name":"CTO-1","type":"CTO","powerCapacity":4,"location":{"longitude":0,"latitude":0}}""", 400)));
    }

    @Test
    void freedSplicePositionIsReused() {
        UUID box = fx.box("CEO", null, 1);
        fx.tray(box, 1, 2);
        UUID cable = fx.cable(12);
        fx.attachToBox(cable, box);
        UUID tube = fx.tube(cable, 1, 12);
        String splicesUrl = API + "/boxes/" + box + "/splices";
        String spliceBody = "{\"trayNumber\":1,\"inputFiberId\":\"%s\",\"outputFiberId\":\"%s\"}";
        String first = fx.postJson(splicesUrl, spliceBody.formatted(fx.fiber(tube, 1, 1), fx.fiber(tube, 2, 2)), 201);
        fx.postJson(splicesUrl, spliceBody.formatted(fx.fiber(tube, 3, 3), fx.fiber(tube, 4, 4)), 201);
        fx.delete(splicesUrl + "/" + JsonPath.read(first, "$.id"), 204);

        String reused = fx.postJson(splicesUrl, spliceBody.formatted(fx.fiber(tube, 5, 5), fx.fiber(tube, 6, 6)), 201);

        assertEquals(1, (Integer) JsonPath.read(reused, "$.positionNumber"));
    }

    @Test
    void spliceAcceptsAndUsesProvidedPosition() {
        UUID box = fx.box("CEO", null, 1);
        fx.tray(box, 1, 3);
        UUID cable = fx.cable(12);
        fx.attachToBox(cable, box);
        UUID tube = fx.tube(cable, 1, 12);
        String splicesUrl = API + "/boxes/" + box + "/splices";
        String spliceBody = "{\"trayNumber\":1,\"inputFiberId\":\"%s\",\"outputFiberId\":\"%s\",\"positionNumber\":%d}";

        String splice2 = fx.postJson(splicesUrl, spliceBody.formatted(fx.fiber(tube, 1, 1), fx.fiber(tube, 2, 2), 2), 201);
        assertEquals(2, (Integer) JsonPath.read(splice2, "$.positionNumber"));

        // posição 2 já está ocupada
        assertEquals("Position 2 is already occupied in this tray",
            detail(fx.postJson(splicesUrl, spliceBody.formatted(fx.fiber(tube, 3, 3), fx.fiber(tube, 4, 4), 2), 409)));
    }

    @Test
    void spliceRejectsPositionOutOfRange() {
        UUID box = fx.box("CEO", null, 1);
        fx.tray(box, 1, 2);
        UUID cable = fx.cable(12);
        fx.attachToBox(cable, box);
        UUID tube = fx.tube(cable, 1, 12);
        String splicesUrl = API + "/boxes/" + box + "/splices";
        String spliceBody = "{\"trayNumber\":1,\"inputFiberId\":\"%s\",\"outputFiberId\":\"%s\",\"positionNumber\":%d}";

        assertEquals("Position 5 exceeds tray capacity of 2",
            detail(fx.postJson(splicesUrl, spliceBody.formatted(fx.fiber(tube, 1, 1), fx.fiber(tube, 2, 2), 5), 400)));
    }

    @Test
    void spliceStillFindsNextFreeWhenPositionNotProvided() {
        UUID box = fx.box("CEO", null, 1);
        fx.tray(box, 1, 3);
        UUID cable = fx.cable(12);
        fx.attachToBox(cable, box);
        UUID tube = fx.tube(cable, 1, 12);
        String splicesUrl = API + "/boxes/" + box + "/splices";
        String spliceBodyNoPos = "{\"trayNumber\":1,\"inputFiberId\":\"%s\",\"outputFiberId\":\"%s\"}";

        String first = fx.postJson(splicesUrl, spliceBodyNoPos.formatted(fx.fiber(tube, 1, 1), fx.fiber(tube, 2, 2)), 201);
        assertEquals(1, (Integer) JsonPath.read(first, "$.positionNumber"));

        String second = fx.postJson(splicesUrl, spliceBodyNoPos.formatted(fx.fiber(tube, 3, 3), fx.fiber(tube, 4, 4)), 201);
        assertEquals(2, (Integer) JsonPath.read(second, "$.positionNumber"));

        fx.delete(splicesUrl + "/" + JsonPath.read(first, "$.id"), 204);

        String third = fx.postJson(splicesUrl, spliceBodyNoPos.formatted(fx.fiber(tube, 5, 5), fx.fiber(tube, 6, 6)), 201);
        assertEquals(1, (Integer) JsonPath.read(third, "$.positionNumber"));
    }
}
