package com.geofiber.geofiber_backend.security;

import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import com.geofiber.geofiber_backend.services.auth.UserService;
import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dono de um elemento é o único que o altera ou apaga. Os outros leem. */
class OwnershipWriteIT extends IntegrationTest {
    private static final String LOCATION = "{\"longitude\":-46.62,\"latitude\":-23.54}";
    private static final String LINE = "[{\"longitude\":-46.63,\"latitude\":-23.55},{\"longitude\":-46.62,\"latitude\":-23.54}]";

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void twoMembers() {
        UUID owner = userService.create("Dona", "dona@teste.com", "senha-dona-123", UserRoleEnum.MEMBER).getId();
        UUID other = userService.create("Outra", "outra@teste.com", "senha-outra-123", UserRoleEnum.MEMBER).getId();
        ownerToken = jwtService.issue(owner, UserRoleEnum.MEMBER);
        otherToken = jwtService.issue(other, UserRoleEnum.MEMBER);
    }

    @Test
    void otherMemberCannotUpdateOrDeleteBox() throws Exception {
        UUID box = boxOf(ownerToken, "CTO-DONA");

        send(HttpMethod.PUT, "/api/v1/boxes/" + box, otherToken, boxBody("CTO-ALTERADA"))
            .andExpect(status().isForbidden());
        send(HttpMethod.DELETE, "/api/v1/boxes/" + box, otherToken, null)
            .andExpect(status().isForbidden());
    }

    @Test
    void otherMemberCanReadBox() throws Exception {
        UUID box = boxOf(ownerToken, "CTO-LEITURA");

        mockMvc.perform(get("/api/v1/boxes/" + box).header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isOk());
    }

    @Test
    void ownerCanUpdateOwnBox() throws Exception {
        UUID box = boxOf(ownerToken, "CTO-PROPRIA");

        send(HttpMethod.PUT, "/api/v1/boxes/" + box, ownerToken, boxBody("CTO-PROPRIA-2"))
            .andExpect(status().isOk());
    }

    @Test
    void otherMemberCannotDeleteCable() throws Exception {
        UUID cable = cableOf(ownerToken);

        send(HttpMethod.DELETE, "/api/v1/cables/" + cable, otherToken, null)
            .andExpect(status().isForbidden());
    }

    @Test
    void otherMemberCannotAddChildToBoxTheyDoNotOwn() throws Exception {
        UUID box = boxOf(ownerToken, "CTO-FILHOS");

        send(HttpMethod.POST, "/api/v1/boxes/" + box + "/trays", otherToken,
            "{\"trayNumber\":1,\"capacity\":12}")
            .andExpect(status().isForbidden());
    }

    private UUID boxOf(String token, String name) throws Exception {
        String body = send(HttpMethod.POST, "/api/v1/boxes", token, boxBody(name))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID cableOf(String token) throws Exception {
        String body = send(HttpMethod.POST, "/api/v1/cables", token,
            "{\"name\":\"CABO-DONO\",\"role\":\"FEEDER\",\"fiberCapacity\":12,\"coordinates\":" + LINE + "}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String boxBody(String name) {
        return "{\"name\":\"" + name + "\",\"type\":\"CTO\",\"location\":" + LOCATION + "}";
    }

    private ResultActions send(HttpMethod method, String url, String token, String body) throws Exception {
        var builder = request(method, url).header("Authorization", "Bearer " + token);
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }
}
