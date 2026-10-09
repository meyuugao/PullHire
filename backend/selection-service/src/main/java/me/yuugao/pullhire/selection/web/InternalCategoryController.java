package me.yuugao.pullhire.selection.web;

import me.yuugao.pullhire.selection.error.ApiException;
import me.yuugao.pullhire.selection.repo.CandidateCategoryRepository;
import me.yuugao.pullhire.selection.web.dto.CandidateCategoryInternalResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /internal/candidates/{id}/category} ({@code docs/api.md §3.5}):
 * the candidate's current category for outreach-service. Network-trust only
 * (no JWT filter in selection-service; the gateway does not route
 * {@code /internal/}).
 */
@RestController
public class InternalCategoryController {

    private final CandidateCategoryRepository categories;

    public InternalCategoryController(CandidateCategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping("/internal/candidates/{id}/category")
    public CandidateCategoryInternalResponse category(@PathVariable("id") long id) {
        return categories.findByCandidateId(id)
                .map(row -> new CandidateCategoryInternalResponse(
                        Long.toString(id),
                        row.specializationCode(),
                        row.grade(),
                        row.categoryCode()))
                .orElseThrow(() -> ApiException.notFound("Категория кандидата не найдена"));
    }
}
