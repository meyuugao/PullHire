package me.yuugao.pullhire.assessment.application;

import java.math.BigDecimal;

import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /assessment/grade-choice} ({@code docs/api.md §3.4},
 * {@code docs/testing_model.md §2.2}): fixes the declared grade
 * ({@code declared_grade}) for the session and sets the routing start θ.
 *
 * <p>The declared grade is persisted into {@code test_sessions.target_grade}
 * (no schema change; see the survey/grade-choice merge note in
 * {@code SurveyRequest}): the survey stage already collects {@code target_grade}
 * because the column is {@code NOT NULL}, and this stage confirms or replaces
 * it with the candidate's explicit choice.
 *
 * <p>Start θ comes from {@code AssessmentProperties.startThetaByGrade} by
 * declared grade; an unknown grade fails with 400 {@code validation_error}.
 *
 * <p>No forced downgrade ({@code docs/testing_model.md §7.2}): the choice is
 * always candidate-initiated, so a lower declared grade than the previously
 * stored {@code target_grade} is applied as-is — the result path never
 * retains the higher grade automatically.
 */
@Service
public class GradeChoiceService {

    private final TestSessionRepository sessions;
    private final AssessmentProperties properties;
    private final GradeEligibilityService eligibility;

    public GradeChoiceService(TestSessionRepository sessions, AssessmentProperties properties,
                              GradeEligibilityService eligibility) {
        this.sessions = sessions;
        this.properties = properties;
        this.eligibility = eligibility;
    }

    /**
     * Fixes the declared grade for one session owned by the candidate and sets
     * its start θ from the grade map.
     *
     * @param sessionId   {@code test_sessions.id}
     * @param candidateId authenticated candidate; another candidate's session
     *                    is reported as missing
     * @param declaredGrade candidate's explicit grade choice
     * @return the fixed grade with its start θ
     * @throws ApiException 404 when the session is missing or not owned,
     *                      400 {@code validation_error} for an unknown grade
     */
    @Transactional
    public Outcome choose(long sessionId, long candidateId, String declaredGrade) {
        String grade = declaredGrade == null ? null : declaredGrade.trim();
        Double startTheta = grade == null ? null : properties.getStartThetaByGrade().get(grade);
        if (grade == null || grade.isEmpty() || startTheta == null) {
            throw ApiException.validation("Unknown declared_grade: " + declaredGrade);
        }
        TestSession session = sessions.findById(sessionId)
                .filter(owned -> owned.candidateId() == candidateId)
                .orElseThrow(() -> ApiException.notFound("Сессия не найдена"));
        if (session.status() != me.yuugao.pullhire.assessment.domain.SessionStatus.STARTED) {
            throw ApiException.conflict("Сессия уже начата");
        }
        // §7.3 scopes the cooldown gate to a grade CHANGE only: re-confirming
        // the session's current grade is a non-change and never gated.
        boolean gradeChange = session.targetGrade() == null || !session.targetGrade().equals(grade);
        if (gradeChange && eligibility != null && !eligibility.check(candidateId).eligible()) {
            throw ApiException.conflict("Повторное изменение грейда недоступно до окончания cooldown");
        }
        // Never clobber θ once serving has begun: only a not-yet-started
        // session (null θ) receives the start θ; otherwise keep the estimate.
        BigDecimal theta = session.theta() != null ? session.theta()
                : BigDecimal.valueOf(startTheta);
        TestSession updated = new TestSession(
                session.id(),
                session.candidateId(),
                session.industryCode(),
                session.specializationCode(),
                grade,
                session.surveyAnswers(),
                session.declaredDimensions(),
                session.formSeed(),
                session.status(),
                theta,
                session.thetaSe(),
                session.itemCount(),
                session.startedAt(),
                session.completedAt());
        sessions.update(updated);
        return new Outcome(sessionId, grade, theta.doubleValue());
    }

    /** Fixed grade with the routing start θ applied to the session. */
    public record Outcome(long sessionId, String declaredGrade, double startTheta) {
    }
}
