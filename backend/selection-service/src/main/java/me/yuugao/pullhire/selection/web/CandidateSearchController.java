package me.yuugao.pullhire.selection.web;

import me.yuugao.pullhire.selection.application.CandidateSearchService;
import me.yuugao.pullhire.selection.web.dto.CandidateCardResponse;
import me.yuugao.pullhire.selection.web.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code GET /candidates} ({@code docs/api.md §3.5}): search over own
 * projections ({@code docs/selection.md §5}). No {@code /api/v1} prefix.
 */
@RestController
@RequestMapping("/candidates")
public class CandidateSearchController {

    private final CandidateSearchService search;

    public CandidateSearchController(CandidateSearchService search) {
        this.search = search;
    }

    @GetMapping
    public PageResponse<CandidateCardResponse> search(
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> stack,
            @RequestParam(name = "fsp-achievements", required = false) Boolean fspAchievements,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CandidateSearchService.SearchPage result = search.search(
                specialization, grade, category, stack, fspAchievements, page, size);
        return new PageResponse<>(result.items(), result.page(), result.size(), result.total());
    }
}
