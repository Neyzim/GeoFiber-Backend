package com.geofiber.geofiber_backend.docs;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiIT extends IntegrationTest {
    @Test
    void exposesAllResourceGroups() {
        String spec = fx.getJson("/v3/api-docs", 200);

        assertEquals("GeoFiber API", JsonPath.read(spec, "$.info.title"));
        Map<String, Object> paths = JsonPath.read(spec, "$.paths");
        assertTrue(paths.keySet().containsAll(List.of(
            "/api/v1/boxes/{boxId}/service-ports",
            "/api/v1/cables/{cableId}/attachments",
            "/api/v1/boxes/{boxId}/connections")), paths.keySet().toString());
        List<String> tags = JsonPath.read(spec, "$.tags[*].name");
        assertTrue(tags.containsAll(List.of(
            "Caixas", "Cabos", "OLTs", "Clientes", "Splitters", "Portas de atendimento",
            "Emendas", "Conexões", "Bandejas")), tags.toString());
    }

    @Test
    void servesSwaggerUi() {
        fx.getJson("/swagger-ui/index.html", 200);
    }
}
