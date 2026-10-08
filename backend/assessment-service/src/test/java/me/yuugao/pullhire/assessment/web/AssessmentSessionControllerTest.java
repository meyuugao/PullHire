package me.yuugao.pullhire.assessment.web;

import java.util.List;

import me.yuugao.pullhire.assessment.application.AnswerService;
import me.yuugao.pullhire.assessment.application.SessionStartService;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.security.JwtAuthFilter;
import me.yuugao.pullhire.assessment.web.dto.AnswerRequest;
import me.yuugao.pullhire.assessment.web.dto.AnswerResponse;
import me.yuugao.pullhire.assessment.web.dto.SessionStartResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit checks (no Spring context) for {@link AssessmentSessionController}:
 * it resolves the authenticated candidate via {@link CurrentUser} and rejects
 * anonymous requests with 401 before touching the start/answer services.
 */
class AssessmentSessionControllerTest {

    private SessionStartService service;
    private AnswerService answers;
    private AssessmentSessionController controller;

    @BeforeEach
    void setUp() {
        service = mock(SessionStartService.class);
        answers = mock(AnswerService.class);
        controller = new AssessmentSessionController(service, answers);
    }

    @Test
    void startsSessionForAuthenticatedCandidate() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthFilter.ATTR_USER_ID)).thenReturn(42L);
        SessionStartResponse expected = new SessionStartResponse(
                "7", "in_progress",
                new SessionStartResponse.ServedItem("1", "anchor", null, List.of()));
        when(service.start(7L, 42L)).thenReturn(expected);

        assertThat(controller.start(7L, request)).isSameAs(expected);
        verify(service).start(7L, 42L);
    }

    @Test
    void rejectsUnauthenticatedRequestWith401() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthFilter.ATTR_USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> controller.start(7L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(api.code()).isEqualTo("unauthenticated");
                });
        verifyNoInteractions(service);
    }

    @Test
    void answersForAuthenticatedCandidatePassingIdempotencyKey() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthFilter.ATTR_USER_ID)).thenReturn(42L);
        AnswerRequest body = new AnswerRequest("21", new JsonMapper().readTree("\"option-b\""), 100, null);
        AnswerResponse expected = AnswerResponse.next(7L, "in_progress", 0.4, 0.894427191,
                1, 22L, "template", null, List.of());
        when(answers.answer(7L, 42L, body, "key-1")).thenReturn(expected);

        assertThat(controller.answer(7L, body, "key-1", request)).isSameAs(expected);
        verify(answers).answer(7L, 42L, body, "key-1");
        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnauthenticatedAnswerWith401() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthFilter.ATTR_USER_ID)).thenReturn(null);
        AnswerRequest body = new AnswerRequest("21", new JsonMapper().readTree("\"option-b\""), 100, null);

        assertThatThrownBy(() -> controller.answer(7L, body, null, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(api.code()).isEqualTo("unauthenticated");
                });
        verifyNoInteractions(service);
        verifyNoInteractions(answers);
    }
}
