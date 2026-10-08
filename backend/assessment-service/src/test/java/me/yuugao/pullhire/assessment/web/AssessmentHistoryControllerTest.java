package me.yuugao.pullhire.assessment.web;

import me.yuugao.pullhire.assessment.application.HistoryService;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.security.JwtAuthFilter;
import me.yuugao.pullhire.assessment.web.dto.HistoryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit checks (no Spring context) for {@link AssessmentHistoryController}:
 * it resolves the authenticated candidate via {@link CurrentUser} and rejects
 * anonymous requests with 401 before touching the service.
 */
class AssessmentHistoryControllerTest {

    private HistoryService service;
    private AssessmentHistoryController controller;

    @BeforeEach
    void setUp() {
        service = mock(HistoryService.class);
        controller = new AssessmentHistoryController(service);
    }

    @Test
    void scopesHistoryToAuthenticatedCandidate() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthFilter.ATTR_USER_ID)).thenReturn(42L);
        HistoryResponse expected = new HistoryResponse(List.of(), List.of());
        when(service.history(42L)).thenReturn(expected);

        assertThat(controller.history(request)).isSameAs(expected);
        verify(service).history(42L);
    }

    @Test
    void rejectsUnauthenticatedRequestWith401() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthFilter.ATTR_USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> controller.history(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(api.code()).isEqualTo("unauthenticated");
                });
        verifyNoInteractions(service);
    }
}
