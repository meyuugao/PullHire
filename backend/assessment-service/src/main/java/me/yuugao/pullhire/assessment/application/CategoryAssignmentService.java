package me.yuugao.pullhire.assessment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.GradeDirection;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.domain.scoring.GradeBandClassifier;
import me.yuugao.pullhire.assessment.domain.scoring.GradeBandClassifier.GradeBand;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.repo.CooldownRepository;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository.GradeHistoryEntry;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completion orchestration for any completed attempt
 * ({@code docs/testing_model.md §7.1, §7.2, §7.3, §8};
 * {@code docs/backlog/closed_questions.md} TD-1; {@code docs/assumptions.md §2}).
 *
 * <p>Every completed attempt classifies {@code θ + SE} into a grade band via
 * {@link GradeBandClassifier}, appends one {@code grade_history} row
 * ({@code initial}/{@code up}/{@code down}/{@code recheck}) and republishes
 * {@code assessment.category_assigned} with the current category (TD-1: including
 * retries and up/down moves). {@code assessment.grade_changed} is
 * audit/notification-only and never drives the category projection.
 *
 * <p>Cooldown ({@code §7.3}): set only on an actual grade change — an {@code up}
 * move — as {@code now + 1 month}. A failed attempt and a downward move never
 * burn a cooldown ({@code assumptions §2}).
 *
 * <p>No forced downgrade ({@code §7.2}): when the fresh band ranks below the
 * candidate's current grade and the move is not candidate-initiated, the current
 * grade is retained (recorded as {@code recheck}).
 *
 * <p>Borderline attempts ({@code §6.3}, {@code §8}): when the classifier flags
 * {@code needs_review}, no category is auto-assigned — the session routes to
 * manual review, and no history row, event or cooldown is produced.
 */
@Service
public class CategoryAssignmentService {

    private final TestSessionRepository sessions;
    private final GradeHistoryRepository gradeHistory;
    private final CooldownRepository cooldowns;
    private final EventPublisher events;
    private final AssessmentProperties properties;
    private final GradeEligibilityService eligibility;

    public CategoryAssignmentService(TestSessionRepository sessions,
                                     GradeHistoryRepository gradeHistory,
                                     CooldownRepository cooldowns,
                                     EventPublisher events,
                                     AssessmentProperties properties,
                                     GradeEligibilityService eligibility) {
        this.sessions = sessions;
        this.gradeHistory = gradeHistory;
        this.cooldowns = cooldowns;
        this.events = events;
        this.properties = properties;
        this.eligibility = eligibility;
    }

    /** Legacy seam: eligibility derived from the cooldown repository. */
    public CategoryAssignmentService(TestSessionRepository sessions,
                                     GradeHistoryRepository gradeHistory,
                                     CooldownRepository cooldowns,
                                     EventPublisher events,
                                     AssessmentProperties properties) {
        this(sessions, gradeHistory, cooldowns, events, properties,
                new GradeEligibilityService(cooldowns));
    }

    /**
     * Completes one session owned by the candidate without a candidate-initiated
     * downgrade, using the current instant.
     */
    public Outcome complete(long sessionId, long candidateId, double theta, double standardError) {
        return complete(sessionId, candidateId, theta, standardError, false, Instant.now());
    }

    /**
     * Completes one session owned by the candidate, using the current instant.
     *
     * @param candidateInitiatedDowngrade {@code true} only when the candidate
     *                                    explicitly chose a lower-grade attempt;
     *                                    a lower band without this flag never
     *                                    downgrades the candidate ({@code §7.2})
     */
    public Outcome complete(long sessionId, long candidateId, double theta, double standardError,
                            boolean candidateInitiatedDowngrade) {
        return complete(sessionId, candidateId, theta, standardError,
                candidateInitiatedDowngrade, Instant.now());
    }

    /** Deterministic seam for tests: the cooldown {@code available_after} derives from {@code now}. */
    Outcome complete(long sessionId, long candidateId, double theta, double standardError,
                     boolean candidateInitiatedDowngrade, Instant now) {
        TestSession session = sessions.findById(sessionId)
                .filter(owned -> owned.candidateId() == candidateId)
                .orElseThrow(() -> ApiException.notFound("Сессия не найдена"));

        if (session.status() == SessionStatus.COMPLETED
                || session.status() == SessionStatus.NEEDS_REVIEW) {
            return terminalOutcome(session, candidateId);
        }

        GradeBand band = GradeBandClassifier.classify(theta, standardError,
                properties.getCutScores(), properties.getSeThreshold(), properties.getCiConfidence());

        if (band.needsReview()) {
            sessions.update(progress(session, SessionStatus.NEEDS_REVIEW, theta, standardError, null));
            return Outcome.manual(band.grade());
        }

        String priorGrade = currentGrade(candidateId, session.specializationCode());
        String effectiveGrade = band.grade();
        if (priorGrade != null && ranksBelow(effectiveGrade, priorGrade) && !candidateInitiatedDowngrade) {
            effectiveGrade = priorGrade;
        }
        // §7.3: an active grade-change cooldown blocks any repeat grade change.
        // Retain the current grade, burn no new cooldown, still republish
        // category_assigned (TD-1) with the unchanged current category.
        // Cooldowns are per-candidate (data_model §6.1 UNIQUE(candidate_id,
        // scope)): the gate is evaluated candidate-wide even for a first-ever
        // attempt in this specialization (priorGrade == null), in which case
        // the band grade is kept but recorded as RECHECK with no new cooldown.
        boolean cooldownActive = !eligibility.check(candidateId, now).eligible();
        if (cooldownActive && priorGrade != null) {
            effectiveGrade = priorGrade;
        }
        GradeDirection direction = cooldownActive ? GradeDirection.RECHECK
                : directionOf(priorGrade, effectiveGrade);
        boolean gradeChanged = !cooldownActive && priorGrade != null && !effectiveGrade.equals(priorGrade);
        String categoryCode = session.specializationCode() + "-" + effectiveGrade;

        sessions.update(progress(session, SessionStatus.COMPLETED, theta, standardError, now));
        gradeHistory.append(candidateId, effectiveGrade, session.specializationCode(), direction,
                sessionId, categoryCode, null, now);

        String sessionRef = Long.toString(sessionId);
        events.publishCategoryAssigned(candidateId, session.specializationCode(), effectiveGrade,
                categoryCode, sessionRef, now);
        events.publishGradeChanged(candidateId, session.specializationCode(), effectiveGrade,
                direction, categoryCode, sessionRef, null);

        if (direction == GradeDirection.UP) {
            cooldowns.upsert(candidateId, now.atZone(ZoneOffset.UTC)
                    .plus(properties.getCooldownDuration()).toInstant());
        }
        return Outcome.assigned(effectiveGrade, categoryCode, direction, gradeChanged);
    }

    /** Terminal-status idempotent no-op: already COMPLETED/NEEDS_REVIEW completes publish nothing. */
    private Outcome terminalOutcome(TestSession session, long candidateId) {
        GradeHistoryEntry latest = latestEntry(candidateId, session.specializationCode());
        String grade = latest != null ? latest.grade() : session.targetGrade();
        if (session.status() == SessionStatus.NEEDS_REVIEW) {
            return Outcome.manual(grade);
        }
        if (grade == null) {
            return Outcome.assigned(null, null, GradeDirection.RECHECK, false);
        }
        String categoryCode = latest != null && latest.categoryCode() != null ? latest.categoryCode()
                : session.specializationCode() + "-" + grade;
        return Outcome.assigned(grade, categoryCode, GradeDirection.RECHECK, false);
    }

    private GradeHistoryEntry latestEntry(long candidateId, String specializationCode) {
        List<GradeHistoryEntry> history = gradeHistory.listByCandidate(candidateId);
        if (history == null || history.isEmpty()) {
            return null;
        }
        GradeHistoryEntry latest = null;
        for (GradeHistoryEntry row : history) {
            if (specializationCode != null && specializationCode.equals(row.specializationCode())) {
                latest = row;
            }
        }
        return latest;
    }

    private String currentGrade(long candidateId, String specializationCode) {
        List<GradeHistoryEntry> history = gradeHistory.listByCandidate(candidateId);
        if (history == null || history.isEmpty()) {
            return null;
        }
        String prior = null;
        for (GradeHistoryEntry row : history) {
            if (specializationCode != null && specializationCode.equals(row.specializationCode())) {
                prior = row.grade();
            }
        }
        return prior;
    }

    private GradeDirection directionOf(String priorGrade, String effectiveGrade) {
        if (priorGrade == null) {
            return GradeDirection.INITIAL;
        }
        if (effectiveGrade.equals(priorGrade)) {
            return GradeDirection.RECHECK;
        }
        int priorRank = rankOf(priorGrade);
        int effectiveRank = rankOf(effectiveGrade);
        if (priorRank >= 0 && effectiveRank >= 0) {
            return effectiveRank > priorRank ? GradeDirection.UP : GradeDirection.DOWN;
        }
        return GradeDirection.RECHECK;
    }

    private boolean ranksBelow(String candidate, String prior) {
        int candidateRank = rankOf(candidate);
        int priorRank = rankOf(prior);
        return candidateRank >= 0 && priorRank >= 0 && candidateRank < priorRank;
    }

    private int rankOf(String grade) {
        List<Map.Entry<String, Double>> ordered =
                new ArrayList<>(properties.getCutScores().entrySet());
        ordered.sort(Comparator.comparingDouble(Map.Entry::getValue));
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getKey().equals(grade)) {
                return i;
            }
        }
        return -1;
    }

    private static TestSession progress(TestSession session, SessionStatus status,
                                        double theta, double standardError, Instant completedAt) {
        return new TestSession(
                session.id(),
                session.candidateId(),
                session.industryCode(),
                session.specializationCode(),
                session.targetGrade(),
                session.surveyAnswers(),
                session.declaredDimensions(),
                session.formSeed(),
                status,
                BigDecimal.valueOf(theta),
                BigDecimal.valueOf(standardError),
                session.itemCount(),
                session.startedAt(),
                completedAt);
    }

    /** Outcome of a completion: either auto-assigned or routed to manual review. */
    public record Outcome(String grade, String categoryCode, GradeDirection direction,
                          boolean gradeChanged, boolean manualReview) {

        static Outcome assigned(String grade, String categoryCode, GradeDirection direction,
                                boolean gradeChanged) {
            return new Outcome(grade, categoryCode, direction, gradeChanged, false);
        }

        static Outcome manual(String band) {
            return new Outcome(band, null, null, false, true);
        }
    }
}
