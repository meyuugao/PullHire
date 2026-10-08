package me.yuugao.pullhire.candidate.web;

import me.yuugao.pullhire.candidate.application.ProfileService;
import me.yuugao.pullhire.candidate.error.ApiException;
import me.yuugao.pullhire.candidate.web.dto.InternalCandidateResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /internal/candidates/by-user/{userId}} ({@code docs/api.md §3.2}):
 * maps the JWT {@code sub} (logical {@code user_id}) to the internal candidate id
 * {@code candidate.profiles.id}, which is the id space of {@code fsp.fsp_links.candidate_id}
 * ({@code docs/data_model.md §5.1}). Network-trust only (the JWT filter exempts
 * {@code /internal/} and the gateway does not route it).
 */
@RestController
public class InternalCandidateController {

    private final ProfileService profiles;

    public InternalCandidateController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/internal/candidates/by-user/{userId}")
    public InternalCandidateResponse byUser(@PathVariable("userId") long userId) {
        return profiles.get(userId)
                .map(profile -> new InternalCandidateResponse(profile.id()))
                .orElseThrow(() -> ApiException.notFound("Профиль кандидата не найден"));
    }
}
