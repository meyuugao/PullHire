package me.yuugao.pullhire.fsp.web;

import java.time.Instant;
import java.util.List;

import me.yuugao.pullhire.fsp.application.CandidateProfileClient;
import me.yuugao.pullhire.fsp.application.FspQueryService;
import me.yuugao.pullhire.fsp.domain.AchievementCache;
import me.yuugao.pullhire.fsp.domain.FspLink;
import me.yuugao.pullhire.fsp.domain.HistoryStatus;
import me.yuugao.pullhire.fsp.domain.LinkStatus;
import me.yuugao.pullhire.fsp.error.ApiException;
import me.yuugao.pullhire.fsp.security.JwtAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /candidate/fsp/achievements}: 404 when unlinked, neutral 200 for no_history. */
class FspAchievementsControllerTest {

    private static final long CANDIDATE = 5L;

    private FspQueryService query;
    private CandidateProfileClient identities;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        query = mock(FspQueryService.class);
        identities = mock(CandidateProfileClient.class);
        when(identities.resolveProfileId(CANDIDATE)).thenReturn(CANDIDATE);
        mockMvc = MockMvcBuilders.standaloneSetup(new FspAchievementsController(query, identities))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void noLinkReturns404ProblemJson() throws Exception {
        when(query.requireLinked(CANDIDATE)).thenThrow(ApiException.notFound("ФСП ID не связан"));

        mockMvc.perform(get("/candidate/fsp/achievements")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, CANDIDATE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @Test
    void noHistoryIsNeutralTwoHundredWithEmptyItems() throws Exception {
        FspLink link = new FspLink(10L, CANDIDATE, "fsp-1", LinkStatus.LINKED, HistoryStatus.NO_HISTORY,
                Instant.now(), Instant.now(), Instant.now(), Instant.now());
        when(query.requireLinked(CANDIDATE)).thenReturn(link);
        when(query.achievements(10L)).thenReturn(List.of());

        mockMvc.perform(get("/candidate/fsp/achievements")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, CANDIDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("no_history"))
                .andExpect(jsonPath("$.linkStatus").value("linked"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void presentHistoryReturnsItems() throws Exception {
        FspLink link = new FspLink(10L, CANDIDATE, "fsp-1", LinkStatus.LINKED, HistoryStatus.PRESENT,
                Instant.now(), Instant.now(), Instant.now(), Instant.now());
        when(query.requireLinked(CANDIDATE)).thenReturn(link);
        when(query.achievements(10L)).thenReturn(List.of(
                new AchievementCache(1L, 10L, "a-1", "Победа", "championship", null, 1, null, Instant.now())));

        mockMvc.perform(get("/candidate/fsp/achievements")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, CANDIDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyStatus").value("present"))
                .andExpect(jsonPath("$.items[0].title").value("Победа"))
                .andExpect(jsonPath("$.items[0].place").value(1));
    }
}
