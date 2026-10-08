package me.yuugao.pullhire.fsp.web;

import java.time.Instant;

import me.yuugao.pullhire.fsp.application.CandidateProfileClient;
import me.yuugao.pullhire.fsp.application.FspLinkService;
import me.yuugao.pullhire.fsp.domain.FspLink;
import me.yuugao.pullhire.fsp.domain.HistoryStatus;
import me.yuugao.pullhire.fsp.domain.LinkStatus;
import me.yuugao.pullhire.fsp.security.JwtAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST|DELETE /candidate/fsp/link} responses and error envelope. */
class FspLinkControllerTest {

    private static final long CANDIDATE = 5L;

    private FspLinkService links;
    private CandidateProfileClient identities;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        links = mock(FspLinkService.class);
        identities = mock(CandidateProfileClient.class);
        when(identities.resolveProfileId(CANDIDATE)).thenReturn(CANDIDATE);
        mockMvc = MockMvcBuilders.standaloneSetup(new FspLinkController(links, identities))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void linkReturnsCurrentState() throws Exception {
        when(links.link(CANDIDATE, "fsp-1")).thenReturn(
                new FspLink(10L, CANDIDATE, "fsp-1", LinkStatus.LINKED, HistoryStatus.PRESENT,
                        Instant.now(), Instant.now(), Instant.now(), Instant.now()));

        mockMvc.perform(post("/candidate/fsp/link")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, CANDIDATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fsp_id\":\"fsp-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value("5"))
                .andExpect(jsonPath("$.fspId").value("fsp-1"))
                .andExpect(jsonPath("$.linkStatus").value("linked"))
                .andExpect(jsonPath("$.historyStatus").value("present"));
    }

    @Test
    void unlinkReturnsUnlinkedState() throws Exception {
        when(links.unlink(CANDIDATE)).thenReturn(
                new FspLink(10L, CANDIDATE, "fsp-1", LinkStatus.UNLINKED, HistoryStatus.UNKNOWN,
                        null, null, Instant.now(), Instant.now()));

        mockMvc.perform(delete("/candidate/fsp/link")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, CANDIDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkStatus").value("unlinked"));
    }

    @Test
    void missingAuthYieldsProblemJson401() throws Exception {
        mockMvc.perform(delete("/candidate/fsp/link"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }

    @Test
    void fspIdRequiredByValidationReturnsProblemJson() throws Exception {
        when(links.link(eq(CANDIDATE), eq(""))).thenThrow(
                me.yuugao.pullhire.fsp.error.ApiException.validation("fsp_id обязателен"));

        mockMvc.perform(post("/candidate/fsp/link")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, CANDIDATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fsp_id\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }
}
