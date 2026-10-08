package me.yuugao.pullhire.assessment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One {@code assessment.test_sessions} row ({@code docs/data_model.md §6.1}): a
 * candidate's grade-test session carrying the form seed and the running IRT-lite
 * ability estimate ({@code theta} / {@code theta_se}).
 *
 * <p>{@code candidateId} is a logical reference to {@code candidate.profiles.id}
 * (no cross-schema FK). {@code surveyAnswers} and the JSON-valued attempt
 * columns are the raw {@code jsonb} model text. {@code declaredDimensions} is the
 * {@code text[]} of dimensions declared in the mandatory survey stage.
 */
public record TestSession(
        long id,
        long candidateId,
        String industryCode,
        String specializationCode,
        String targetGrade,
        String surveyAnswers,
        List<String> declaredDimensions,
        Long formSeed,
        SessionStatus status,
        BigDecimal theta,
        BigDecimal thetaSe,
        int itemCount,
        Instant startedAt,
        Instant completedAt) {
}
