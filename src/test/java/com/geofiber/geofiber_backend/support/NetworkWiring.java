package com.geofiber.geofiber_backend.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Monta ligações de rede para os testes de leitura: fibras, anexos de cabo, conectores, fusões,
 * splitters, portas de atendimento e clientes. Usa a API quando ela existe e SQL só para o que a
 * API não expõe (fusão e porta PON de OLT).
 */
public class NetworkWiring {
    public static final String API = "/api/v1";
    public static final String LOC = "{\"longitude\":-46.62,\"latitude\":-23.54}";
    public static final String LINE = "[{\"longitude\":-46.63,\"latitude\":-23.55},{\"longitude\":-46.62,\"latitude\":-23.54}]";

    private final NetworkFixtures fx;
    private final JdbcTemplate jdbc;

    public NetworkWiring(NetworkFixtures fx, JdbcTemplate jdbc) {
        this.fx = fx;
        this.jdbc = jdbc;
    }

    public UUID box(String name, String type) {
        return NetworkFixtures.id(fx.postJson(API + "/boxes", """
            {"name":"%s","type":"%s","location":%s}
            """.formatted(name, type, LOC), 201));
    }

    public UUID cable(String name, int fiberCapacity) {
        return NetworkFixtures.id(fx.postJson(API + "/cables", """
            {"name":"%s","role":"FEEDER","fiberCapacity":%d,"coordinates":%s}
            """.formatted(name, fiberCapacity, LINE), 201));
    }

    /** Fibra {@code number} num tubo próprio (número absoluto = number). */
    public UUID fiberIn(UUID cable, int number) {
        UUID tube = fx.tube(cable, number, 1);
        return fx.fiber(tube, 1, number);
    }

    public UUID end(UUID fiber, String end) {
        return jdbc.queryForObject(
            "SELECT id FROM fiber_termination WHERE fiber_id = ? AND end_code = ?", UUID.class, fiber, end);
    }

    /** Anexa o cabo aos elementos na ordem: primeiro START, último END, os do meio PASS_THROUGH. */
    public void attachChain(UUID cable, String... elementJson) {
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

    public static String boxJson(UUID box) {
        return "{\"boxId\":\"" + box + "\"}";
    }

    public static String oltJson(UUID olt) {
        return "{\"oltId\":\"" + olt + "\"}";
    }

    public void connector(UUID box, UUID fiberA, String endA, UUID fiberB, String endB) {
        fx.postJson(API + "/boxes/" + box + "/connections", """
            {"fiberAId":"%s","fiberAEnd":"%s","fiberBId":"%s","fiberBEnd":"%s"}
            """.formatted(fiberA, endA, fiberB, endB), 201);
    }

    /** Fusão numa bandeja: entrada (ponta B) e saída (ponta A), como a emenda da spec. */
    public void fusion(UUID box, UUID tray, int position, UUID inputFiber, UUID outputFiber) {
        UUID first = end(inputFiber, "B");
        UUID second = end(outputFiber, "A");
        UUID connection = UUID.randomUUID();
        jdbc.update("INSERT INTO optical_connection (id, connection_type, box_id, termination_a_id, termination_b_id)"
                + " VALUES (?, 'FUSION', ?, LEAST(?::uuid, ?::uuid), GREATEST(?::uuid, ?::uuid))",
            connection, box, first, second, first, second);
        jdbc.update("INSERT INTO splice (id, position_number, box_id, input_fiber_id, output_fiber_id, connection_id, tray_id)"
                + " VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?)",
            position, box, inputFiber, outputFiber, connection, tray);
    }

    public String createSplitter(UUID box, String ratio, boolean servicePorts) {
        return fx.postJson(API + "/boxes/" + box + "/splitters", """
            {"ratio":"%s","balanced":true,"createServicePorts":%s}
            """.formatted(ratio, servicePorts), 201);
    }

    public static UUID splitterId(String splitterJson) {
        return UUID.fromString(JsonPath.read(splitterJson, "$.id"));
    }

    public static UUID inputPort(String splitterJson) {
        return UUID.fromString(JsonPath.<List<String>>read(splitterJson, "$.ports[?(@.direction=='INPUT')].id").get(0));
    }

    public static UUID outputPort(String splitterJson, int portNumber) {
        return UUID.fromString(JsonPath.<List<String>>read(splitterJson,
            "$.ports[?(@.portNumber==" + portNumber + ")].id").get(0));
    }

    /** Liga a entrada do splitter à fibra pela ponta B. */
    public void linkSplitterInput(UUID box, UUID splitter, UUID input, UUID fiber) {
        fx.putJson(API + "/splitters/" + splitter + "/ports/" + input,
            "{\"fiberId\":\"" + fiber + "\",\"fiberEnd\":\"B\"}", 200);
    }

    public UUID servicePortOf(UUID splitterPort) {
        return jdbc.queryForObject("SELECT id FROM service_port WHERE splitter_port_id = ?", UUID.class, splitterPort);
    }

    public UUID customer(String name, UUID servicePort) {
        return NetworkFixtures.id(fx.postJson(API + "/customers", """
            {"name":"%s","contractCode":"%s","location":%s,"servicePortId":"%s"}
            """.formatted(name, name.replace(" ", "-"), LOC, servicePort), 201));
    }

    /** Liga a ponta de fibra a uma porta PON da OLT (a API de OLT não expõe essa ligação). */
    public void linkOltPort(UUID fiber, String end, UUID oltPort) {
        jdbc.update("UPDATE fiber_termination SET olt_port_id = ?, status = 'CONNECTED' WHERE fiber_id = ? AND end_code = ?",
            oltPort, fiber, end);
    }
}
