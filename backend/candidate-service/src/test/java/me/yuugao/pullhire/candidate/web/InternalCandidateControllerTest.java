package me.yuugao.pullhire.candidate.web;

import me.yuugao.pullhire.candidate.application.ProfileService;
import me.yuugao.pullhire.candidate.domain.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /internal/candidates/by-user/{userId}}: sub → profile id, 404 if none. */
class InternalCandidateControllerTest {

    private ProfileService profiles;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalCandidateController(profiles))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void unknownUserReturns404() throws Exception {
        when(profiles.get(9L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/candidates/by-user/9"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @Test
    void knownUserReturnsProfileId() throws Exception {
        when(profiles.get(7L)).thenReturn(Optional.of(profile(3L, 7L)));

        mockMvc.perform(get("/internal/candidates/by-user/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value(3));
    }

    private static Profile profile(long id, long userId) {
        Instant now = Instant.now();
        return new Profile(id, userId, "Ivan P.", null, null, null, null, null,
                new String[0], new String[0], new String[0], null, null, null, "draft", now, now);
    }
}
