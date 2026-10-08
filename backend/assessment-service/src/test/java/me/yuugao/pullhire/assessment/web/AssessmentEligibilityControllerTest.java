package me.yuugao.pullhire.assessment.web;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import me.yuugao.pullhire.assessment.application.GradeEligibilityService;
import me.yuugao.pullhire.assessment.application.GradeEligibilityService.GradeEligibility;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.security.JwtAuthFilter;
import me.yuugao.pullhire.assessment.web.dto.EligibilityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code GET /assessment/grade-eligibility} wiring: the controller resolves the
 * candidate from {@link CurrentUser} and maps the service decision onto
 * {@link EligibilityResponse}. Mocked service; no Spring context.
 */
class AssessmentEligibilityControllerTest {

    private GradeEligibilityService service;
    private AssessmentEligibilityController controller;

    @BeforeEach
    void setUp() {
        service = mock(GradeEligibilityService.class);
        controller = new AssessmentEligibilityController(service);
    }

    @Test
    void mapsEligibleDecision() {
        HttpServletRequest request = authenticatedAs(7L);
        when(service.check(7L)).thenReturn(new GradeEligibility(true, null));

        EligibilityResponse response = controller.gradeEligibility(request);

        assertThat(response.eligible()).isTrue();
        assertThat(response.availableAfter()).isNull();
        verify(service).check(7L);
    }

    @Test
    void mapsRefusedDecisionWithAvailableAfter() {
        HttpServletRequest request = authenticatedAs(7L);
        Instant availableAfter = Instant.parse("2026-07-15T12:00:00Z");
        when(service.check(7L)).thenReturn(new GradeEligibility(false, availableAfter));

        EligibilityResponse response = controller.gradeEligibility(request);

        assertThat(response.eligible()).isFalse();
        assertThat(response.availableAfter()).isEqualTo(availableAfter);
    }

    @Test
    void unauthenticatedRequestIs401() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthFilter.ATTR_USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> controller.gradeEligibility(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(401));
    }

    private static HttpServletRequest authenticatedAs(long userId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthFilter.ATTR_USER_ID)).thenReturn(userId);
        return request;
    }
}
