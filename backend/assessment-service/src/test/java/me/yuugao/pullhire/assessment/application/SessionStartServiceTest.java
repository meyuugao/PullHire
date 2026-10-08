package me.yuugao.pullhire.assessment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.Attempt;
import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.ItemKind;
import me.yuugao.pullhire.assessment.domain.ItemStatus;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.repo.AttemptRepository;
import me.yuugao.pullhire.assessment.repo.ItemBankRepository;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import me.yuugao.pullhire.assessment.web.dto.SessionStartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit checks (no Spring context) for {@link SessionStartService}
 * ({@code docs/testing_model.md §3.4, §4, §9.1}): deterministic assembly from
 * {@code form_seed}, first-attempt persistence with {@code variant_seed},
 * idempotent re-start, and an anti-leak response surface.
 */
class SessionStartServiceTest {

    private static final long SESSION_ID = 7L;
    private static final long CANDIDATE_ID = 42L;
    private static final long FORM_SEED = 12345L;
    private static final JsonMapper JSON = new JsonMapper();

    private TestSessionRepository sessions;
    private ItemBankRepository items;
    private AttemptRepository attempts;
    private SessionStartService service;

    @BeforeEach
    void setUp() {
        sessions = mock(TestSessionRepository.class);
        items = mock(ItemBankRepository.class);
        attempts = mock(AttemptRepository.class);
        service = new SessionStartService(sessions, items, attempts, new AssessmentProperties());

        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.STARTED)));
        when(items.findActiveBySpecialization("backend")).thenReturn(bank());
        when(attempts.priorServedVariants(anyLong(), anyString())).thenReturn(List.of());
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of());
        when(attempts.insertIgnoringConflict(any(Attempt.class))).thenReturn(Optional.of(1L));
        when(sessions.update(any(TestSession.class))).thenReturn(1);
    }

    @Test
    void sameSeedYieldsIdenticalResponseAcrossRepeatedCalls() {
        SessionStartResponse first = service.start(SESSION_ID, CANDIDATE_ID);
        Attempt persisted = new Attempt(1L, SESSION_ID, Long.parseLong(first.item().itemId()), 1,
                555L, null, null, null, null, Instant.EPOCH);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(persisted));
        SessionStartResponse second = service.start(SESSION_ID, CANDIDATE_ID);

        assertThat(second).isEqualTo(first);
        assertThat(second.item().itemId()).isEqualTo(first.item().itemId());
        assertThat(second.item().optionOrder()).isEqualTo(first.item().optionOrder());
    }

    @Test
    void firstAttemptIsPersistedWithVariantSeedAndOrder() {
        SessionStartResponse response = service.start(SESSION_ID, CANDIDATE_ID);

        ArgumentCaptor<Attempt> captor = ArgumentCaptor.forClass(Attempt.class);
        verify(attempts).insertIgnoringConflict(captor.capture());
        Attempt persisted = captor.getValue();

        assertThat(persisted.sessionId()).isEqualTo(SESSION_ID);
        assertThat(persisted.itemId()).isEqualTo(Long.parseLong(response.item().itemId()));
        assertThat(persisted.itemOrder()).isEqualTo(1);
        assertThat(persisted.variantSeed()).isNotNull();
        assertThat(persisted.answer()).isNull();
        assertThat(persisted.isCorrect()).isNull();
    }

    @Test
    void servingIncrementsExposureAndRetiresWhenExhausted() {
        SessionStartResponse response = service.start(SESSION_ID, CANDIDATE_ID);
        long itemId = Long.parseLong(response.item().itemId());

        verify(items).incrementExposure(itemId);
    }

    @Test
    void exhaustedItemIsRetiredAfterIncrement() {
        when(items.isExposureExhausted(anyLong())).thenReturn(true);

        SessionStartResponse response = service.start(SESSION_ID, CANDIDATE_ID);

        verify(items).incrementExposure(Long.parseLong(response.item().itemId()));
        verify(items).retire(Long.parseLong(response.item().itemId()));
    }

    @Test
    void idempotentReserviceDoesNotTouchExposure() {
        SessionStartResponse first = service.start(SESSION_ID, CANDIDATE_ID);
        Attempt persisted = new Attempt(1L, SESSION_ID, Long.parseLong(first.item().itemId()), 1,
                555L, null, null, null, null, Instant.EPOCH);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(persisted));

        org.mockito.Mockito.clearInvocations(items);
        service.start(SESSION_ID, CANDIDATE_ID);

        verify(items, never()).incrementExposure(anyLong());
        verify(items, never()).retire(anyLong());
    }

    @Test
    void concurrentDuplicateInsertReservesPersistedAttempt() {
        SessionStartResponse probe = service.start(SESSION_ID, CANDIDATE_ID);
        long firstItemId = Long.parseLong(probe.item().itemId());
        Attempt winner = new Attempt(2L, SESSION_ID, firstItemId, 1, 777L,
                null, null, null, null, Instant.EPOCH);
        // ON CONFLICT DO NOTHING: loser insert returns empty without throwing,
        // so the surrounding transaction stays usable and start stays idempotent.
        when(attempts.insertIgnoringConflict(any(Attempt.class))).thenReturn(Optional.empty());
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(winner));

        SessionStartResponse response = service.start(SESSION_ID, CANDIDATE_ID);

        assertThat(response.sessionId()).isEqualTo(Long.toString(SESSION_ID));
        assertThat(response.item().itemId()).isEqualTo(Long.toString(firstItemId));
        verify(attempts, never()).insert(any(Attempt.class));
    }

    @Test
    void persistedAttemptWithDifferentItemIsReservedByOrder() {
        SessionStartResponse probe = service.start(SESSION_ID, CANDIDATE_ID);
        long otherItemId = Long.parseLong(probe.item().itemId()) == 20L ? 21L : 20L;
        Attempt persisted = new Attempt(99L, SESSION_ID, otherItemId, 1, 555L,
                null, null, null, null, Instant.EPOCH);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(persisted));

        SessionStartResponse response = service.start(SESSION_ID, CANDIDATE_ID);

        assertThat(response.item().itemId()).isEqualTo(Long.toString(otherItemId));
        assertThat(response.item().itemKind()).isEqualTo("template");
        assertThat(response.item().content()).isNotNull();
    }

    @Test
    void startingPersistsFormSeedAndAdvancesStatusToInProgress() {
        service.start(SESSION_ID, CANDIDATE_ID);

        ArgumentCaptor<TestSession> captor = ArgumentCaptor.forClass(TestSession.class);
        verify(sessions).update(captor.capture());
        TestSession updated = captor.getValue();

        assertThat(updated.id()).isEqualTo(SESSION_ID);
        assertThat(updated.formSeed()).isEqualTo(FORM_SEED);
        assertThat(updated.status()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    void alreadyStartedSessionReusesPersistedAttemptWithoutDuplicateInsert() {
        SessionStartResponse probe = service.start(SESSION_ID, CANDIDATE_ID);
        Attempt existing = new Attempt(99L, SESSION_ID, Long.parseLong(probe.item().itemId()), 1,
                555L, null, null, null, null, Instant.EPOCH);
        when(attempts.findBySession(SESSION_ID)).thenReturn(List.of(existing));

        org.mockito.Mockito.clearInvocations(attempts);
        service.start(SESSION_ID, CANDIDATE_ID);

        verify(attempts, never()).insertIgnoringConflict(any(Attempt.class));
    }

    @Test
    void responseUsesSnakeCaseKeysAndStringIds() {
        SessionStartResponse response = service.start(SESSION_ID, CANDIDATE_ID);

        assertThat(response.sessionId()).isEqualTo(Long.toString(SESSION_ID));
        assertThat(response.item().itemId()).matches("\\d+");

        assertThat(response.item().content()).isNotNull();
        assertThat(response.item().content().get("key")).isNull();

        String json = JSON.writeValueAsString(response);
        assertThat(json).doesNotContain("\"key\"");
        assertThat(json).doesNotContain("correct");
        assertThat(json).doesNotContain("difficulty");
        assertThat(json).doesNotContain("variant_seed");
        assertThat(json).doesNotContain("sessionId");
        assertThat(json).doesNotContain("itemId");
        assertThat(json).doesNotContain("itemKind");
        assertThat(json).doesNotContain("optionOrder");
        assertThat(json).contains("\"session_id\"").contains("\"item_id\"")
                .contains("\"item_kind\"").contains("\"option_order\"");
    }

    @Test
    void foreignOrUnknownSessionIsNotFound() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(sessionWithCandidate(99L)));

        assertThatThrownBy(() -> service.start(SESSION_ID, CANDIDATE_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("not_found"));
    }

    @Test
    void terminalSessionIsRejectedAsConflict() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.COMPLETED)));

        assertThatThrownBy(() -> service.start(SESSION_ID, CANDIDATE_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("conflict"));
    }

    private static TestSession session(SessionStatus status) {
        return sessionWithCandidate(CANDIDATE_ID, status);
    }

    private static TestSession sessionWithCandidate(long candidateId) {
        return sessionWithCandidate(candidateId, SessionStatus.STARTED);
    }

    private static TestSession sessionWithCandidate(long candidateId, SessionStatus status) {
        return new TestSession(
                SESSION_ID,
                candidateId,
                "it",
                "backend",
                "junior",
                "{}",
                List.of("backend.concept.single-choice", "backend.concept.multiple-choice", "backend.concept.code"),
                FORM_SEED,
                status,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                0,
                Instant.EPOCH,
                null);
    }

    private static List<Item> bank() {
        return List.of(
                item(1L, ItemKind.ANCHOR, null, "backend.anchor.baseline", 0.0,
                        "{\"model\":{\"format\":\"short_answer\",\"statement\":\"Which HTTP status code signals an internal server error?\",\"answer_type\":\"integer\"},\"key\":{\"value\":500}}"),
                item(20L, ItemKind.TEMPLATE, "backend.family.single-choice", "backend.concept.single-choice", -1.0,
                        "{\"model\":{\"format\":\"single_choice\",\"statement\":\"Choose one for {{scenario}}.\",\"parameters\":[\"scenario\"],\"options\":[\"option-a\",\"option-b\",\"option-c\",\"option-d\"]},\"key\":{\"correct\":\"option-b\"}}"),
                item(21L, ItemKind.TEMPLATE, "backend.family.multiple-choice", "backend.concept.multiple-choice", 0.0,
                        "{\"model\":{\"format\":\"multiple_choice\",\"statement\":\"Select all for {{scenario}}.\",\"parameters\":[\"scenario\"],\"options\":[\"statement-a\",\"statement-b\",\"statement-c\",\"statement-d\"]},\"key\":{\"correct\":[\"statement-a\",\"statement-c\"]}}"),
                item(22L, ItemKind.TEMPLATE, "backend.family.code", "backend.concept.code", 1.0,
                        "{\"model\":{\"format\":\"code\",\"statement\":\"Implement {{function}}.\",\"parameters\":[\"function\"],\"language\":\"java\"},\"key\":{\"reference_tests\":\"tests specify the expected behaviour\"}}"));
    }

    private static Item item(long id, ItemKind kind, String family, String concept, double difficulty, String payload) {
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
