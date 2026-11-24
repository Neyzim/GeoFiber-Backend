package com.geofiber.geofiber_backend.security;

import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import com.geofiber.geofiber_backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtFilterIT extends IntegrationTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void tokenWithGarbageSignatureGetsProblemDetailUnauthorized() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/boxes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenReachesBusinessEndpoint() throws Exception {
        String token = jwtService.issue(UUID.randomUUID(), UserRoleEnum.MEMBER);

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/boxes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
    }
}
