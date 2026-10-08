package me.yuugao.pullhire.assessment.web;

import me.yuugao.pullhire.assessment.application.GradeChoiceService;
import me.yuugao.pullhire.assessment.application.GradeChoiceService.Outcome;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.web.dto.GradeChoiceRequest;
import me.yuugao.pullhire.assessment.web.dto.GradeChoiceResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /assessment/grade-choice} ({@code docs/api.md §3.4}): the
 * grade-choice stage ({@code docs/testing_model.md §2.2}). Owner-scoped via
 * the JWT {@code sub}; fixes {@code declared_grade} (persisted as
 * {@code test_sessions.target_grade}) and the routing start θ for the
 * session. {@code @Valid} failures and unknown grades surface as the unified
 * problem+json envelope (400 {@code validation_error}).
 */
@RestController
@RequestMapping("/assessment")
public class AssessmentGradeController {

    private final GradeChoiceService grades;

    public AssessmentGradeController(GradeChoiceService grades) {
        this.grades = grades;
    }

    @PostMapping("/grade-choice")
    public GradeChoiceResponse gradeChoice(@Valid @RequestBody GradeChoiceRequest body,
                                           HttpServletRequest request) {
        long candidateId = CurrentUser.id(request);
        long sessionId = sessionId(body.sessionId());
        Outcome outcome = grades.choose(sessionId, candidateId, body.declaredGrade());
        return GradeChoiceResponse.from(outcome.sessionId(), outcome.declaredGrade(), outcome.startTheta());
    }

    private static long sessionId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException | NullPointerException ex) {
            throw ApiException.validation("Некорректный session_id");
        }
    }
}
