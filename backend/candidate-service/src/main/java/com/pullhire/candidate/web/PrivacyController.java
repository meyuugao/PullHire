package com.pullhire.candidate.web;

import com.pullhire.candidate.application.PrivacyService;
import com.pullhire.candidate.web.dto.PrivacyRequest;
import com.pullhire.candidate.web.dto.PrivacyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET|PUT /candidate/privacy} ({@code docs/api.md §3.2}). */
@RestController
@RequestMapping("/candidate/privacy")
public class PrivacyController {

    private final PrivacyService privacy;

    public PrivacyController(PrivacyService privacy) {
        this.privacy = privacy;
    }

    @GetMapping
    public PrivacyResponse get(HttpServletRequest request) {
        return PrivacyResponse.from(privacy.get(CurrentUser.id(request)));
    }

    @PutMapping
    public PrivacyResponse put(@Valid @RequestBody PrivacyRequest body, HttpServletRequest request) {
        return PrivacyResponse.from(privacy.update(CurrentUser.id(request), body.visibleToEmployers(),
                body.showFsp(), body.showContacts()));
    }
}
