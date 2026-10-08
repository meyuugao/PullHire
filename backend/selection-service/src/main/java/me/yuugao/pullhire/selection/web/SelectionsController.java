package me.yuugao.pullhire.selection.web;

import jakarta.validation.Valid;
import me.yuugao.pullhire.selection.application.SelectionService;
import me.yuugao.pullhire.selection.web.dto.SelectionRequest;
import me.yuugao.pullhire.selection.web.dto.SelectionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /selections} + {@code GET /selections/{id}}
 * ({@code docs/api.md §3.5}): formation of a подборка with explainability
 * plus snapshot persistence ({@code docs/selection.md §5}). No
 * {@code /api/v1} prefix. Refinement is a new {@code POST} with
 * {@code parent_selection_id}; {@code GET} returns either snapshot as stored.
 */
@RestController
@RequestMapping("/selections")
public class SelectionsController {

    private final SelectionService selections;

    public SelectionsController(SelectionService selections) {
        this.selections = selections;
    }

    @PostMapping
    public SelectionResponse create(@Valid @RequestBody SelectionRequest request) {
        return selections.create(request);
    }

    @GetMapping("/{id}")
    public SelectionResponse getById(@PathVariable long id) {
        return selections.getById(id);
    }
}
