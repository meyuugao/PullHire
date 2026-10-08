package me.yuugao.pullhire.assessment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import me.yuugao.pullhire.assessment.application.ProctoringService.Outcome;
import me.yuugao.pullhire.assessment.application.ProctoringService.ProctoringFlag;
import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.Attempt;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.repo.AttemptRepository;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proctoring-flag ingestion ({@code docs/testing_model.md §9.2}, §6.3): any
 * single flag routes the session to {@code needs_review} with timestamps
 * persisted, never auto-fails ({@code is_correct} untouched, never abandoned),
 * and the SE/CI condition alone also routes to review.
 *
 * <p>Plain JUnit 5 + AssertJ + Mockito; no Spring context.
 */
class ProctoringServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final Instant FLAG_AT = Instant.parse("2026-06-15T11:30:00Z");
    private static final long SESSION = 11L;
    private static final long ATTEMPT = 21L;
    private static final long CANDIDATE = 42L;

    /** Middle band with default cut-scores, far from any boundary at SE 0.1. */
    private static final double MIDDLE_THETA = 0.0;
    private static final double PRECISE_SE = 0.1;
    /** Far above the SE threshold: the §6.3 imprecise-estimate path. */
    private static final double BLOATED_SE = 5.0;

    private AttemptRepository attempts;
    private TestSessionRepository sessions;
    private ProctoringService service;

    @BeforeEach
    void setUp() {
        attempts = mock(AttemptRepository.class);
        sessions = mock(TestSessionRepository.class);
        service = new ProctoringService(attempts, sessions, new AssessmentProperties());
        when(attempts.findBySession(SESSION)).thenReturn(List.of(attempt("[]", Boolean.TRUE)));
        when(sessions.findById(SESSION)).thenReturn(Optional.of(session()));
    }

    @Test
    void singleVisibilitychangeFlagRoutesToNeedsReview() {
        Outcome outcome = service.ingest(SESSION, ATTEMPT,
                new ProctoringFlag(ProctoringService.VISIBILITYCHANGE, FLAG_AT),
                MIDDLE_THETA, PRECISE_SE);

        assertThat(outcome.needsReview()).isTrue();
        assertThat(outcome.storedFlags()).contains(ProctoringService.VISIBILITYCHANGE);
        assertThat(outcome.storedFlags()).contains(FLAG_AT.toString());
        assertThat(sessionUpdate().status()).isEqualTo(SessionStatus.NEEDS_REVIEW);
    }

    @Test
    void singleFullscreenExitFlagRoutesToNeedsReview() {
        Outcome outcome = service.ingest(SESSION, ATTEMPT,
                new ProctoringFlag(ProctoringService.FULLSCREEN_EXIT, FLAG_AT),
                MIDDLE_THETA, PRECISE_SE);

        assertThat(outcome.needsReview()).isTrue();
        assertThat(outcome.storedFlags()).contains(ProctoringService.FULLSCREEN_EXIT);
        assertThat(outcome.storedFlags()).contains(FLAG_AT.toString());
        assertThat(sessionUpdate().status()).isEqualTo(SessionStatus.NEEDS_REVIEW);
    }

    @Test
    void singlePasteFlagRoutesToNeedsReview() {
        Outcome outcome = service.ingest(SESSION, ATTEMPT,
                new ProctoringFlag(ProctoringService.PASTE, FLAG_AT),
                MIDDLE_THETA, PRECISE_SE);

        assertThat(outcome.needsReview()).isTrue();
        assertThat(outcome.storedFlags()).contains(ProctoringService.PASTE);
        assertThat(outcome.storedFlags()).contains(FLAG_AT.toString());
        assertThat(sessionUpdate().status()).isEqualTo(SessionStatus.NEEDS_REVIEW);
    }

    @Test
    void neverAutoFailsAnswerAndNeverAbandonsSession() {
        service.ingest(SESSION, ATTEMPT,
                new ProctoringFlag(ProctoringService.PASTE, FLAG_AT),
                MIDDLE_THETA, PRECISE_SE);

        ArgumentCaptor<Boolean> correctness = ArgumentCaptor.forClass(Boolean.class);
        verify(attempts).saveAnswer(eq(ATTEMPT), any(), correctness.capture(), any(), any());
        assertThat(correctness.getValue()).isEqualTo(Boolean.TRUE);
        TestSession updated = sessionUpdate();
        assertThat(updated.status()).isEqualTo(SessionStatus.NEEDS_REVIEW);
        assertThat(updated.status()).isNotEqualTo(SessionStatus.ABANDONED);
    }

    @Test
    void flagsAppendedWithTimestampsOntoExistingDocument() {
        when(attempts.findBySession(SESSION))
                .thenReturn(List.of(attempt(
                        "[{\"type\":\"paste\",\"at\":\"2026-06-15T11:00:00Z\"}]", null)));

        Outcome outcome = service.ingest(SESSION, ATTEMPT,
                new ProctoringFlag(ProctoringService.VISIBILITYCHANGE, FLAG_AT),
                MIDDLE_THETA, PRECISE_SE);

        assertThat(outcome.needsReview()).isTrue();
        assertThat(outcome.storedFlags()).contains("paste");
        assertThat(outcome.storedFlags()).contains(ProctoringService.VISIBILITYCHANGE);
        assertThat(outcome.storedFlags()).contains(FLAG_AT.toString());
        ArgumentCaptor<Boolean> correctness = ArgumentCaptor.forClass(Boolean.class);
        verify(attempts).saveAnswer(eq(ATTEMPT), any(), correctness.capture(), any(), any());
        assertThat(correctness.getValue()).isNull();
    }

    @Test
    void seCiConditionAloneRoutesToNeedsReview() {
        Outcome outcome = service.ingest(SESSION, ATTEMPT, List.of(),
                MIDDLE_THETA, BLOATED_SE);

        assertThat(outcome.needsReview()).isTrue();
        assertThat(sessionUpdate().status()).isEqualTo(SessionStatus.NEEDS_REVIEW);
        assertThat(sessionUpdate().status()).isNotEqualTo(SessionStatus.ABANDONED);
    }

    @Test
    void cleanAttemptWithPreciseEstimateStaysOutOfReview() {
        Outcome outcome = service.ingest(SESSION, ATTEMPT, List.of(),
                MIDDLE_THETA, PRECISE_SE);

        assertThat(outcome.needsReview()).isFalse();
        assertThat(outcome.storedFlags()).isEqualTo("[]");
        verify(sessions, never()).update(any(TestSession.class));
        verify(attempts).saveAnswer(eq(ATTEMPT), any(), any(), any(), eq("[]"));
    }

    @Test
    void reviewUpdatePreservesSessionProgressColumns() {
        service.ingest(SESSION, ATTEMPT,
                new ProctoringFlag(ProctoringService.PASTE, FLAG_AT),
                MIDDLE_THETA, PRECISE_SE);

        TestSession updated = sessionUpdate();
        assertThat(updated.id()).isEqualTo(SESSION);
        assertThat(updated.candidateId()).isEqualTo(CANDIDATE);
        assertThat(updated.theta()).isEqualTo(BigDecimal.valueOf(MIDDLE_THETA));
        assertThat(updated.thetaSe()).isEqualTo(BigDecimal.valueOf(PRECISE_SE));
    }

    @Test
    void mergeJsonAppendsWithoutOverwritingStoredFlags() {
        String stored = ProctoringService.mergeJson(
                "[{\"type\":\"paste\",\"at\":\"2026-06-15T11:00:00Z\"}]",
                "[{\"type\":\"paste\",\"at\":\"2026-06-15T11:30:00Z\"}]");

        assertThat(stored).contains("2026-06-15T11:00:00Z");
        assertThat(stored).contains("2026-06-15T11:30:00Z");
        assertThat(ProctoringService.mergeJson(stored, null)).isEqualTo(stored);
        assertThat(ProctoringService.mergeJson(null, null)).isEqualTo("[]");
    }

    private TestSession sessionUpdate() {
        ArgumentCaptor<TestSession> progress = ArgumentCaptor.forClass(TestSession.class);
        verify(sessions).update(progress.capture());
        return progress.getValue();
    }

    private static Attempt attempt(String flags, Boolean correct) {
        return new Attempt(ATTEMPT, SESSION, 7L, 1, 99L, "{\"choice\":\"a\"}",
                correct, 1_200, flags, NOW);
    }

    private static TestSession session() {
        return new TestSession(SESSION, CANDIDATE, "it", "backend", "middle", null, null,
                7L, SessionStatus.IN_PROGRESS, null, null, 10,
                Instant.parse("2026-06-15T11:00:00Z"), null);
    }
}
