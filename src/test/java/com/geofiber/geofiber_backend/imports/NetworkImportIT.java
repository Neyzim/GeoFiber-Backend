package com.geofiber.geofiber_backend.imports;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkImportIT extends IntegrationTest {
    private static final String URL = "/api/v1/imports/network";
    private static final String LOC = "{\"longitude\":-51.57,\"latitude\":-10.65}";
    private static final String LOC_2 = "{\"longitude\":-51.58,\"latitude\":-10.66}";
    private static final String LINE = "[{\"longitude\":-51.57,\"latitude\":-10.65},{\"longitude\":-51.58,\"latitude\":-10.66}]";

    @Test
    void createsBoxesOltAndCablesWithCountersAndIds() {
        String json = fx.postJson(URL, """
            {"usageStatus":"INSTALLED",
             "elements":[
               {"ref":"p-12","action":"CREATE","kind":"BOX","type":"CTO","name":"CTO-001","description":"Rua A, 10","location":%s},
               {"ref":"p-13","action":"CREATE","kind":"OLT","name":"OLT-IMP","location":%s}],
             "cables":[
               {"ref":"l-3","action":"CREATE","name":"CABO-01","role":"DISTRIBUTION","fiberCapacity":12,"coordinates":%s}]}
            """.formatted(LOC, LOC_2, LINE), 201);

        assertEquals(1, JsonPath.<Integer>read(json, "$.created.boxes"));
        assertEquals(1, JsonPath.<Integer>read(json, "$.created.olts"));
        assertEquals(1, JsonPath.<Integer>read(json, "$.created.cables"));
        assertEquals(0, JsonPath.<Integer>read(json, "$.created.attachments"));
        assertEquals(0, JsonPath.<Integer>read(json, "$.moved"));
        assertEquals(0, JsonPath.<Integer>read(json, "$.reshaped"));
        assertEquals(3, JsonPath.<Map<String, String>>read(json, "$.ids").size());

        assertEquals(1, jdbc.queryForObject(
            "SELECT count(*) FROM network_box WHERE name = 'CTO-001' AND box_type = 'CTO' AND usage_status = 'INSTALLED'",
            Integer.class));
        assertEquals(1, jdbc.queryForObject(
            "SELECT count(*) FROM network_olt WHERE name = 'OLT-IMP' AND usage_status = 'INSTALLED'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
            "SELECT count(*) FROM network_cable WHERE name = 'CABO-01' AND fiber_capacity = 12 AND cable_role = 'DISTRIBUTION'",
            Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM fiber", Integer.class));
    }

    @Test
    void attachesEndsByRefBoxIdOltIdAndLeavesAbsentEndsEmpty() {
        UUID existingBox = fx.box("CEO", null, null);
        UUID existingOlt = fx.olt();
        String json = fx.postJson(URL, """
            {"usageStatus":"PLANNED",
             "elements":[{"ref":"p-1","action":"CREATE","kind":"BOX","type":"CTO","name":"CTO-NEW","location":%s}],
             "cables":[
               {"ref":"l-1","action":"CREATE","name":"CABO-A","role":"FEEDER","fiberCapacity":24,"coordinates":%s,
                "start":{"ref":"p-1"},"end":{"boxId":"%s"}},
               {"ref":"l-2","action":"CREATE","name":"CABO-B","role":"BACKBONE","fiberCapacity":6,"coordinates":%s,
                "start":{"oltId":"%s"},"end":{"ref":"p-1"}},
               {"ref":"l-3","action":"CREATE","name":"CABO-C","role":"DROP","fiberCapacity":2,"coordinates":%s}]}
            """.formatted(LOC, LINE, existingBox, LINE, existingOlt, LINE), 201);

        assertEquals(4, JsonPath.<Integer>read(json, "$.created.attachments"));
        assertEquals(List.of(2, 2, 0), attachmentCounts("CABO-A", "CABO-B", "CABO-C"));
        assertEquals("START", roleOf("CABO-A", 1));
        assertEquals("END", roleOf("CABO-A", 2));
        assertEquals("START", roleOf("CABO-B", 1));
        assertEquals("END", roleOf("CABO-B", 2));
    }

    @Test
    void moveUpdatesLocationAndKeepsId() {
        UUID box = fx.box("CTO", 8, null);
        String json = fx.postJson(URL, """
            {"usageStatus":"INSTALLED",
             "elements":[{"ref":"p-77","action":"MOVE","kind":"BOX","id":"%s","location":%s}]}
            """.formatted(box, LOC), 201);

        assertEquals(1, JsonPath.<Integer>read(json, "$.moved"));
        assertEquals(box.toString(), JsonPath.<String>read(json, "$.ids['p-77']"));
        assertEquals(-51.57, jdbc.queryForObject("SELECT ST_X(geom) FROM network_box WHERE id = ?", Double.class, box));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM network_box", Integer.class));
    }

    @Test
    void reshapeChangesTraceOfCableWithoutConnectedFibers() {
        UUID cable = fx.cable(12);
        String json = fx.postJson(URL, """
            {"usageStatus":"INSTALLED",
             "cables":[{"ref":"l-9","action":"RESHAPE","id":"%s","coordinates":[
               {"longitude":-46.60,"latitude":-23.50},{"longitude":-46.61,"latitude":-23.51},{"longitude":-46.62,"latitude":-23.52}]}]}
            """.formatted(cable), 201);

        assertEquals(1, JsonPath.<Integer>read(json, "$.reshaped"));
        assertEquals(3, jdbc.queryForObject(
            "SELECT ST_NumPoints(geom) FROM network_cable WHERE id = ?", Integer.class, cable));
    }

    @Test
    void reshapeOfCableWithConnectedFiberIsRejectedAndNothingIsWritten() {
        UUID cable = fx.cable(12);
        UUID tube = fx.tube(cable, 1, 12);
        UUID fiber = fx.fiber(tube, 1, 1);
        UUID olt = fx.olt();
        UUID port = fx.oltPort(olt, 1, 1);
        jdbc.update("UPDATE fiber_termination SET olt_port_id = ?, status = 'CONNECTED' WHERE fiber_id = ? AND end_code = 'A'",
            port, fiber);

        String body = """
            {"usageStatus":"INSTALLED",
             "elements":[{"ref":"p-1","action":"CREATE","kind":"BOX","type":"CTO","name":"CTO-ROLLBACK","location":%s}],
             "cables":[{"ref":"l-9","action":"RESHAPE","id":"%s","coordinates":%s}]}
            """.formatted(LOC, cable, LINE);
        String json = fx.postJson(URL, body, 409);

        assertEquals("Cable has connected fibers and cannot be reshaped", JsonPath.<String>read(json, "$.detail"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM network_box", Integer.class));
    }

    @Test
    void duplicateOfExistingNameSameTypeIsRejectedAllOrNothing() {
        fx.box("CTO", 8, null); // "CTO-1"
        String json = fx.postJson(URL, """
            {"usageStatus":"PLANNED",
             "elements":[
               {"ref":"p-1","action":"CREATE","kind":"OLT","name":"OLT-OK","location":%s},
               {"ref":"p-2","action":"CREATE","kind":"BOX","type":"CTO","name":"  cto_1 ","location":%s}]}
            """.formatted(LOC, LOC_2), 409);

        assertEquals("Import has elements that already exist", JsonPath.<String>read(json, "$.detail"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM network_olt", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM network_box", Integer.class));
    }

    @Test
    void twoCreatesWithSameNameInSameRequestAreRejected() {
        String json = fx.postJson(URL, """
            {"usageStatus":"PLANNED",
             "elements":[
               {"ref":"p-1","action":"CREATE","kind":"BOX","type":"CTO","name":"Caixa Á","location":%s},
               {"ref":"p-2","action":"CREATE","kind":"BOX","type":"CTO","name":"caixa a","location":%s}]}
            """.formatted(LOC, LOC_2), 409);

        assertEquals("Import has elements that already exist", JsonPath.<String>read(json, "$.detail"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM network_box", Integer.class));
    }

    @Test
    void sameNameWithDifferentTypeIsCreated() {
        fx.box("CTO", 8, null); // "CTO-1"
        String json = fx.postJson(URL, """
            {"usageStatus":"PLANNED",
             "elements":[{"ref":"p-1","action":"CREATE","kind":"BOX","type":"CEO","name":"CTO-1","location":%s}]}
            """.formatted(LOC), 201);

        assertEquals(1, JsonPath.<Integer>read(json, "$.created.boxes"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM network_box", Integer.class));
    }

    @Test
    void invalidItemsAreRejectedWithInvalidImportAndErrors() {
        assertInvalid("""
            {"usageStatus":"PLANNED","elements":[
              {"ref":"p-1","action":"CREATE","kind":"BOX","type":"CTO","name":"A","location":%s},
              {"ref":"p-1","action":"CREATE","kind":"BOX","type":"CTO","name":"B","location":%s}]}
            """.formatted(LOC, LOC_2), "ref");
        assertInvalid("""
            {"usageStatus":"PLANNED","cables":[
              {"ref":"l-1","action":"CREATE","name":"C","role":"DROP","fiberCapacity":2,"coordinates":%s,
               "start":{"ref":"nao-existe"}}]}
            """.formatted(LINE), "start");
        assertInvalid("""
            {"usageStatus":"PLANNED","elements":[
              {"ref":"p-1","action":"CREATE","kind":"BOX","type":"CTO","name":"A","location":{"longitude":-51.5,"latitude":95}}]}
            """, "location");
        assertInvalid("""
            {"usageStatus":"PLANNED","cables":[
              {"ref":"l-1","action":"CREATE","name":"C","role":"DROP","fiberCapacity":2,
               "coordinates":[{"longitude":-51.5,"latitude":-10.6}]}]}
            """, "coordinates");
        assertInvalid("""
            {"elements":[{"ref":"p-1","action":"CREATE","kind":"BOX","type":"CTO","name":"A","location":%s}]}
            """.formatted(LOC), "usageStatus");
        assertInvalid("""
            {"usageStatus":"PLANNED","elements":[
              {"ref":"p-1","action":"CREATE","kind":"BOX","type":"CTO","name":"   ","location":%s}]}
            """.formatted(LOC), "name");
        assertInvalid("""
            {"usageStatus":"PLANNED","elements":[
              {"ref":"p-1","action":"CREATE","kind":"OLT","type":"CTO","name":"OLT-X","location":%s}]}
            """.formatted(LOC), "type");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM network_box", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM network_olt", Integer.class));
    }

    @Test
    void endpointWithMoreThanOneKeyIsRejected() {
        UUID box = fx.box("CTO", 8, null);
        assertInvalid("""
            {"usageStatus":"PLANNED","elements":[
              {"ref":"p-1","action":"CREATE","kind":"OLT","name":"OLT-X","location":%s}],
             "cables":[{"ref":"l-1","action":"CREATE","name":"C","role":"DROP","fiberCapacity":2,"coordinates":%s,
               "end":{"ref":"p-1","boxId":"%s"}}]}
            """.formatted(LOC, LINE, box), "end");
    }

    @Test
    void endpointPointingToMissingBoxIsRejected() {
        assertInvalid("""
            {"usageStatus":"PLANNED","cables":[
              {"ref":"l-1","action":"CREATE","name":"C","role":"DROP","fiberCapacity":2,"coordinates":%s,
               "end":{"boxId":"%s"}}]}
            """.formatted(LINE, UUID.randomUUID()), "end");
    }

    @Test
    void moreThanTenThousandItemsIsRejectedBeforeAnyCheck() {
        String elements = IntStream.range(0, 10_001)
            .mapToObj(index -> "{\"ref\":\"r" + index + "\"}")
            .collect(Collectors.joining(","));
        String json = fx.postJson(URL, "{\"usageStatus\":\"PLANNED\",\"elements\":[" + elements + "]}", 400);

        assertEquals("Import exceeds 10000 items", JsonPath.<String>read(json, "$.detail"));
    }

    @Test
    void moveOfUnknownIdIsNotFound() {
        String json = fx.postJson(URL, """
            {"usageStatus":"PLANNED","elements":[
              {"ref":"p-77","action":"MOVE","kind":"BOX","id":"%s","location":%s}]}
            """.formatted(UUID.randomUUID(), LOC), 404);

        assertEquals("Box not found", JsonPath.<String>read(json, "$.detail"));
    }

    @Test
    void moveWithWrongKindIsNotFound() {
        UUID olt = fx.olt();
        fx.postJson(URL, """
            {"usageStatus":"PLANNED","elements":[
              {"ref":"p-77","action":"MOVE","kind":"BOX","id":"%s","location":%s}]}
            """.formatted(olt, LOC), 404);
    }

    private void assertInvalid(String body, String expectedField) {
        String json = fx.postJson(URL, body, 400);
        assertEquals("Invalid import", JsonPath.<String>read(json, "$.detail"));
        List<String> fields = JsonPath.read(json, "$.errors[*].field");
        assertTrue(fields.contains(expectedField), "expected error on " + expectedField + " but was " + fields);
        assertFalse(fields.isEmpty());
    }

    private List<Integer> attachmentCounts(String... cableNames) {
        return Arrays.stream(cableNames)
            .map(name -> jdbc.queryForObject(
                "SELECT count(*) FROM cable_attachment a JOIN network_cable c ON c.id = a.cable_id WHERE c.name = ?",
                Integer.class, name))
            .toList();
    }

    private String roleOf(String cableName, int sequence) {
        return jdbc.queryForObject(
            "SELECT a.role FROM cable_attachment a JOIN network_cable c ON c.id = a.cable_id "
                + "WHERE c.name = ? AND a.sequence_number = ?",
            String.class, cableName, sequence);
    }
}
