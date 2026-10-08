package me.yuugao.pullhire.assessment.web;

import me.yuugao.pullhire.assessment.application.SurveyService;
import me.yuugao.pullhire.assessment.web.dto.SurveyRequest;
import me.yuugao.pullhire.assessment.web.dto.SurveyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /assessment/survey} ({@code docs/api.md §3.4}): the mandatory survey
 * stage ({@code docs/testing_model.md §2.1}). Owner-scoped via the JWT {@code sub};
 * opens the candidate's session carrying the survey answers and declared
 * dimensions and returns its id (TD-3). {@code @Valid} failures surface through
 * {@link ApiExceptionHandler} as the unified problem+json envelope (400
 * {@code validation_error}).
 */
@RestController
@RequestMapping("/assessment")
public class AssessmentSurveyController {

    private final SurveyService surveys;

    public AssessmentSurveyController(SurveyService surveys) {
        this.surveys = surveys;
    }

    @PostMapping("/survey")
    public SurveyResponse survey(@Valid @RequestBody SurveyRequest body, HttpServletRequest request) {
        return SurveyResponse.from(surveys.submit(CurrentUser.id(request), body));
    }
}
