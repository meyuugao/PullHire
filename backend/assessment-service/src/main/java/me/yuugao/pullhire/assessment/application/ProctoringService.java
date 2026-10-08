package me.yuugao.pullhire.assessment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.Attempt;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.domain.scoring.GradeBandClassifier;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.repo.AttemptRepository;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proctoring-flag ingestion ({@code docs/testing_model.md §9.2}): persists
 * {@code visibilitychange} / {@code fullscreen_exit} / {@code paste} flags with
 * timestamps into {@code attempts.proctoring_flags} (jsonb) and marks the session
 * {@code needs_review} when flags are present or the SE/CI condition
 * ({@code GradeBandClassifier.needsReview}, {@code §6.3}) holds.
 *
 * <p>Never auto-fails: {@code is_correct} is passed through untouched (never
 * forced to {@code false}) and the session is never abandoned — only
 * {@code NEEDS_REVIEW}. The human decision path is preserved; every numeric
 * threshold (cut-scores, SE threshold, CI multiplier) arrives from
 * {@code AssessmentProperties}, nothing is hardcoded here.
 */
@Service
public class ProctoringService {

    /** Tab hidden / page navigation ({@code §9.2}). */
    public static final String VISIBILITYCHANGE = "visibilitychange";
    /** Fullscreen mode left ({@code §9.2}). */
    public static final String FULLSCREEN_EXIT = "fullscreen_exit";
    /** Clipboard paste into an answer field ({@code §9.2}). */
    public static final String PASTE = "paste";

    private static final Set<String> ALLOWED_FLAGS =
            Set.of(VISIBILITYCHANGE, FULLSCREEN_EXIT, PASTE);

    private static final String EMPTY_FLAGS_JSON = "[]";
    private static final String NULL_JSON_LITERAL = "null";

    private final AttemptRepository attempts;
    private final TestSessionRepository sessions;
    private final AssessmentProperties properties;

    public ProctoringService(AttemptRepository attempts,
                             TestSessionRepository sessions,
                             AssessmentProperties properties) {
        this.attempts = attempts;
        this.sessions = sessions;
        this.properties = properties;
    }

    /** One observed proctoring signal with its capture timestamp. */
    public record ProctoringFlag(String type, Instant occurredAt) {
    }

    /** Ingestion outcome: whether the session routed to manual review plus stored jsonb. */
    public record Outcome(boolean needsReview, String storedFlags) {
    }

    /**
     * Ingests a single flag for an attempt and routes the session to manual
     * review. Convenience overload for {@link #ingest(long, long, List, double, double)}.
     */
    public Outcome ingest(long sessionId, long attemptId, ProctoringFlag flag,
                          double theta, double standardError) {
        return ingest(sessionId, attemptId, flag == null ? List.of() : List.of(flag),
                theta, standardError);
    }
    /**
     * Persists flags with timestamps into {@code attempts.proctoring_flags} and
     * marks the session {@code needs_review} when flags are present or the
     * SE/CI condition holds. Never sets {@code is_correct=false} — the stored
     * answer, correctness and response time pass through untouched — and never
     * abandons the session; only {@code NEEDS_REVIEW}.
     *
     * <p>Note: production flag flow currently arrives via the answers-path
     * append-merge ({@link #mergeJson}); a standalone flag-reporting endpoint
     * calling this method directly is deferred to a follow-up slice.
     *
     * @param sessionId     owning session; the attempt must belong to it
     * @param attemptId     served attempt receiving the flags
     * @param flags         observed flags, may be empty (SE/CI still evaluated)
     * @param theta         ability estimate θ for the SE/CI condition
     * @param standardError standard error SE of θ
     * @return outcome with the review flag and the stored jsonb document
     */
    @Transactional
    public Outcome ingest(long sessionId, long attemptId, List<ProctoringFlag> flags,
                          double theta, double standardError) {
        List<ProctoringFlag> observed = flags == null ? List.of() : List.copyOf(flags);
        for (ProctoringFlag flag : observed) {
            requireValid(flag);
        }
        Attempt attempt = attempts.findBySession(sessionId).stream()
                .filter(candidate -> candidate.id() == attemptId)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Попытка не найдена"));

        String stored = merge(attempt.proctoringFlags(), observed);
        attempts.saveAnswer(attemptId, attempt.answer(), attempt.isCorrect(),
                attempt.responseMs(), stored);

        boolean classifierNeedsReview = GradeBandClassifier.classify(theta, standardError,
                properties.getCutScores(), properties.getSeThreshold(),
                properties.getCiConfidence()).needsReview();
        boolean needsReview = !observed.isEmpty() || classifierNeedsReview;
        if (needsReview) {
            TestSession session = sessions.findById(sessionId)
                    .orElseThrow(() -> ApiException.notFound("Сессия не найдена"));
            sessions.update(review(session, theta, standardError));
        }
        return new Outcome(needsReview, stored);
    }

    private static void requireValid(ProctoringFlag flag) {
        if (flag == null) {
            throw new IllegalArgumentException("proctoring flag must not be null");
        }
        if (flag.type() == null || !ALLOWED_FLAGS.contains(flag.type())) {
            throw new IllegalArgumentException("unknown proctoring flag: " + flag.type());
        }
        if (flag.occurredAt() == null) {
            throw new IllegalArgumentException("proctoring flag timestamp is required");
        }
    }

    private static TestSession review(TestSession session, double theta, double standardError) {
        return new TestSession(
                session.id(),
                session.candidateId(),
                session.industryCode(),
                session.specializationCode(),
                session.targetGrade(),
                session.surveyAnswers(),
                session.declaredDimensions(),
                session.formSeed(),
                SessionStatus.NEEDS_REVIEW,
                BigDecimal.valueOf(theta),
                BigDecimal.valueOf(standardError),
                session.itemCount(),
                session.startedAt(),
                session.completedAt());
    }

    static String merge(String existing, List<ProctoringFlag> observed) {
        String base = normalize(existing);
        // base ends with ']'; drop it so new entries can be appended, then re-close.
        StringBuilder merged = new StringBuilder(base.substring(0, base.length() - 1));
        boolean first = base.equals(EMPTY_FLAGS_JSON);
        for (ProctoringFlag flag : observed) {
            if (!first) {
                merged.append(',');
            }
            merged.append("{\"type\":\"")
                    .append(flag.type())
                    .append("\",\"at\":\"")
                    .append(flag.occurredAt().toString())
                    .append("\"}");
            first = false;
        }
        merged.append(']');
        return merged.toString();
    }

    private static String normalize(String existing) {
        if (existing == null || existing.isBlank() || existing.trim().equals(NULL_JSON_LITERAL)) {
            return EMPTY_FLAGS_JSON;
        }
        String trimmed = existing.trim();
        if (trimmed.startsWith("{")) {
            return "[" + trimmed + "]";
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed;
        }
        return EMPTY_FLAGS_JSON;
    }

    /**
     * Append-merge of two raw jsonb flag documents (the single shared
     * append-merge semantics used by both the answers path and ingestion):
     * concatenates the top-level array elements, preserving what is stored.
     */
    public static String mergeJson(String existing, String incoming) {
        String base = normalize(existing);
        String next = normalize(incoming);
        String baseInner = base.substring(1, base.length() - 1).trim();
        String nextInner = next.substring(1, next.length() - 1).trim();
        if (baseInner.isEmpty()) {
            return next;
        }
        if (nextInner.isEmpty()) {
            return base;
        }
        return "[" + baseInner + "," + nextInner + "]";
    }
}
