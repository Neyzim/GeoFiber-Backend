package com.geofiber.geofiber_backend.readmodel;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadModelIT extends IntegrationTest {
    private static final String API = "/api/v1";

    private static String structure(String... tubes) {
        return "{\"tubes\":[" + String.join(",", tubes) + "]}";
    }

    private static String tube(int number, int capacity, String color) {
        return "{\"tubeNumber\":%d,\"capacity\":%d,\"color\":\"%s\"}".formatted(number, capacity, color);
    }

    @Test
    void corsAllowsFrontendOriginOnly() throws Exception {
        mvc.perform(options(API + "/boxes")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));

        mvc.perform(options(API + "/boxes")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }

    @Test
    void structureCreatesTubesAndFibersWithStandardColors() {
        UUID cable = fx.cable(24);

        String body = fx.postJson(API + "/cables/" + cable + "/structure",
            structure(tube(1, 12, "GREEN"), tube(2, 12, "YELLOW")), 201);

        assertEquals(List.of(1, 2), JsonPath.read(body, "$.tubes[*].tubeNumber"));
        assertEquals(12, (Integer) JsonPath.read(body, "$.tubes[1].fibers.length()"));
        assertEquals("GREEN", JsonPath.read(body, "$.tubes[1].fibers[0].color"));
        assertEquals("YELLOW", JsonPath.read(body, "$.tubes[1].fibers[1].color"));
        assertEquals(13, (Integer) JsonPath.read(body, "$.tubes[1].fibers[0].absoluteNumber"));
        assertEquals(24, (Integer) JsonPath.read(fx.getJson(API + "/cables/" + cable + "/fibers", 200), "$.length()"));
        assertEquals(48, jdbc.queryForObject("SELECT count(*) FROM fiber_termination", Integer.class));
    }

    @Test
    void structureContinuesNumberingAfterExistingTubes() {
        UUID cable = fx.cable(24);
        fx.postJson(API + "/cables/" + cable + "/structure", structure(tube(1, 12, "GREEN")), 201);

        String body = fx.postJson(API + "/cables/" + cable + "/structure", structure(tube(2, 12, "YELLOW")), 201);

        assertEquals(13, (Integer) JsonPath.read(body, "$.tubes[0].fibers[0].absoluteNumber"));
    }

    @Test
    void structureIsAtomic() {
        UUID cable = fx.cable(12);

        fx.postJson(API + "/cables/" + cable + "/structure", structure(tube(1, 12, "GREEN"), tube(2, 12, "YELLOW")), 400);
        fx.postJson(API + "/cables/" + cable + "/structure", structure(tube(1, 6, "GREEN"), tube(1, 6, "YELLOW")), 409);

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM loose_tube", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM fiber", Integer.class));
    }

    @Test
    void structureRejectsEmptyList() {
        fx.postJson(API + "/cables/" + fx.cable(12) + "/structure", structure(), 400);
    }

    @Test
    void listsCablesAttachedToBoxAndOlt() {
        UUID olt = fx.olt();
        UUID ceo = fx.box("CEO", null, null);
        UUID cable = fx.cable(12);
        fx.putJson(API + "/cables/" + cable + "/attachments", """
            [{"role":"START","oltId":"%s"},{"role":"END","boxId":"%s"}]""".formatted(olt, ceo), 200);

        String atBox = fx.getJson(API + "/boxes/" + ceo + "/cables", 200);
        String atOlt = fx.getJson(API + "/olts/" + olt + "/cables", 200);

        assertEquals(List.of(cable.toString()), JsonPath.read(atBox, "$[*].cableId"));
        assertEquals("END", JsonPath.read(atBox, "$[0].attachmentRole"));
        assertEquals("CABO-1", JsonPath.read(atBox, "$[0].name"));
        assertEquals("START", JsonPath.read(atOlt, "$[0].attachmentRole"));
        assertEquals("[]", fx.getJson(API + "/boxes/" + fx.box("CTO", 8, null) + "/cables", 200));
    }

    @Test
    void cableFibersShowWhatEachEndIsConnectedTo() {
        UUID olt = fx.olt();
        UUID ceo = fx.box("CEO", null, 1);
        fx.tray(ceo, 1, 12);
        UUID cable = fx.cable(12);
        fx.putJson(API + "/cables/" + cable + "/attachments", """
            [{"role":"START","oltId":"%s"},{"role":"END","boxId":"%s"}]""".formatted(olt, ceo), 200);
        String created = fx.postJson(API + "/cables/" + cable + "/structure", structure(tube(1, 12, "GREEN")), 201);
        String first = JsonPath.read(created, "$.tubes[0].fibers[0].id");
        String second = JsonPath.read(created, "$.tubes[0].fibers[1].id");
        String port = JsonPath.read(fx.postJson(API + "/olts/" + olt + "/ports",
            "{\"slotNumber\":1,\"portNumber\":1,\"fiberId\":\"" + first + "\"}", 201), "$.id");
        String splice = JsonPath.read(fx.postJson(API + "/boxes/" + ceo + "/splices",
            "{\"trayNumber\":1,\"inputFiberId\":\"%s\",\"outputFiberId\":\"%s\"}".formatted(first, second), 201), "$.id");

        String fibers = fx.getJson(API + "/cables/" + cable + "/fibers", 200);

        assertEquals("OCCUPIED", JsonPath.read(fibers, "$[0].status"));
        assertEquals(1, (Integer) JsonPath.read(fibers, "$[0].tubeNumber"));
        assertEquals("OLT_PORT", JsonPath.read(fibers, "$[0].ends[0].link.type"));
        assertEquals(port, JsonPath.read(fibers, "$[0].ends[0].link.id"));
        assertEquals(olt.toString(), JsonPath.read(fibers, "$[0].ends[0].link.elementId"));
        assertEquals("FUSION", JsonPath.read(fibers, "$[0].ends[1].link.type"));
        assertEquals(splice, JsonPath.read(fibers, "$[0].ends[1].link.id"));
        assertEquals(second, JsonPath.read(fibers, "$[0].ends[1].link.peerFiberId"));
        assertEquals("A", JsonPath.read(fibers, "$[0].ends[1].link.peerFiberEnd"));
        assertEquals(ceo.toString(), JsonPath.read(fibers, "$[1].ends[0].link.elementId"));
        assertEquals((Object) null, JsonPath.read(fibers, "$[2].ends[0].link"));
    }

    @Test
    void boxOverviewGathersEverythingInTheBox() {
        UUID cto = fx.box("CTO", 8, 1);
        UUID cable = fx.cable(12);
        fx.attachToBox(cable, cto);
        fx.tray(cto, 1, 12);
        fx.postJson(API + "/boxes/" + cto + "/splitters", "{\"ratio\":\"RATIO_1_2\",\"balanced\":true}", 201);
        String servicePort = JsonPath.read(fx.getJson(API + "/boxes/" + cto + "/service-ports", 200), "$[0].id");
        fx.postJson(API + "/customers", """
            {"name":"Maria","contractCode":"C-1","location":{"longitude":-46.62,"latitude":-23.54},
             "servicePortId":"%s"}""".formatted(servicePort), 201);

        String overview = fx.getJson(API + "/boxes/" + cto + "/overview", 200);

        assertEquals("CTO-1", JsonPath.read(overview, "$.box.name"));
        assertEquals(List.of(cable.toString()), JsonPath.read(overview, "$.cables[*].cableId"));
        assertEquals(1, (Integer) JsonPath.read(overview, "$.trays.length()"));
        assertEquals(1, (Integer) JsonPath.read(overview, "$.splitters.length()"));
        assertEquals(2, (Integer) JsonPath.read(overview, "$.servicePorts.length()"));
        assertEquals(List.of("Maria"), JsonPath.read(overview, "$.customers[*].name"));
        assertEquals(0, (Integer) JsonPath.read(overview, "$.splices.length()"));
        assertEquals(0, (Integer) JsonPath.read(overview, "$.connections.length()"));
    }

    @Test
    void readEndpointsReturn404ForUnknownIds() {
        UUID unknown = UUID.randomUUID();
        fx.getJson(API + "/boxes/" + unknown + "/overview", 404);
        fx.getJson(API + "/boxes/" + unknown + "/cables", 404);
        fx.getJson(API + "/olts/" + unknown + "/cables", 404);
        fx.getJson(API + "/cables/" + unknown + "/fibers", 404);
        fx.postJson(API + "/cables/" + unknown + "/structure", structure(tube(1, 12, "GREEN")), 404);
    }
}
