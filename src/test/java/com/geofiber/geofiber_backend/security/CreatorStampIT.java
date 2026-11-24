package com.geofiber.geofiber_backend.security;

import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import com.geofiber.geofiber_backend.services.auth.UserService;
import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreatorStampIT extends IntegrationTest {
    private static final String LOCATION = "{\"longitude\":-46.62,\"latitude\":-23.54}";
    private static final String LINE = "[{\"longitude\":-46.63,\"latitude\":-23.55},{\"longitude\":-46.62,\"latitude\":-23.54}]";

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    private UUID memberId;
    private String token;

    @BeforeEach
    void member() {
        memberId = userService.create("Membro", "membro@teste.com", "senha-membro-123", UserRoleEnum.MEMBER).getId();
        token = jwtService.issue(memberId, UserRoleEnum.MEMBER);
    }

    @Test
    void boxCreatedByMemberIsStampedWithTheirId() throws Exception {
        UUID box = idOf(createBox("CTO-A"));

        assertEquals(memberId, createdBy("network_box", box));
    }

    @Test
    void cableOltAndCustomerAreStampedWithTheirCreator() throws Exception {
        UUID cable = idOf(post("/api/v1/cables"), """
            {"name":"CABO-1","role":"FEEDER","fiberCapacity":12,"coordinates":%s}
            """.formatted(LINE));
        UUID olt = idOf(post("/api/v1/olts"), "{\"name\":\"OLT-1\",\"location\":" + LOCATION + "}");
        UUID box = idOf(createBox("CTO-B"));
        mockMvc.perform(post("/api/v1/boxes/" + box + "/splitters")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ratio\":\"RATIO_1_4\",\"balanced\":true,\"createServicePorts\":true}"))
            .andExpect(status().isCreated());
        UUID servicePort = jdbc.queryForObject(
            "SELECT id FROM service_port WHERE box_id = ? ORDER BY port_number LIMIT 1", UUID.class, box);
        UUID customer = idOf(post("/api/v1/customers"), """
            {"name":"Cliente 1","contractCode":"C-1","location":%s,"servicePortId":"%s"}
            """.formatted(LOCATION, servicePort));

        assertEquals(memberId, createdBy("network_cable", cable));
        assertEquals(memberId, createdBy("network_olt", olt));
        assertEquals(memberId, createdBy("customer", customer));
    }

    @Test
    void creatorIdIsNotExposedInResponses() throws Exception {
        String body = createBox("CTO-C").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("createdBy"));
    }

    private ResultActions createBox(String name) throws Exception {
        return mockMvc.perform(post("/api/v1/boxes")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + name + "\",\"type\":\"CTO\",\"location\":" + LOCATION + "}"));
    }

    private static UUID idOf(ResultActions actions) throws Exception {
        return UUID.fromString(JsonPath.read(
            actions.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));
    }

    private UUID idOf(MockHttpServletRequestBuilder request, String json) throws Exception {
        return idOf(mockMvc.perform(request.header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(json)));
    }

    private UUID createdBy(String table, UUID id) {
        return jdbc.queryForObject("SELECT created_by FROM " + table + " WHERE id = ?", UUID.class, id);
    }
}
