package me.yuugao.pullhire.fsp.web;

import me.yuugao.pullhire.fsp.application.CandidateProfileClient;
import me.yuugao.pullhire.fsp.application.FspLinkService;
import me.yuugao.pullhire.fsp.web.dto.LinkRequest;
import me.yuugao.pullhire.fsp.web.dto.LinkResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST|DELETE /candidate/fsp/link} ({@code docs/api.md §3.3}). Owner-scoped:
 * the JWT {@code sub} is resolved to the logical candidate id
 * ({@code candidate.profiles.id}) via {@link CandidateProfileClient}
 * ({@code docs/fsp_integration.md §2}). Link is idempotent, unlink is a 200 no-op
 * when absent.
 */
@RestController
@RequestMapping("/candidate/fsp/link")
public class FspLinkController {

    private final FspLinkService links;
    private final CandidateProfileClient identities;

    public FspLinkController(FspLinkService links, CandidateProfileClient identities) {
        this.links = links;
        this.identities = identities;
    }

    @PostMapping
    public LinkResponse link(@Valid @RequestBody LinkRequest body, HttpServletRequest request) {
        long candidateId = identities.resolveProfileId(CurrentUser.id(request));
        return LinkResponse.from(links.link(candidateId, body.fspId()));
    }

    @DeleteMapping
    public LinkResponse unlink(HttpServletRequest request) {
        long candidateId = identities.resolveProfileId(CurrentUser.id(request));
        return LinkResponse.from(links.unlink(candidateId));
    }
}
