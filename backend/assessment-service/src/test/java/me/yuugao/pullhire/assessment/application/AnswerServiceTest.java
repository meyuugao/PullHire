package me.yuugao.pullhire.assessment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.Attempt;
import me.yuugao.pullhire.assessment.domain.GradeDirection;
import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.ItemKind;
import me.yuugao.pullhire.assessment.domain.ItemStatus;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.repo.AttemptRepository;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository;
import me.yuugao.pullhire.assessment.repo.ItemBankRepository;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import me.yuugao.pullhire.assessment.web.dto.AnswerRequest;
import me.yuugao.pullhire.assessment.web.dto.AnswerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit checks (no Spring context) for {@link AnswerService}
 * ({@code docs/api.md §3.4}, {@code docs/testing_model.md §5.1, §5.2, §4, §9.1}):
 * answer persistence with correctness/response time, {@code θ}/{@code SE}
 * updates, closest-difficulty routing, SE/forced stops, completion via
 * {@code CategoryAssignmentService}, the anti-leak response surface and
 * per-serve exposure bookkeeping.
 */
class AnswerServiceTest {

    private static final long SESSION_ID = 7L;
    private static final long CANDIDATE_ID = 42L;
    private static final JsonMapper JSON = new JsonMapper();

    private TestSessionRepository sessions;
    private ItemBankRepository items;
    private AttemptRepository attempts;
    private CategoryAssignmentService categories;
    private AssessmentProperties properties;
    private GradeHistoryRepository gradeHistory;
    private ProctoringService proctoring;
    private AnswerService service;

    @BeforeEach
    void setUp() {
        sessions = mock(TestSessionRepository.class);
        items = mock(ItemBankRepository.class);
        attempts = mock(AttemptRepository.class);
        categories = mock(CategoryAssignmentService.class);
        properties = new AssessmentProperties();
        properties.setMinItems(10);
        properties.setMaxItems(30);
        properties.setSeThreshold(0.3);
        gradeHistory = mock(GradeHistoryRepository.class);
        proctoring = mock(ProctoringService.class);
        when(gradeHistory.listByCandidate(CANDIDATE_ID)).thenReturn(List.of());
        service = new AnswerService(sessions, items, attempts, categories, properties,
                gradeHistory, proctoring);

        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.IN_PROGRESS)));
        when(items.findActiveBySpecialization("backend")).thenReturn(bank());
        when(sessions.update(any(TestSession.class))).thenReturn(1);
        when(attempts.saveAnswer(anyLong(), any(), any(), any(), any())).thenReturn(1);
        when(attempts.insertIgnoringConflict(any(Attempt.class))).thenReturn(Optional.of(100L));
        when(categories.complete(anyLong(), anyLong(), anyDouble(), anyDouble(), anyBoolean()))
                .thenReturn(CategoryAssignmentService.Outcome.assigned(
                        "junior", "backend-junior", GradeDirection.INITIAL, false));
    }

    @Test
    void answerPersistedWithCorrectnessAndResponseMs() {
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));

        service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 1234));

        ArgumentCaptor<Boolean> correctness = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Integer> responseMs = ArgumentCaptor.forClass(Integer.class);
        verify(attempts).saveAnswer(eq(1L), any(), correctness.capture(), responseMs.capture(), any());
        assertThat(correctness.getValue()).isTrue();
        assertThat(responseMs.getValue()).isEqualTo(1234);
    }

    @Test
    void wrongAnswerPersistedAsIncorrect() {
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));

        service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-a\"", 500));

        verify(attempts).saveAnswer(eq(1L), any(), eq(false), eq(500), any());
    }

    @Test
    void thetaAndSeUpdatedAfterCorrectAnswer() {
        // Prior θ=0 (middle start), SE=1, item b=0: p=0.5, SE'²=0.8, θ'=0.4.
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));

        AnswerResponse response = service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100));

        assertThat(response.theta().doubleValue()).isCloseTo(0.4, within(1e-9));
        assertThat(response.thetaSe().doubleValue()).isCloseTo(Math.sqrt(0.8), within(1e-9));

        ArgumentCaptor<TestSession> progress = ArgumentCaptor.forClass(TestSession.class);
        verify(sessions).update(progress.capture());
        TestSession updated = progress.getValue();
        assertThat(updated.theta().doubleValue()).isCloseTo(0.4, within(1e-9));
        assertThat(updated.itemCount()).isEqualTo(1);
    }

    @Test
    void nextItemIsClosestToTheta() {
        // After a correct answer on b=0 (θ'=0.4): |0.4−(−1)|=1.4 vs |0.4−1|=0.6 → item 22.
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));

        AnswerResponse response = service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100));

        ArgumentCaptor<Attempt> served = ArgumentCaptor.forClass(Attempt.class);
        verify(attempts).insertIgnoringConflict(served.capture());
        assertThat(served.getValue().itemId()).isEqualTo(22L);
        assertThat(response.completed()).isFalse();
        assertThat(response.item().itemId()).isEqualTo("22");
        assertThat(response.item().content()).isNotNull();
    }

    @Test
    void stopsWhenSeBelowThresholdAfterMinAndCompletes() {
        properties.setMinItems(1);
        properties.setSeThreshold(10.0);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));

        AnswerResponse response = service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100));

        verify(categories).complete(eq(SESSION_ID), eq(CANDIDATE_ID), anyDouble(), anyDouble(), anyBoolean());
        verify(attempts, never()).insertIgnoringConflict(any(Attempt.class));
        assertThat(response.completed()).isTrue();
        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.item()).isNull();
        assertThat(response.grade()).isEqualTo("junior");
        assertThat(response.categoryCode()).isEqualTo("backend-junior");
    }

    @Test
    void forcedStopAtMaxDespiteHighSe() {
        properties.setMinItems(1);
        properties.setMaxItems(1);
        properties.setSeThreshold(0.0);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));

        AnswerResponse response = service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100));

        verify(categories).complete(eq(SESSION_ID), eq(CANDIDATE_ID), anyDouble(), anyDouble(), anyBoolean());
        assertThat(response.completed()).isTrue();
    }

    @Test
    void noStopBeforeMinServesNextWithoutCompletion() {
        properties.setMinItems(5);
        properties.setSeThreshold(10.0);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));

        AnswerResponse response = service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100));

        verifyNoInteractions(categories);
        assertThat(response.completed()).isFalse();
        assertThat(response.item()).isNotNull();
    }

    @Test
    void proctoringFlagsRouteToNeedsReviewWithoutCategoryAssignment() {
        properties.setMinItems(1);
        properties.setSeThreshold(10.0);
        Attempt flagged = new Attempt(1L, SESSION_ID, 21L, 1, 9L, null, null, null,
                "[{\"type\":\"paste\",\"at\":\"2026-10-08T00:00:00Z\"}]", Instant.EPOCH);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(flagged));

        AnswerResponse response = service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100));

        verifyNoInteractions(categories);
        ArgumentCaptor<TestSession> progress = ArgumentCaptor.forClass(TestSession.class);
        verify(sessions).update(progress.capture());
        assertThat(progress.getValue().status()).isEqualTo(SessionStatus.NEEDS_REVIEW);
        assertThat(response.completed()).isTrue();
        assertThat(response.status()).isEqualTo("needs_review");
        assertThat(response.item()).isNull();
    }

    @Test
    void manualReviewOutcomeMapsToNeedsReview() {
        properties.setMinItems(1);
        properties.setSeThreshold(10.0);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));
        when(categories.complete(anyLong(), anyLong(), anyDouble(), anyDouble(), anyBoolean()))
                .thenReturn(CategoryAssignmentService.Outcome.manual("junior"));

        AnswerResponse response = service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100));

        assertThat(response.completed()).isTrue();
        assertThat(response.status()).isEqualTo("needs_review");
        assertThat(response.grade()).isEqualTo("junior");
    }

    @Test
    void replayOfAnsweredItemIsStrictNoopReturningCurrentProgress() {
        Attempt answered = new Attempt(1L, SESSION_ID, 21L, 1, 9L, "{\"a\":1}", true, 100,
                "[]", Instant.EPOCH);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(answered));

        AnswerResponse response = service.answer(SESSION_ID, CANDIDATE_ID,
                request("21", "\"option-b\"", 100), "key-1");

        verify(attempts, never()).saveAnswer(anyLong(), any(), any(), any(), any());
        verify(attempts, never()).insertIgnoringConflict(any(Attempt.class));
        verifyNoInteractions(categories);
        verify(sessions, never()).update(any(TestSession.class));
        assertThat(response.completed()).isFalse();
        assertThat(response.item()).isNull();
        assertThat(response.sessionId()).isEqualTo(Long.toString(SESSION_ID));
    }

    @Test
    void candidateInitiatedDownSignalPassedToCompletion() {
        properties.setMinItems(1);
        properties.setSeThreshold(10.0);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(sessionWithTarget("junior")));
        when(gradeHistory.listByCandidate(CANDIDATE_ID)).thenReturn(List.of(
                new GradeHistoryRepository.GradeHistoryEntry(1L, CANDIDATE_ID, "senior", "backend",
                        GradeDirection.UP, 5L, "backend-senior", null, Instant.EPOCH)));

        service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100));

        verify(categories).complete(eq(SESSION_ID), eq(CANDIDATE_ID), anyDouble(), anyDouble(), eq(true));
    }

    @Test
    void nonCandidateInitiatedCompletionPassesFalse() {
        properties.setMinItems(1);
        properties.setSeThreshold(10.0);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));

        service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100));

        verify(categories).complete(eq(SESSION_ID), eq(CANDIDATE_ID), anyDouble(), anyDouble(), eq(false));
    }

    @Test
    void codeItemStaysUngradedAndLeavesThetaUntouched() {
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 22L, 1)));

        AnswerResponse response = service.answer(SESSION_ID, CANDIDATE_ID,
                request("22", "{\"code\":\"x\"}", 100));

        verify(attempts).saveAnswer(eq(1L), any(), eq(null), eq(100), any());
        assertThat(response.theta().doubleValue()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void servingIncrementsExposureAndRetiresWhenExhausted() {
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));
        when(items.isExposureExhausted(anyLong())).thenReturn(true);

        AnswerResponse response = service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100));

        long nextId = Long.parseLong(response.item().itemId());
        verify(items).incrementExposure(nextId);
        verify(items).retire(nextId);
    }

    @Test
    void responseHidesKeysAndScoringParams() {
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));

        AnswerResponse response = service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100));

        assertThat(response.sessionId()).isEqualTo(Long.toString(SESSION_ID));
        assertThat(response.item().itemId()).matches("\\d+");
        String json = JSON.writeValueAsString(response);
        assertThat(json).doesNotContain("\"key\"");
        assertThat(json).doesNotContain("correct");
        assertThat(json).doesNotContain("difficulty");
        assertThat(json).doesNotContain("variant_seed");
        assertThat(json).doesNotContain("sessionId");
        assertThat(json).doesNotContain("itemId");
        assertThat(json).contains("\"session_id\"").contains("\"item_id\"")
                .contains("\"item_kind\"").contains("\"option_order\"")
                .contains("\"theta_se\"").contains("\"item_count\"");
    }

    @Test
    void unservedItemIsNotFound() {
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));

        assertThatThrownBy(() -> service.answer(SESSION_ID, CANDIDATE_ID, request("20", "\"x\"", 100)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("not_found"));
    }

    @Test
    void terminalSessionIsRejectedAsConflict() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.COMPLETED)));
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(unanswered(1L, 21L, 1)));

        assertThatThrownBy(() -> service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("conflict"));
    }

    @Test
    void foreignOrUnknownSessionIsNotFound() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(sessionWithCandidate(99L)));

        assertThatThrownBy(() -> service.answer(SESSION_ID, CANDIDATE_ID, request("21", "\"option-b\"", 100)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("not_found"));
    }

    private static AnswerRequest request(String itemId, String answerJson, Integer responseMs) {
        JsonNode answer = JSON.readTree(answerJson);
        return new AnswerRequest(itemId, answer, responseMs, null);
    }

    private static TestSession session(SessionStatus status) {
        return sessionWithCandidate(CANDIDATE_ID, status);
    }

    private static TestSession sessionWithCandidate(long candidateId) {
        return sessionWithCandidate(candidateId, SessionStatus.IN_PROGRESS);
    }

    private static TestSession sessionWithCandidate(long candidateId, SessionStatus status) {
        return new TestSession(
                SESSION_ID,
                candidateId,
                "it",
                "backend",
                "middle",
                "{}",
                List.of("backend.concept.single-choice"),
                12345L,
                status,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                0,
                Instant.EPOCH,
                null);
    }

    private static TestSession sessionWithTarget(String targetGrade) {
        return new TestSession(
                SESSION_ID,
                CANDIDATE_ID,
                "it",
                "backend",
                targetGrade,
                "{}",
                List.of("backend.concept.single-choice"),
                12345L,
                SessionStatus.IN_PROGRESS,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                0,
                Instant.EPOCH,
                null);
    }

    private static Attempt unanswered(long id, long itemId, int order) {
        return new Attempt(id, SESSION_ID, itemId, order, 9L, null, null, null, null, Instant.EPOCH);
    }

    private static List<Item> bank() {
        return List.of(
                item(20L, ItemKind.TEMPLATE, "backend.family.single-choice",
                        "backend.concept.single-choice", -1.0,
                        "{\"model\":{\"format\":\"single_choice\",\"statement\":\"Choose one.\",\"options\":[\"option-a\",\"option-b\",\"option-c\",\"option-d\"]},\"key\":{\"correct\":\"option-b\"}}"),
                item(21L, ItemKind.TEMPLATE, "backend.family.single-choice-2",
                        "backend.concept.single-choice", 0.0,
                        "{\"model\":{\"format\":\"single_choice\",\"statement\":\"Choose one.\",\"options\":[\"option-a\",\"option-b\",\"option-c\",\"option-d\"]},\"key\":{\"correct\":\"option-b\"}}"),
                item(22L, ItemKind.TEMPLATE, "backend.family.code",
                        "backend.concept.code", 1.0,
                        "{\"model\":{\"format\":\"code\",\"statement\":\"Implement f.\",\"language\":\"java\"},\"key\":{\"reference_tests\":\"tests specify the expected behaviour\"}}"));
    }

    private static Item item(long id, ItemKind kind, String family, String concept,
                             double difficulty, String payload) {
        return new Item(
                id,
                kind,
                family,
                concept,
                "backend",
                BigDecimal.valueOf(difficulty),
                true,
                null,
                0,
                payload,
                ItemStatus.ACTIVE,
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
