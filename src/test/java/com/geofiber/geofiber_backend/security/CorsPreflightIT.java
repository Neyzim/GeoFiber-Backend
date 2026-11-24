package com.geofiber.geofiber_backend.security;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

/** O front chama PATCH e cookies entre origens; o preflight precisa liberar os dois. */
class CorsPreflightIT extends IntegrationTest {

    @Test
    void preflightAllowsPatchWithCredentials() throws Exception {
        mvc.perform(options("/api/v1/users/00000000-0000-0000-0000-000000000001/active")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
