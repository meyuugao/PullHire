package me.yuugao.pullhire.candidate.web;

import me.yuugao.pullhire.candidate.application.ProfileService;
import me.yuugao.pullhire.candidate.web.dto.ProfileRequest;
import me.yuugao.pullhire.candidate.web.dto.ProfileResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET|PUT /candidate/profile} ({@code docs/api.md §3.2}). */
@RestController
@RequestMapping("/candidate/profile")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ProfileResponse get(HttpServletRequest request) {
        return ProfileResponse.from(profiles.getRequired(CurrentUser.id(request)));
    }

    @PutMapping
    public ProfileResponse put(@Valid @RequestBody ProfileRequest body, HttpServletRequest request) {
        return ProfileResponse.from(profiles.upsert(CurrentUser.id(request), body.toCommand()));
    }
}
