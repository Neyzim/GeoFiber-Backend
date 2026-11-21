package com.geofiber.geofiber_backend.controllers.errors;

import com.geofiber.geofiber_backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiErrorsIT extends IntegrationTest {
    @Test
    void validationErrorListsFields() throws Exception {
        mvc.perform(post("/api/v1/boxes").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CTO\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title", notNullValue()))
            .andExpect(jsonPath("$.errors[*].field", hasItems("name", "location")));
    }

    @Test
    void malformedJsonIsProblemDetail() throws Exception {
        mvc.perform(post("/api/v1/boxes").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownIdIs404ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/boxes/" + UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Box not found"));
    }

    @Test
    void duplicateTubeNumberHasSpecificMessage() throws Exception {
        UUID cable = fx.cable(24);
        fx.tube(cable, 1, 12);
        mvc.perform(post("/api/v1/cables/" + cable + "/tubes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tubeNumber\":1,\"capacity\":12,\"color\":\"GREEN\"}"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("A tube with this number already exists in the cable"));
    }

    @Test
    void invalidEnumIs400() throws Exception {
        mvc.perform(post("/api/v1/boxes").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"X","type":"XYZ","location":{"longitude":0,"latitude":0}}"""))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void attachmentListWithNullElementsIs400ProblemDetail() throws Exception {
        UUID cable = fx.cable(12);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/cables/" + cable + "/attachments")
                .contentType(MediaType.APPLICATION_JSON).content("[null,null]"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unexpectedErrorIs500ProblemDetail() {
        org.springframework.http.ProblemDetail problem =
            new ApiExceptionHandler().handleUnexpected(new IllegalStateException("boom")).getBody();

        org.junit.jupiter.api.Assertions.assertEquals(500, problem.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("Unexpected error", problem.getTitle());
    }
}
