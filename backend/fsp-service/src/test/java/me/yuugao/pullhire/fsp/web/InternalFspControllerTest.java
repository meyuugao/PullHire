package me.yuugao.pullhire.fsp.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import me.yuugao.pullhire.fsp.application.FspQueryService;
import me.yuugao.pullhire.fsp.domain.AchievementCache;
import me.yuugao.pullhire.fsp.domain.FspLink;
import me.yuugao.pullhire.fsp.domain.HistoryStatus;
import me.yuugao.pullhire.fsp.domain.LinkStatus;
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

/** {@code GET /internal/fsp/{candidateId}}: link + achievements, 404 when unlinked. */
class InternalFspControllerTest {

    private FspQueryService query;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        query = mock(FspQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalFspController(query))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void unlinkedReturns404() throws Exception {
        when(query.findLinked(9L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/fsp/9"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("not_found"));
    }

    @Test
    void linkedReturnsLinkAndAchievements() throws Exception {
        FspLink link = new FspLink(10L, 7L, "fsp-1", LinkStatus.LINKED, HistoryStatus.PRESENT,
                Instant.now(), Instant.now(), Instant.now(), Instant.now());
        when(query.findLinked(7L)).thenReturn(Optional.of(link));
        when(query.achievements(10L)).thenReturn(List.of(
                new AchievementCache(1L, 10L, "a-1", "Победа", "championship", null, 1, null, Instant.now())));

        mockMvc.perform(get("/internal/fsp/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value("7"))
                .andExpect(jsonPath("$.fspId").value("fsp-1"))
                .andExpect(jsonPath("$.historyStatus").value("present"))
                .andExpect(jsonPath("$.items[0].externalAchievementId").value("a-1"));
    }
}
