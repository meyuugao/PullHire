package me.yuugao.pullhire.assessment.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import me.yuugao.pullhire.assessment.application.CategoryAssignmentService.Outcome;
import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.GradeDirection;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.repo.CooldownRepository;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository.GradeHistoryEntry;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Completion orchestration ({@code docs/testing_model.md §7.1, §7.2, §7.3, §8};
 * TD-1; {@code docs/assumptions.md §2}): every completed attempt republishes
 * {@code category_assigned}; only an {@code up} change burns a cooldown
 * ({@code now + 1 month}); a downgrade applies only when candidate-initiated;
 * {@code needs_review} routes to manual with no auto category.
 *
 * <p>Plain JUnit 5 + AssertJ + Mockito; the clock instant is injected.
 */
class CategoryAssignmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final long SESSION = 11L;
    private static final long CANDIDATE = 42L;

    /** Middle band with default cut-scores, far from any boundary at SE 0.1. */
    private static final double MIDDLE_THETA = 0.0;
    /** Senior band with default cut-scores, far from any boundary at SE 0.1. */
    private static final double SENIOR_THETA = 2.0;
    private static final double PRECISE_SE = 0.1;

    private TestSessionRepository sessions;
    private GradeHistoryRepository gradeHistory;
    private CooldownRepository cooldowns;
    private EventPublisher events;
    private GradeEligibilityService eligibility;
    private CategoryAssignmentService service;

    @BeforeEach
    void setUp() {
        sessions = mock(TestSessionRepository.class);
        gradeHistory = mock(GradeHistoryRepository.class);
        cooldowns = mock(CooldownRepository.class);
        events = mock(EventPublisher.class);
        eligibility = mock(GradeEligibilityService.class);
        when(eligibility.check(anyLong(), any(Instant.class)))
                .thenReturn(GradeEligibilityService.GradeEligibility.ofEligible());
        service = new CategoryAssignmentService(sessions, gradeHistory, cooldowns,
                events, new AssessmentProperties(), eligibility);
        when(sessions.findById(SESSION)).thenReturn(Optional.of(session()));
    }

    @Test
    void retryRepublishesCategoryAssigned() {
        when(gradeHistory.listByCandidate(CANDIDATE)).thenReturn(List.of(entry("middle")));

        Outcome outcome = service.complete(SESSION, CANDIDATE, MIDDLE_THETA, PRECISE_SE, false, NOW);

        assertThat(outcome.manualReview()).isFalse();
        assertThat(outcome.direction()).isEqualTo(GradeDirection.RECHECK);
        verify(events).publishCategoryAssigned(CANDIDATE, "backend", "middle",
                "backend-middle", Long.toString(SESSION), NOW);
        verify(events).publishGradeChanged(eq(CANDIDATE), eq("backend"), eq("middle"),
                eq(GradeDirection.RECHECK), eq("backend-middle"), anyString(), any());
        verifyNoInteractions(cooldowns);
    }

    @Test
    void upMoveRepublishesCategoryAndBurnsOneMonthCooldown() {
        when(gradeHistory.listByCandidate(CANDIDATE)).thenReturn(List.of(entry("middle")));

        Outcome outcome = service.complete(SESSION, CANDIDATE, SENIOR_THETA, PRECISE_SE, false, NOW);

        assertThat(outcome.direction()).isEqualTo(GradeDirection.UP);
        assertThat(outcome.gradeChanged()).isTrue();
        verify(events).publishCategoryAssigned(CANDIDATE, "backend", "senior",
                "backend-senior", Long.toString(SESSION), NOW);
        Instant expected = NOW.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        verify(cooldowns).upsert(CANDIDATE, expected);
    }

    @Test
    void candidateInitiatedDownMoveRepublishesCategoryWithoutCooldown() {
        when(gradeHistory.listByCandidate(CANDIDATE)).thenReturn(List.of(entry("senior")));

        Outcome outcome = service.complete(SESSION, CANDIDATE, MIDDLE_THETA, PRECISE_SE, true, NOW);

        assertThat(outcome.direction()).isEqualTo(GradeDirection.DOWN);
        assertThat(outcome.grade()).isEqualTo("middle");
        verify(events).publishCategoryAssigned(CANDIDATE, "backend", "middle",
                "backend-middle", Long.toString(SESSION), NOW);
        verifyNoInteractions(cooldowns);
    }

    @Test
    void failedAttemptNeverForcesDowngrade() {
        when(gradeHistory.listByCandidate(CANDIDATE)).thenReturn(List.of(entry("senior")));

        Outcome outcome = service.complete(SESSION, CANDIDATE, MIDDLE_THETA, PRECISE_SE, false, NOW);

        assertThat(outcome.grade()).isEqualTo("senior");
        assertThat(outcome.direction()).isEqualTo(GradeDirection.RECHECK);
        assertThat(outcome.gradeChanged()).isFalse();
        verify(events).publishCategoryAssigned(CANDIDATE, "backend", "senior",
                "backend-senior", Long.toString(SESSION), NOW);
        verifyNoInteractions(cooldowns);
        ArgumentCaptor<TestSession> progress = ArgumentCaptor.forClass(TestSession.class);
        verify(sessions).update(progress.capture());
        assertThat(progress.getValue().status()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void initialAttemptAssignsWithoutCooldown() {
        when(gradeHistory.listByCandidate(CANDIDATE)).thenReturn(List.of());

        Outcome outcome = service.complete(SESSION, CANDIDATE, MIDDLE_THETA, PRECISE_SE, false, NOW);

        assertThat(outcome.direction()).isEqualTo(GradeDirection.INITIAL);
        verify(events).publishCategoryAssigned(CANDIDATE, "backend", "middle",
                "backend-middle", Long.toString(SESSION), NOW);
        verify(gradeHistory).append(eq(CANDIDATE), eq("middle"), eq("backend"),
                eq(GradeDirection.INITIAL), eq(SESSION), eq("backend-middle"), any(), eq(NOW));
        verifyNoInteractions(cooldowns);
    }

    @Test
    void needsReviewRoutesToManualWithNoAutoCategory() {
        when(gradeHistory.listByCandidate(CANDIDATE)).thenReturn(List.of(entry("middle")));

        Outcome outcome = service.complete(SESSION, CANDIDATE, MIDDLE_THETA, 5.0, false, NOW);

        assertThat(outcome.manualReview()).isTrue();
        assertThat(outcome.categoryCode()).isNull();
        verifyNoInteractions(events);
        verifyNoInteractions(gradeHistory);
        verifyNoInteractions(cooldowns);
        ArgumentCaptor<TestSession> progress = ArgumentCaptor.forClass(TestSession.class);
        verify(sessions).update(progress.capture());
        assertThat(progress.getValue().status()).isEqualTo(SessionStatus.NEEDS_REVIEW);
    }

    @Test
    void gradeChangedEventNeverDrivesCooldownOnRecheck() {
        when(gradeHistory.listByCandidate(CANDIDATE)).thenReturn(List.of(entry("middle")));

        service.complete(SESSION, CANDIDATE, MIDDLE_THETA, PRECISE_SE, false, NOW);

        verify(events).publishGradeChanged(eq(CANDIDATE), eq("backend"), eq("middle"),
                eq(GradeDirection.RECHECK), eq("backend-middle"), anyString(), any());
        verify(cooldowns, never()).upsert(anyLong(), any(Instant.class));
    }

    @Test
    void gradeScopedToSessionSpecialization() {
        when(sessions.findById(SESSION)).thenReturn(Optional.of(session("frontend")));
        when(gradeHistory.listByCandidate(CANDIDATE)).thenReturn(List.of(entry("senior")));

        Outcome outcome = service.complete(SESSION, CANDIDATE, MIDDLE_THETA, PRECISE_SE, false, NOW);

        assertThat(outcome.manualReview()).isFalse();
        assertThat(outcome.grade()).isEqualTo("middle");
        assertThat(outcome.direction()).isEqualTo(GradeDirection.INITIAL);
        assertThat(outcome.gradeChanged()).isFalse();
        verify(events).publishCategoryAssigned(CANDIDATE, "frontend", "middle",
                "frontend-middle", Long.toString(SESSION), NOW);
        verifyNoInteractions(cooldowns);
    }

    @Test
    void recompleteTerminalSessionIsNoop() {
        when(sessions.findById(SESSION)).thenReturn(Optional.of(terminalSession(SessionStatus.COMPLETED)));

        Outcome outcome = service.complete(SESSION, CANDIDATE, SENIOR_THETA, PRECISE_SE, false, NOW);

        assertThat(outcome.manualReview()).isFalse();
        assertThat(outcome.grade()).isEqualTo("middle");
        verify(gradeHistory).listByCandidate(CANDIDATE);
        verify(gradeHistory, never()).append(anyLong(), anyString(), anyString(), any(),
                any(), anyString(), any(), any(Instant.class));
        verifyNoInteractions(events);
        verifyNoInteractions(cooldowns);
        verify(sessions, never()).update(any(TestSession.class));
    }

    @Test
    void recompleteNeedsReviewSessionIsNoop() {
        when(sessions.findById(SESSION)).thenReturn(Optional.of(terminalSession(SessionStatus.NEEDS_REVIEW)));

        Outcome outcome = service.complete(SESSION, CANDIDATE, MIDDLE_THETA, PRECISE_SE, false, NOW);

        assertThat(outcome.manualReview()).isTrue();
        verify(gradeHistory).listByCandidate(CANDIDATE);
        verify(gradeHistory, never()).append(anyLong(), anyString(), anyString(), any(),
                any(), anyString(), any(), any(Instant.class));
        verifyNoInteractions(events);
        verifyNoInteractions(cooldowns);
        verify(sessions, never()).update(any(TestSession.class));
    }

    @Test
    void activeCooldownRetainsGradeWithoutBurningNewCooldown() {
        when(gradeHistory.listByCandidate(CANDIDATE)).thenReturn(List.of(entry("middle")));
        when(eligibility.check(anyLong(), any(Instant.class)))
                .thenReturn(GradeEligibilityService.GradeEligibility
                        .ofRefused(NOW.plusSeconds(3_600)));

        Outcome outcome = service.complete(SESSION, CANDIDATE, SENIOR_THETA, PRECISE_SE, false, NOW);

        assertThat(outcome.manualReview()).isFalse();
        assertThat(outcome.grade()).isEqualTo("middle");
        assertThat(outcome.direction()).isEqualTo(GradeDirection.RECHECK);
        assertThat(outcome.gradeChanged()).isFalse();
        verify(events).publishCategoryAssigned(CANDIDATE, "backend", "middle",
                "backend-middle", Long.toString(SESSION), NOW);
        verify(cooldowns, never()).upsert(anyLong(), any(Instant.class));
    }

    @Test
    void firstAttemptUnderCandidateCooldownKeepsBandAsRecheck() {
        when(gradeHistory.listByCandidate(CANDIDATE)).thenReturn(List.of());
        when(eligibility.check(anyLong(), any(Instant.class)))
                .thenReturn(GradeEligibilityService.GradeEligibility
                        .ofRefused(NOW.plusSeconds(3_600)));

        Outcome outcome = service.complete(SESSION, CANDIDATE, MIDDLE_THETA, PRECISE_SE, false, NOW);

        assertThat(outcome.manualReview()).isFalse();
        assertThat(outcome.grade()).isEqualTo("middle");
        assertThat(outcome.direction()).isEqualTo(GradeDirection.RECHECK);
        assertThat(outcome.gradeChanged()).isFalse();
        verify(cooldowns, never()).upsert(anyLong(), any(Instant.class));
    }

    @Test
    void terminalOutcomeDerivesGradeFromLatestHistoryRow() {
        when(sessions.findById(SESSION)).thenReturn(Optional.of(terminalSession(SessionStatus.COMPLETED)));
        when(gradeHistory.listByCandidate(CANDIDATE))
                .thenReturn(List.of(entry("middle"), entry("senior")));

        Outcome outcome = service.complete(SESSION, CANDIDATE, MIDDLE_THETA, PRECISE_SE, false, NOW);

        assertThat(outcome.grade()).isEqualTo("senior");
        assertThat(outcome.categoryCode()).isEqualTo("backend-senior");
    }

    private static TestSession session() {
        return session("backend");
    }

    private static TestSession session(String specializationCode) {
        return new TestSession(SESSION, CANDIDATE, "it", specializationCode, "middle", null, null,
                7L, SessionStatus.IN_PROGRESS, null, null, 10,
                Instant.parse("2026-06-15T11:00:00Z"), null);
    }

    private static TestSession terminalSession(SessionStatus status) {
        return new TestSession(SESSION, CANDIDATE, "it", "backend", "middle", null, null,
                7L, status, null, null, 10,
                Instant.parse("2026-06-15T11:00:00Z"),
                status == SessionStatus.NEEDS_REVIEW ? null : NOW);
    }

    private static GradeHistoryEntry entry(String grade) {
        return new GradeHistoryEntry(1L, CANDIDATE, grade, "backend", GradeDirection.RECHECK,
                SESSION, "backend-" + grade, null, NOW.minusSeconds(3_600));
    }
}
