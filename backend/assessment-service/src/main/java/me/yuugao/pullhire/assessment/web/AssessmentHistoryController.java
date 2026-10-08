package me.yuugao.pullhire.assessment.web;

import me.yuugao.pullhire.assessment.application.HistoryService;
import me.yuugao.pullhire.assessment.web.dto.HistoryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /assessment/history} ({@code docs/api.md §3.4}): read-only candidate history. */
@RestController
public class AssessmentHistoryController {

    private final HistoryService history;

    public AssessmentHistoryController(HistoryService history) {
        this.history = history;
    }

    @GetMapping("/assessment/history")
    public HistoryResponse history(HttpServletRequest request) {
        return history.history(CurrentUser.id(request));
    }
}
