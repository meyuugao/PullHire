package me.yuugao.pullhire.assessment.web;

import me.yuugao.pullhire.assessment.application.GradeEligibilityService;
import me.yuugao.pullhire.assessment.web.dto.EligibilityResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /assessment/grade-eligibility} ({@code docs/api.md §3.4}): whether a
 * grade change is allowed for the authenticated candidate under the 1-month
 * cooldown ({@code docs/testing_model.md §7.3}).
 */
@RestController
@RequestMapping("/assessment")
public class AssessmentEligibilityController {

    private final GradeEligibilityService eligibility;

    public AssessmentEligibilityController(GradeEligibilityService eligibility) {
        this.eligibility = eligibility;
    }

    @GetMapping("/grade-eligibility")
    public EligibilityResponse gradeEligibility(HttpServletRequest request) {
        return EligibilityResponse.from(eligibility.check(CurrentUser.id(request)));
    }
}
