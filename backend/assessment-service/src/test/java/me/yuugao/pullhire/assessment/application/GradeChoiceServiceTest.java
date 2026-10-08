package me.yuugao.pullhire.assessment.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import me.yuugao.pullhire.assessment.application.GradeChoiceService.Outcome;
import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit checks (no Spring, no DB) for {@link GradeChoiceService}: it
 * persists {@code target_grade}, sets the start θ from
 * {@code startThetaByGrade}, rejects unknown grades with
 * {@code validation_error}, and never forces a downgrade — a lower
 * candidate-chosen grade is applied as-is.
 */
class GradeChoiceServiceTest {

    private TestSessionRepository sessions;
    private GradeEligibilityService eligibility;
    private GradeChoiceService service;

    @BeforeEach
    void setUp() {
        sessions = mock(TestSessionRepository.class);
        eligibility = mock(GradeEligibilityService.class);
        when(eligibility.check(anyLong()))
                .thenReturn(GradeEligibilityService.GradeEligibility.ofEligible());
        service = new GradeChoiceService(sessions, new AssessmentProperties(), eligibility);
    }

    @Test
    void persistsTargetGradeAndSetsStartTheta() {
        when(sessions.findById(7L)).thenReturn(Optional.of(session(7L, "middle", null)));

        Outcome outcome = service.choose(7L, 42L, "senior");

        assertThat(outcome.sessionId()).isEqualTo(7L);
        assertThat(outcome.declaredGrade()).isEqualTo("senior");
        assertThat(outcome.startTheta()).isEqualTo(0.5);
        TestSession saved = capture();
        assertThat(saved.targetGrade()).isEqualTo("senior");
        assertThat(saved.theta()).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    void setsCorrectStartThetaPerGrade() {
        when(sessions.findById(7L)).thenReturn(Optional.of(session(7L, "middle", null)));

        Outcome outcome = service.choose(7L, 42L, "  junior  ");

        assertThat(outcome.declaredGrade()).isEqualTo("junior");
        assertThat(outcome.startTheta()).isEqualTo(-0.5);
        assertThat(capture().theta()).isEqualByComparingTo(new BigDecimal("-0.5"));
    }

    @Test
    void unknownGradeIsRejectedWithValidationError() {
        when(sessions.findById(7L)).thenReturn(Optional.of(session(7L, "middle", null)));

        assertThatThrownBy(() -> service.choose(7L, 42L, "staff++"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).code()).isEqualTo("validation_error");
                    assertThat(((ApiException) ex).status().value()).isEqualTo(400);
                });
        verifyNoMoreInteractions(sessions);
    }

    @Test
    void candidateInitiatedLowerGradeIsAppliedWithoutForcedDowngrade() {
        when(sessions.findById(7L)).thenReturn(Optional.of(session(7L, "senior", null)));

        Outcome outcome = service.choose(7L, 42L, "junior");

        assertThat(outcome.declaredGrade()).isEqualTo("junior");
        assertThat(capture().targetGrade()).isEqualTo("junior");
    }

    @Test
    void foreignSessionIsNotFound() {
        when(sessions.findById(7L)).thenReturn(Optional.of(session(7L, "middle", null)));

        assertThatThrownBy(() -> service.choose(7L, 99L, "junior"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("not_found"));
    }

    @Test
    void nonStartedSessionIsRejectedAsConflict() {
        when(sessions.findById(7L)).thenReturn(Optional.of(sessionWithStatus(7L, SessionStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> service.choose(7L, 42L, "junior"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).code()).isEqualTo("conflict");
                    assertThat(((ApiException) ex).status().value()).isEqualTo(409);
                });
    }

    @Test
    void activeCooldownIsRejectedAsConflict() {
        when(sessions.findById(7L)).thenReturn(Optional.of(session(7L, "middle", null)));
        when(eligibility.check(anyLong()))
                .thenReturn(GradeEligibilityService.GradeEligibility
                        .ofRefused(java.time.Instant.now().plusSeconds(3_600)));

        assertThatThrownBy(() -> service.choose(7L, 42L, "senior"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).code()).isEqualTo("conflict");
                    assertThat(((ApiException) ex).status().value()).isEqualTo(409);
                });
    }

    @Test
    void sameGradeReconfirmationIsAllowedUnderCooldown() {
        when(sessions.findById(7L)).thenReturn(Optional.of(session(7L, "senior", null)));
        when(eligibility.check(anyLong()))
                .thenReturn(GradeEligibilityService.GradeEligibility
                        .ofRefused(java.time.Instant.now().plusSeconds(3_600)));

        Outcome outcome = service.choose(7L, 42L, "senior");

        assertThat(outcome.declaredGrade()).isEqualTo("senior");
        assertThat(capture().targetGrade()).isEqualTo("senior");
    }

    @Test
    void startedThetaIsNeverClobbered() {
        when(sessions.findById(7L)).thenReturn(Optional.of(session(7L, "middle", new BigDecimal("0.9"))));

        Outcome outcome = service.choose(7L, 42L, "junior");

        assertThat(outcome.startTheta()).isEqualTo(0.9);
        assertThat(capture().theta()).isEqualByComparingTo(new BigDecimal("0.9"));
    }

    private TestSession capture() {
        ArgumentCaptor<TestSession> captor = ArgumentCaptor.forClass(TestSession.class);
        verify(sessions).update(captor.capture());
        return captor.getValue();
    }

    private static TestSession session(long id, String targetGrade, BigDecimal theta) {
        return new TestSession(id, 42L, "industry-1", "spec-1", targetGrade, "{\"q\":1}",
                List.of("algorithms"), 777L, SessionStatus.STARTED, theta, null, 0, null, null);
    }

    private static TestSession sessionWithStatus(long id, SessionStatus status) {
        return new TestSession(id, 42L, "industry-1", "spec-1", "middle", "{\"q\":1}",
                List.of("algorithms"), 777L, status, new BigDecimal("0.5"), null, 1, null, null);
    }
}
