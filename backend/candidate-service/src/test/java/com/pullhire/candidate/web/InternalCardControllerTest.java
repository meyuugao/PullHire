package com.pullhire.candidate.web;

import com.pullhire.candidate.application.CardProjectorService;
import com.pullhire.candidate.domain.PublicCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /internal/candidates/{id}/card}: hidden → 404, visible → no PII. */
class InternalCardControllerTest {

    private CardProjectorService cards;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cards = mock(CardProjectorService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalCardController(cards))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void hiddenProfileReturns404() throws Exception {
        when(cards.current(9L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/candidates/9/card"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @Test
    void visibleProfileReturnsPrivacyFilteredCard() throws Exception {
        when(cards.current(7L)).thenReturn(Optional.of(new PublicCard(3L, 7L, "Ivan P.", "Senior Developer",
                "it", "backend", "senior", new String[]{"Java"}, false, false, Instant.now())));

        mockMvc.perform(get("/internal/candidates/7/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value("7"))
                .andExpect(jsonPath("$.displayName").value("Ivan P."))
                .andExpect(jsonPath("$.contactReleased").value(false))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.contactPhone").doesNotExist())
                .andExpect(jsonPath("$.fullName").doesNotExist())
                .andExpect(content().string(not(containsString("@"))));
    }

    @Test
    void nullStackElementDoesNotBreakCard() throws Exception {
        when(cards.current(7L)).thenReturn(Optional.of(new PublicCard(3L, 7L, "Ivan P.", "Senior Developer",
                "it", "backend", "senior", new String[]{"Java", null}, false, false, Instant.now())));

        mockMvc.perform(get("/internal/candidates/7/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stack[0]").value("Java"))
                .andExpect(jsonPath("$.stack[1]").doesNotExist());
    }
}
