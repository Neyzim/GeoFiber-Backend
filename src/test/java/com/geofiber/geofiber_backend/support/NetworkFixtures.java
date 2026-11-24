package com.geofiber.geofiber_backend.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Atalhos HTTP para montar uma rede de teste. Cada método de criação devolve o id criado. */
public class NetworkFixtures {
    private static final String API = "/api/v1";
    private final MockMvc mvc;
    private final String authorization;

    public NetworkFixtures(MockMvc mvc, String token) {
        this.mvc = mvc;
        this.authorization = "Bearer " + token;
    }

    public UUID olt() {
        return id(postJson(API + "/olts",
            "{\"name\":\"OLT-1\",\"location\":{\"longitude\":-46.63,\"latitude\":-23.55}}", 201));
    }

    public UUID cable(int fiberCapacity) {
        return id(postJson(API + "/cables", """
            {"name":"CABO-1","role":"FEEDER","fiberCapacity":%d,
             "coordinates":[{"longitude":-46.63,"latitude":-23.55},{"longitude":-46.62,"latitude":-23.54}]}
            """.formatted(fiberCapacity), 201));
    }

    public UUID tube(UUID cableId, int number, int capacity) {
        return id(postJson(API + "/cables/" + cableId + "/tubes",
            "{\"tubeNumber\":%d,\"capacity\":%d,\"color\":\"GREEN\"}".formatted(number, capacity), 201));
    }

    public UUID fiber(UUID tubeId, int numberInTube, int absoluteNumber) {
        return id(postJson(API + "/tubes/" + tubeId + "/fibers",
            "{\"numberInTube\":%d,\"absoluteNumber\":%d,\"color\":\"BLUE\"}".formatted(numberInTube, absoluteNumber),
            201));
    }

    public UUID box(String type, Integer powerCapacity, Integer trayCapacity) {
        return id(postJson(API + "/boxes", """
            {"name":"%s-1","type":"%s","powerCapacity":%s,"trayCapacity":%s,
             "location":{"longitude":-46.62,"latitude":-23.54}}
            """.formatted(type, type, powerCapacity, trayCapacity), 201));
    }

    public UUID tray(UUID boxId, int number, int capacity) {
        return id(postJson(API + "/boxes/" + boxId + "/trays",
            "{\"trayNumber\":%d,\"capacity\":%d}".formatted(number, capacity), 201));
    }

    public UUID oltPort(UUID oltId, int slot, int port) {
        return id(postJson(API + "/olts/" + oltId + "/ports",
            "{\"slotNumber\":%d,\"portNumber\":%d}".formatted(slot, port), 201));
    }

    /** Anexa o cabo a uma caixa: cria uma CEO auxiliar como START e usa a caixa como END. */
    public void attachToBox(UUID cableId, UUID boxId) {
        UUID start = box("CEO", null, null);
        putJson(API + "/cables/" + cableId + "/attachments",
            "[{\"role\":\"START\",\"boxId\":\"" + start + "\"},{\"role\":\"END\",\"boxId\":\"" + boxId + "\"}]", 200);
    }

    public String getJson(String url, int expectedStatus) {
        return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
            .header("Authorization", authorization), expectedStatus);
    }

    public String postJson(String url, String body, int expectedStatus) {
        return perform(post(url).header("Authorization", authorization)
            .contentType(MediaType.APPLICATION_JSON).content(body), expectedStatus);
    }

    public String putJson(String url, String body, int expectedStatus) {
        return perform(put(url).header("Authorization", authorization)
            .contentType(MediaType.APPLICATION_JSON).content(body), expectedStatus);
    }

    public String delete(String url, int expectedStatus) {
        return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
            .header("Authorization", authorization), expectedStatus);
    }

    public static UUID id(String json) {
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private String perform(RequestBuilder request, int expectedStatus) {
        try {
            return mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
