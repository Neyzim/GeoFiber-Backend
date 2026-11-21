package com.geofiber.geofiber_backend.trace;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.geofiber.geofiber_backend.support.NetworkFixtures;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceIT extends IntegrationTest {
    private static final String API = "/api/v1";
    private static final String LOC = "{\"longitude\":-46.62,\"latitude\":-23.54}";
    private static final String LINE = "[{\"longitude\":-46.63,\"latitude\":-23.55},{\"longitude\":-46.62,\"latitude\":-23.54}]";

    // ---------- 1. Caminho de fibra da caixa até a OLT, atravessando conector ----------

    @Test
    void upstreamFromBoxFollowsSplitterInputThroughConnectorToOltPort() {
        Scenario s = upstreamScenario(false);

        String json = fx.getJson(trace("BOX", s.cto, "UPSTREAM"), 200);

        assertEquals("BOX", JsonPath.read(json, "$.start.kind"));
        assertEquals("CTO-001", JsonPath.read(json, "$.start.label"));
        assertEquals("UPSTREAM", JsonPath.read(json, "$.direction"));
        assertEquals("REACHED_OLT", JsonPath.read(json, "$.status"));
        assertEquals(List.of("CABLE", "BOX", "CABLE", "OLT_PORT"), JsonPath.read(json, "$.steps[*].kind"));
        assertEquals("CABO-DIST-01", JsonPath.read(json, "$.steps[0].label"));
        assertEquals(2, JsonPath.<Integer>read(json, "$.steps[0].fiber.number"));
        assertEquals(2, JsonPath.<List<?>>read(json, "$.steps[0].coordinates").size());
        assertEquals("CEO-01", JsonPath.read(json, "$.steps[1].label"));
        assertEquals("OLT-1 · placa 1 · porta 3", JsonPath.read(json, "$.steps[3].label"));
        assertEquals(List.of(), JsonPath.read(json, "$.breaks"));
        assertEquals(List.of(), JsonPath.read(json, "$.branches"));
    }

    // ---------- 2. Fusão dentro da caixa não para o caminho ----------

    @Test
    void fusionInsideBoxIsCrossedAndReportsTrayPosition() {
        Scenario s = upstreamScenario(true);

        String json = fx.getJson(trace("BOX", s.cto, "UPSTREAM"), 200);

        assertEquals("REACHED_OLT", JsonPath.read(json, "$.status"));
        assertEquals(4, JsonPath.<List<?>>read(json, "$.steps").size());
        assertEquals(1, JsonPath.<Integer>read(json, "$.steps[1].position.tray"));
        assertEquals(4, JsonPath.<Integer>read(json, "$.steps[1].position.number"));
    }

    // ---------- 3. Splitter descendo: resumo das saídas ----------

    @Test
    void splitterDownstreamSummarisesEachOutput() {
        Splitted s = splitterScenario();

        String json = fx.getJson(trace("SPLITTER", s.splitter, "DOWNSTREAM"), 200);

        assertEquals("REACHED_CUSTOMERS", JsonPath.read(json, "$.status"));
        assertEquals(List.of(), JsonPath.read(json, "$.steps"));
        assertEquals(List.of(1, 2, 3, 4), JsonPath.read(json, "$.branches[*].outputNumber"));
        assertEquals(List.of("CUSTOMERS", "CUSTOMERS", "FREE", "FREE"), JsonPath.read(json, "$.branches[*].status"));
        assertEquals(List.of(1, 1, 0, 0), JsonPath.read(json, "$.branches[*].customerCount"));
        assertEquals(s.outputPort2.toString(), JsonPath.read(json, "$.branches[0].traceRef"));
        assertEquals(List.of(), JsonPath.read(json, "$.customers"));
    }

    // ---------- 4. Abrir uma saída leva aos clientes ----------

    @Test
    void splitterPortDownstreamOpensOutputAndReachesCustomer() {
        Splitted s = splitterScenario();

        String json = fx.getJson(trace("SPLITTER_PORT", s.outputPort2, "DOWNSTREAM"), 200);

        assertEquals("REACHED_CUSTOMERS", JsonPath.read(json, "$.status"));
        assertEquals(List.of("SERVICE_PORT"), JsonPath.read(json, "$.steps[*].kind"));
        assertEquals("CTO-002 · porta 1", JsonPath.read(json, "$.steps[0].label"));
        assertEquals(List.of("Cliente 1"), JsonPath.read(json, "$.customers[*].name"));
    }

    // ---------- 5. Ponta sem ligação ----------

    @Test
    void fiberEndWithoutConnectionBreaksWithBoxPosition() {
        Scenario s = upstreamScenario(false);
        UUID looseCable = cable("CABO-SOLTO-01", 12);
        UUID loose = fiberIn(looseCable, 1);
        attachChain(looseCable, boxJson(s.ceo), boxJson(s.cto));

        String json = fx.getJson(traceEnd(endOf(loose, "B"), "UPSTREAM"), 200);

        assertEquals("BROKEN", JsonPath.read(json, "$.status"));
        assertEquals(List.of("CABLE"), JsonPath.read(json, "$.steps[*].kind"));
        assertEquals("NO_CONNECTION", JsonPath.read(json, "$.breaks[0].reason"));
        assertEquals("FIBER_END", JsonPath.read(json, "$.breaks[0].at.kind"));
        assertEquals(endOf(loose, "A").toString(), JsonPath.read(json, "$.breaks[0].at.id"));
        assertEquals("CABO-SOLTO-01 · fibra 1 · ponta A", JsonPath.read(json, "$.breaks[0].at.label"));
        // A ponta A fica no elemento de início do cabo (a CEO); sem posição própria, usa a da caixa.
        assertEquals(-46.62, JsonPath.<Double>read(json, "$.breaks[0].at.coordinates[0].longitude"));
        assertEquals(-23.54, JsonPath.<Double>read(json, "$.breaks[0].at.coordinates[0].latitude"));
    }

    // ---------- 6. Laço ----------

    @Test
    void loopIsDetectedInsteadOfHanging() {
        UUID box = box("CEO-LACO", "CEO");
        UUID cable1 = cable("CABO-L1", 12);
        UUID cable2 = cable("CABO-L2", 12);
        UUID f1 = fiberIn(cable1, 1);
        UUID f2 = fiberIn(cable2, 1);
        attachChain(cable1, boxJson(box), boxJson(box(("CTO-L1"), "CTO")));
        attachChain(cable2, boxJson(box), boxJson(box(("CTO-L2"), "CTO")));
        connector(box, f1, "A", f2, "A");
        connector(box, f2, "B", f1, "B");

        String json = fx.getJson(traceEnd(endOf(f1, "A"), "UPSTREAM"), 200);

        assertEquals("BROKEN", JsonPath.read(json, "$.status"));
        assertEquals("LOOP", JsonPath.read(json, "$.breaks[0].reason"));
    }

    // ---------- 7. Limite de profundidade ----------

    @Test
    void chainLongerThanFiftyStepsStopsAtDepthLimit() {
        UUID first = null;
        UUID previous = null;
        for (int i = 0; i < 27; i++) {
            UUID fiber = fiberIn(cable("CABO-LONGO-" + i, 12), 1);
            if (previous != null) {
                UUID box = box("CAIXA-LONGA-" + i, "CEO");
                jdbc.update("INSERT INTO optical_connection (id, connection_type, box_id, termination_a_id, termination_b_id)"
                        + " VALUES (gen_random_uuid(), 'CONNECTOR', ?, LEAST(?::uuid, ?::uuid), GREATEST(?::uuid, ?::uuid))",
                    box, endOf(previous, "B"), endOf(fiber, "A"), endOf(previous, "B"), endOf(fiber, "A"));
            }
            if (first == null) {
                first = fiber;
            }
            previous = fiber;
        }

        String json = fx.getJson(traceEnd(endOf(first, "B"), "DOWNSTREAM"), 200);

        assertEquals("BROKEN", JsonPath.read(json, "$.status"));
        assertEquals(50, JsonPath.<List<?>>read(json, "$.steps").size());
        assertEquals("DEPTH_LIMIT", JsonPath.read(json, "$.breaks[0].reason"));
    }

    // ---------- 8. Erros ----------

    @Test
    void unknownStartIsNotFoundAndBadKindOrDirectionIsBadRequest() {
        UUID missing = UUID.randomUUID();
        String notFound = fx.getJson(API + "/trace/FIBER/" + missing + "?direction=UPSTREAM", 404);
        assertEquals("Fiber not found", JsonPath.read(notFound, "$.detail"));

        fx.getJson(API + "/trace/XYZ/" + missing + "?direction=UPSTREAM", 400);
        fx.getJson(API + "/trace/BOX/" + missing + "?direction=SIDEWAYS", 400);
    }

    // ---------- 9. Nada ligado ----------

    @Test
    void boxWithNothingLinkedIsEmpty() {
        UUID box = box("CTO-VAZIA", "CTO");

        String json = fx.getJson(trace("BOX", box, "UPSTREAM"), 200);

        assertEquals("EMPTY", JsonPath.read(json, "$.status"));
        assertEquals(List.of(), JsonPath.read(json, "$.steps"));
        assertEquals(List.of(), JsonPath.read(json, "$.breaks"));
    }

    // ================= montagem da rede =================

    private record Scenario(UUID cto, UUID ceo, UUID olt) {
    }

    private record Splitted(UUID splitter, UUID outputPort2) {
    }

    /**
     * OLT --(G)-- CEO-01 --(F)-- CTO-001 (splitter). F.A conecta a G.B na CEO. G.A vai para a porta
     * da OLT (placa 1, porta 3). F.B vai para a entrada do splitter da CTO.
     */
    private Scenario upstreamScenario(boolean fusion) {
        UUID olt = fx.olt();
        UUID oltPort = fx.oltPort(olt, 1, 3);
        UUID ceo = box("CEO-01", "CEO");
        UUID cto = box("CTO-001", "CTO");

        UUID feed = cable("CABO-FEED-01", 12);
        UUID g = fiberIn(feed, 1);
        UUID dist = cable("CABO-DIST-01", 12);
        UUID f = fiberIn(dist, 2);

        attachChain(feed, oltJson(olt), boxJson(ceo));
        attachChain(dist, boxJson(ceo), boxJson(cto));

        jdbc.update("UPDATE fiber_termination SET olt_port_id = ?, status = 'CONNECTED' WHERE fiber_id = ? AND end_code = 'A'",
            oltPort, g);

        if (fusion) {
            UUID tray = fx.tray(ceo, 1, 8);
            // Emenda: entrada = G (vem da OLT, ponta B), saída = F (ponta A). F.B segue para o splitter.
            fusion(ceo, tray, 4, g, f);
        } else {
            connector(ceo, f, "A", g, "B");
        }

        String splitterJson = createSplitter(cto, "RATIO_1_2", false);
        UUID splitter = UUID.fromString(JsonPath.read(splitterJson, "$.id"));
        UUID input = UUID.fromString(JsonPath.<List<String>>read(splitterJson, "$.ports[?(@.direction=='INPUT')].id").get(0));
        linkSplitterInput(cto, splitter, f, input);
        return new Scenario(cto, ceo, olt);
    }

    /** CTO-002 com splitter 1:4 e atendimento. Saídas 1 e 2 têm cliente; 3 e 4 ficam livres. */
    private Splitted splitterScenario() {
        UUID ceo = box("CEO-02", "CEO");
        UUID cto = box("CTO-002", "CTO");
        UUID dist = cable("CABO-DIST-02", 12);
        UUID f = fiberIn(dist, 3);
        attachChain(dist, boxJson(ceo), boxJson(cto));

        String splitterJson = createSplitter(cto, "RATIO_1_4", true);
        UUID splitter = UUID.fromString(JsonPath.read(splitterJson, "$.id"));
        UUID input = UUID.fromString(JsonPath.<List<String>>read(splitterJson, "$.ports[?(@.direction=='INPUT')].id").get(0));
        UUID output2 = UUID.fromString(JsonPath.<List<String>>read(splitterJson, "$.ports[?(@.portNumber==2)].id").get(0));
        UUID output3 = UUID.fromString(JsonPath.<List<String>>read(splitterJson, "$.ports[?(@.portNumber==3)].id").get(0));
        linkSplitterInput(cto, splitter, f, input);

        customer("Cliente 1", servicePortOf(output2));
        customer("Cliente 2", servicePortOf(output3));
        return new Splitted(splitter, output2);
    }

    private UUID cable(String name, int fiberCapacity) {
        return NetworkFixtures.id(fx.postJson(API + "/cables", """
            {"name":"%s","role":"FEEDER","fiberCapacity":%d,"coordinates":%s}
            """.formatted(name, fiberCapacity, LINE), 201));
    }

    private UUID box(String name, String type) {
        return NetworkFixtures.id(fx.postJson(API + "/boxes", """
            {"name":"%s","type":"%s","location":%s}
            """.formatted(name, type, LOC), 201));
    }

    /** Cria um tubo próprio com a fibra {@code number} (abs = number). */
    private UUID fiberIn(UUID cable, int number) {
        UUID tube = fx.tube(cable, number, 1);
        return fx.fiber(tube, 1, number);
    }

    private UUID endOf(UUID fiber, String end) {
        return jdbc.queryForObject(
            "SELECT id FROM fiber_termination WHERE fiber_id = ? AND end_code = ?", UUID.class, fiber, end);
    }

    private void attachChain(UUID cable, String... elementJson) {
        StringBuilder body = new StringBuilder("[");
        for (int i = 0; i < elementJson.length; i++) {
            String role = i == 0 ? "START" : i == elementJson.length - 1 ? "END" : "PASS_THROUGH";
            if (i > 0) {
                body.append(',');
            }
            body.append("{\"role\":\"").append(role).append("\",").append(elementJson[i].substring(1));
        }
        body.append(']');
        fx.putJson(API + "/cables/" + cable + "/attachments", body.toString(), 200);
    }

    private static String boxJson(UUID box) {
        return "{\"boxId\":\"" + box + "\"}";
    }

    private static String oltJson(UUID olt) {
        return "{\"oltId\":\"" + olt + "\"}";
    }

    private void connector(UUID box, UUID fiberA, String endA, UUID fiberB, String endB) {
        fx.postJson(API + "/boxes/" + box + "/connections", """
            {"fiberAId":"%s","fiberAEnd":"%s","fiberBId":"%s","fiberBEnd":"%s"}
            """.formatted(fiberA, endA, fiberB, endB), 201);
    }

    /** Fusão de duas fibras numa bandeja: entrada B, saída A (convenção da emenda). */
    private void fusion(UUID box, UUID tray, int position, UUID inputFiber, UUID outputFiber) {
        UUID first = endOf(inputFiber, "B");
        UUID second = endOf(outputFiber, "A");
        UUID connection = UUID.randomUUID();
        jdbc.update("INSERT INTO optical_connection (id, connection_type, box_id, termination_a_id, termination_b_id)"
                + " VALUES (?, 'FUSION', ?, LEAST(?::uuid, ?::uuid), GREATEST(?::uuid, ?::uuid))",
            connection, box, first, second, first, second);
        jdbc.update("INSERT INTO splice (id, position_number, box_id, input_fiber_id, output_fiber_id, connection_id, tray_id)"
                + " VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?)",
            position, box, inputFiber, outputFiber, connection, tray);
    }

    private String createSplitter(UUID box, String ratio, boolean servicePorts) {
        return fx.postJson(API + "/boxes/" + box + "/splitters", """
            {"ratio":"%s","balanced":true,"createServicePorts":%s}
            """.formatted(ratio, servicePorts), 201);
    }

    /** Liga a entrada do splitter (porta INPUT) à fibra pela ponta B. */
    private void linkSplitterInput(UUID box, UUID splitter, UUID fiber, UUID input) {
        fx.putJson(API + "/splitters/" + splitter + "/ports/" + input,
            "{\"fiberId\":\"" + fiber + "\",\"fiberEnd\":\"B\"}", 200);
    }

    private UUID servicePortOf(UUID splitterPort) {
        return jdbc.queryForObject("SELECT id FROM service_port WHERE splitter_port_id = ?", UUID.class, splitterPort);
    }

    private void customer(String name, UUID servicePort) {
        fx.postJson(API + "/customers", """
            {"name":"%s","contractCode":"%s","location":%s,"servicePortId":"%s"}
            """.formatted(name, name.replace(" ", "-"), LOC, servicePort), 201);
    }

    private static String trace(String kind, UUID id, String direction) {
        return API + "/trace/" + kind + "/" + id + "?direction=" + direction;
    }

    private static String traceEnd(UUID terminationId, String direction) {
        return trace("FIBER_END", terminationId, direction);
    }
}
