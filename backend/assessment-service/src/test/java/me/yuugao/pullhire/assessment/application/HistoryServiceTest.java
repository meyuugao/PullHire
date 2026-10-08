package me.yuugao.pullhire.assessment.application;

import tools.jackson.databind.json.JsonMapper;
import me.yuugao.pullhire.assessment.domain.Attempt;
import me.yuugao.pullhire.assessment.domain.GradeDirection;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.repo.AttemptRepository;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository.GradeHistoryEntry;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import me.yuugao.pullhire.assessment.web.dto.HistoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit checks (no Spring, no DB) for {@link HistoryService}: it composes
 * sessions with their attempts, preserves repository time ordering, scopes to the
 * candidate id, and issues read calls only.
 */
class HistoryServiceTest {

    private TestSessionRepository sessions;
    private AttemptRepository attempts;
    private GradeHistoryRepository gradeHistory;
    private HistoryService service;

    @BeforeEach
    void setUp() {
        sessions = mock(TestSessionRepository.class);
        attempts = mock(AttemptRepository.class);
        gradeHistory = mock(GradeHistoryRepository.class);
        service = new HistoryService(sessions, attempts, gradeHistory);
    }

    @Test
    void composesEachSessionWithItsOwnAttemptsInServingOrder() {
        TestSession newest = session(2L, "spec-2", Instant.parse("2026-02-02T10:00:00Z"));
        TestSession older = session(1L, "spec-1", Instant.parse("2026-02-01T10:00:00Z"));
        when(sessions.findByCandidate(42L)).thenReturn(List.of(newest, older));
        when(attempts.findBySession(2L)).thenReturn(List.of(attempt(20L, 2L, 1), attempt(21L, 2L, 2)));
        when(attempts.findBySession(1L)).thenReturn(List.of(attempt(10L, 1L, 1)));
        when(gradeHistory.listByCandidate(42L)).thenReturn(List.of());

        HistoryResponse response = service.history(42L);

        assertThat(response.sessions()).extracting(HistoryResponse.SessionHistory::id).containsExactly("2", "1");
        assertThat(response.sessions().get(0).attempts())
                .extracting(HistoryResponse.AttemptItem::itemOrder).containsExactly(1, 2);
        assertThat(response.sessions().get(0).attempts())
                .extracting(HistoryResponse.AttemptItem::itemId).containsExactly("2000", "2100");
        assertThat(response.sessions().get(1).attempts())
                .extracting(HistoryResponse.AttemptItem::itemId).containsExactly("1000");
        assertThat(response.gradeHistory()).isEmpty();
    }

    @Test
    void preservesGradeHistoryOldestToNewest() {
        when(sessions.findByCandidate(42L)).thenReturn(List.of());
        when(gradeHistory.listByCandidate(42L)).thenReturn(List.of(
                grade(1L, Instant.parse("2026-01-01T09:00:00Z"), GradeDirection.INITIAL),
                grade(2L, Instant.parse("2026-02-01T09:00:00Z"), GradeDirection.UP)));

        HistoryResponse response = service.history(42L);

        assertThat(response.sessions()).isEmpty();
        assertThat(response.gradeHistory()).extracting(HistoryResponse.GradeHistoryItem::id)
                .containsExactly("1", "2");
        assertThat(response.gradeHistory()).extracting(HistoryResponse.GradeHistoryItem::direction)
                .containsExactly("initial", "up");
    }

    @Test
    void stringifiesIdentifiersAndDropsVariantSeedOnTheWire() throws Exception {
        TestSession session = session(2L, "spec-2", Instant.parse("2026-02-02T10:00:00Z"));
        when(sessions.findByCandidate(42L)).thenReturn(List.of(session));
        when(attempts.findBySession(2L)).thenReturn(List.of(attempt(20L, 2L, 1)));
        when(gradeHistory.listByCandidate(42L)).thenReturn(
                List.of(grade(1L, Instant.parse("2026-01-01T09:00:00Z"), GradeDirection.INITIAL)));

        HistoryResponse response = service.history(42L);

        String json = new JsonMapper().writeValueAsString(response);
        assertThat(json).contains("\"session_id\":\"2\"");
        assertThat(json).contains("\"item_id\":\"2000\"");
        assertThat(json).doesNotContain("variant_seed");
        assertThat(json).doesNotContain("variantSeed");
        assertThat(json).contains("\"grade_history\"");
        assertThat(json).contains("\"is_correct\"");
    }

    @Test
    void scopesEveryReadToTheCandidateId() {
        when(sessions.findByCandidate(7L)).thenReturn(List.of());
        when(gradeHistory.listByCandidate(7L)).thenReturn(List.of());

        service.history(7L);

        verify(sessions).findByCandidate(7L);
        verify(gradeHistory).listByCandidate(7L);
    }

    @Test
    void returnsEmptyHistoryWhenNothingExists() {
        when(sessions.findByCandidate(42L)).thenReturn(List.of());
        when(gradeHistory.listByCandidate(42L)).thenReturn(List.of());

        HistoryResponse response = service.history(42L);

        assertThat(response.sessions()).isEmpty();
        assertThat(response.gradeHistory()).isEmpty();
    }

    @Test
    void isReadOnlyAndNeverInvokesRepositoryWrites() {
        when(sessions.findByCandidate(42L)).thenReturn(List.of(session(1L, "spec-1", Instant.EPOCH)));
        when(attempts.findBySession(1L)).thenReturn(List.of());
        when(gradeHistory.listByCandidate(42L)).thenReturn(List.of());

        service.history(42L);

        verify(sessions).findByCandidate(42L);
        verify(attempts).findBySession(1L);
        verify(gradeHistory).listByCandidate(42L);
        verify(sessions, never()).create(any());
        verify(sessions, never()).update(any());
        verify(attempts, never()).insert(any());
        verify(attempts, never()).saveAnswer(anyLong(), any(), any(), any(), any());
        verify(gradeHistory, never()).append(anyLong(), any(), any(), any(), any(), any(), any(), any());
        verifyNoMoreInteractions(sessions, attempts, gradeHistory);
    }

    private static TestSession session(long id, String specializationCode, Instant startedAt) {
        return new TestSession(id, 42L, "industry-1", specializationCode, "middle", null, List.of(), 777L,
                SessionStatus.COMPLETED, new BigDecimal("0.125"), new BigDecimal("0.300"), 2,
                startedAt, startedAt.plusSeconds(1800));
    }

    private static Attempt attempt(long id, long sessionId, int itemOrder) {
        return new Attempt(id, sessionId, id * 100, itemOrder, 5L, "{\"choice\":1}", true, 1234, null,
                Instant.parse("2026-02-01T10:05:00Z"));
    }

    private static GradeHistoryEntry grade(long id, Instant createdAt, GradeDirection direction) {
        return new GradeHistoryEntry(id, 42L, "middle", "spec-1", direction, 1L, null, null, createdAt);
    }
}
