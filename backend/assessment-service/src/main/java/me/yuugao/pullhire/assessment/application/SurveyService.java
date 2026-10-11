package me.yuugao.pullhire.assessment.application;

import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.repo.ItemBankRepository;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import me.yuugao.pullhire.assessment.web.dto.SurveyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Mandatory SURVEY input stage ({@code docs/testing_model.md §2.1},
 * {@code docs/api.md §3.4}, {@code docs/backlog/closed_questions.md TD-3}).
 *
 * <p>Opens a new {@code assessment.test_sessions} row for the authenticated
 * candidate and records the raw survey answers ({@code survey_answers}, jsonb)
 * and the declared dimensions ({@code declared_dimensions}, text[]) on it
 * ({@code docs/data_model.md §6.1}). The declared dimensions drive the
 * round-robin family selection when the form is assembled
 * ({@link me.yuugao.pullhire.assessment.domain.form.FormAssembler}). The generated
 * session id is returned so the caller can drive the next pipeline stages (grade
 * choice, start).
 *
 * <p>No {@code candidate}-schema write happens here: {@code candidate_id} is the
 * logical candidate id ({@code candidate.profiles.id}) resolved from the JWT
 * by {@code JwtAuthFilter} (no cross-schema FK, {@code docs/data_model.md §1}).
 *
 * <p>Owner-side taxonomy validation ({@code docs/data_model.md §1}): the
 * owning service validates codes against the seeded taxonomy, so unknown
 * {@code specialization_code} / {@code target_grade} fail here with 400
 * {@code validation_error} instead of surfacing later as 409 on {@code /start}.
 * {@code declared_dimensions} are an open vocabulary
 * ({@code docs/data_model.md §6.1}: отрасль/специализация/грейд): blank tokens
 * fail with 400, while any other non-blank token is accepted — known
 * specialization/grade tokens drive form coverage and the rest are treated as
 * industry codes (no industry registry exists to validate against).
 */
@Service
public class SurveyService {

    private static final JsonMapper JSON = new JsonMapper();

    private static final Logger log = LoggerFactory.getLogger(SurveyService.class);

    private final TestSessionRepository sessions;
    private final ItemBankRepository itemBank;
    private final AssessmentProperties properties;

    public SurveyService(TestSessionRepository sessions, ItemBankRepository itemBank,
            AssessmentProperties properties) {
        this.sessions = sessions;
        this.itemBank = itemBank;
        this.properties = properties;
    }

    /**
     * Persists the survey stage and returns the new session id. Mandatory-field
     * validation is enforced by the web layer ({@code @Valid}); this method assumes
     * a validated {@link SurveyRequest} and additionally checks the codes against
     * the seeded taxonomy (specialization, explicit grades list). Declared
     * dimensions are an open vocabulary: only blank tokens are rejected here;
     * coverage matching resolves known specialization/grade tokens and treats the
     * rest as industry codes (see {@link #validateTaxonomy}).
     */
    @Transactional
    public long submit(long candidateId, SurveyRequest request) {
        String specialization = request.specializationCode().trim();
        String grade = request.targetGrade().trim();
        List<String> declared = dimensions(request.declaredDimensions());
        validateTaxonomy(specialization, grade, declared);
        TestSession session = new TestSession(
                0L,
                candidateId,
                trimmed(request.industryCode()),
                specialization,
                grade,
                JSON.writeValueAsString(request.surveyAnswers()),
                declared,
                null,
                SessionStatus.STARTED,
                null,
                null,
                0,
                null,
                null);
        long sessionId = sessions.create(session);
        // Success marker for the chain: MDC correlation_id comes from CorrelationMdcFilter.
        log.info("Опрос принят, сессия открыта: session_id={}", sessionId);
        return sessionId;
    }

    private void validateTaxonomy(String specialization, String grade, List<String> declared) {
        java.util.HashSet<String> specializations = new HashSet<>(itemBank.listDistinctSpecializations());
        if (!specializations.contains(specialization)) {
            throw ApiException.validation("Unknown specialization_code: " + specialization);
        }
        java.util.HashSet<String> grades = new HashSet<>(properties.getGrades());
        if (!grades.contains(grade)) {
            throw ApiException.validation("Unknown target_grade: " + grade);
        }
        // declared_dimensions are industry/specialization/grade values (docs/data_model.md §6.1),
        // NOT concept codes. There is no industry registry, so the vocabulary is open:
        // blank tokens are rejected, known specialization/grade tokens drive form
        // coverage, and any other non-blank token is accepted as an industry code.
        for (String dimension : declared) {
            if (dimension.isBlank()) {
                throw ApiException.validation("Blank declared_dimension");
            }
        }
    }

    private static List<String> dimensions(List<String> declared) {
        return declared.stream().filter(Objects::nonNull).map(String::trim).toList();
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
