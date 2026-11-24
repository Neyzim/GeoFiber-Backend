package com.geofiber.geofiber_backend.docs;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Em produção (sem {@code API_DOCS_ENABLED=true}) o Swagger e o OpenAPI não respondem. */
@TestPropertySource(properties = "API_DOCS_ENABLED=false")
class OpenApiDisabledIT extends IntegrationTest {
    @Test
    void hidesTheApiDocs() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
