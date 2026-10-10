package me.yuugao.pullhire.assessment.web;

import me.yuugao.pullhire.assessment.application.AnswerService;
import me.yuugao.pullhire.assessment.application.SessionStartService;
import me.yuugao.pullhire.assessment.web.dto.AnswerRequest;
import me.yuugao.pullhire.assessment.web.dto.AnswerResponse;
import me.yuugao.pullhire.assessment.web.dto.SessionStartResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /assessment/sessions/{id}} session-progress endpoints
 * ({@code docs/api.md §3.4}): starting a session and serving the first item,
 * then accepting answers ({@code POST /assessment/sessions/{id}/answers}).
 */
@RestController
public class AssessmentSessionController {

    private final SessionStartService start;
    private final AnswerService answers;

    public AssessmentSessionController(SessionStartService start, AnswerService answers) {
        this.start = start;
        this.answers = answers;
    }

    /** Starts the session and returns the first item to serve. */
    @PostMapping("/assessment/sessions/{id}/start")
    public SessionStartResponse start(@PathVariable long id, HttpServletRequest request) {
        return start.start(id, CurrentUser.id(request));
    }

    /**
     * Grades one served item and returns the next item or the completion
     * outcome. The {@code Idempotency-Key} header is enforced per
     * {@code docs/api.md §1} by the idempotency-lib filter with the persistent
     * {@code idempotency_keys} store (S9) — re-posting an already-answered item
     * is additionally a no-op returning the current progress (see
     * {@code AnswerService}).
     */
    @PostMapping("/assessment/sessions/{id}/answers")
    public AnswerResponse answer(@PathVariable long id,
                                 @Valid @RequestBody AnswerRequest body,
                                 @RequestHeader(value = "Idempotency-Key", required = false)
                                 String idempotencyKey,
                                 HttpServletRequest request) {
        return answers.answer(id, CurrentUser.id(request), body, idempotencyKey);
    }
}
