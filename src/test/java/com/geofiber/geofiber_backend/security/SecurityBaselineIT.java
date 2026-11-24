package com.geofiber.geofiber_backend.security;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityBaselineIT extends IntegrationTest {

    @Test
    void businessEndpointWithoutTokenIsUnauthorizedProblemDetail() throws Exception {
        String body = mvc.perform(get("/api/v1/boxes"))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        assertEquals("Unauthorized", JsonPath.read(body, "$.title"));
    }

    @Test
    void swaggerApiDocsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
