package com.pullhire.auth.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone-проверка маппинга нарушения уникальности в 409 problem+json (без БД). */
class ApiExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        @GetMapping("/boom")
        String boom() {
            throw new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint \"uq_users_email\"");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void dataIntegrityViolationMapsTo409Conflict() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("conflict"));
    }
}
