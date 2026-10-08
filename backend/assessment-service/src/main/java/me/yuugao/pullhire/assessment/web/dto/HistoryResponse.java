package me.yuugao.pullhire.assessment.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.yuugao.pullhire.assessment.domain.Attempt;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository.GradeHistoryEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /assessment/history} response ({@code docs/api.md §3.4}): the
 * authenticated candidate's sessions (newest first, each with its attempts in
 * serving order) plus the append-only grade-change journal (oldest first).
 *
 * <p>Shapes the domain records so the wire contract does not leak repo internals;
 * enum values are emitted by their stable DB wire text. Wire keys are
 * {@code snake_case} and every identifier is a string ({@code docs/api.md §1});
 * the per-item {@code variant_seed} is never exposed (presentation-only rule,
 * see {@code SessionStartResponse}).
 */
public record HistoryResponse(
        List<SessionHistory> sessions,
        @JsonProperty("grade_history") List<GradeHistoryItem> gradeHistory) {

    /** One session with its served attempts in serving order. */
    public record SessionHistory(
            @JsonProperty("session_id") String id,
            @JsonProperty("industry_code") String industryCode,
            @JsonProperty("specialization_code") String specializationCode,
            @JsonProperty("target_grade") String targetGrade,
            String status,
            BigDecimal theta,
            @JsonProperty("theta_se") BigDecimal thetaSe,
            @JsonProperty("item_count") int itemCount,
            @JsonProperty("started_at") Instant startedAt,
            @JsonProperty("completed_at") Instant completedAt,
            List<AttemptItem> attempts) {

        public static SessionHistory from(TestSession session, List<AttemptItem> attempts) {
            return new SessionHistory(
                    Long.toString(session.id()),
                    session.industryCode(),
                    session.specializationCode(),
                    session.targetGrade(),
                    session.status() == null ? null : session.status().wire(),
                    session.theta(),
                    session.thetaSe(),
                    session.itemCount(),
                    session.startedAt(),
                    session.completedAt(),
                    List.copyOf(attempts));
        }
    }

    /** One served item of a session. */
    public record AttemptItem(
            String id,
            @JsonProperty("item_id") String itemId,
            @JsonProperty("item_order") int itemOrder,
            String answer,
            // Candidate's own result, not a scoring key: no key/b/params leak.
            @JsonProperty("is_correct") Boolean isCorrect,
            @JsonProperty("response_ms") Integer responseMs,
            @JsonProperty("proctoring_flags") String proctoringFlags,
            @JsonProperty("served_at") Instant servedAt) {

        public static AttemptItem from(Attempt attempt) {
            return new AttemptItem(
                    Long.toString(attempt.id()),
                    Long.toString(attempt.itemId()),
                    attempt.itemOrder(),
                    attempt.answer(),
                    attempt.isCorrect(),
                    attempt.responseMs(),
                    attempt.proctoringFlags(),
                    attempt.servedAt());
        }
    }

    /** One append-only grade/category change entry. */
    public record GradeHistoryItem(
            String id,
            String grade,
            @JsonProperty("specialization_code") String specializationCode,
            String direction,
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("category_code") String categoryCode,
            String reason,
            @JsonProperty("created_at") Instant createdAt) {

        public static GradeHistoryItem from(GradeHistoryEntry entry) {
            return new GradeHistoryItem(
                    Long.toString(entry.id()),
                    entry.grade(),
                    entry.specializationCode(),
                    entry.direction() == null ? null : entry.direction().wire(),
                    entry.sessionId() == null ? null : Long.toString(entry.sessionId()),
                    entry.categoryCode(),
                    entry.reason(),
                    entry.createdAt());
        }
    }
}
