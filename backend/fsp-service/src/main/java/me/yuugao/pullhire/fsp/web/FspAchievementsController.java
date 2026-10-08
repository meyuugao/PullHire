package me.yuugao.pullhire.fsp.web;

import me.yuugao.pullhire.fsp.application.CandidateProfileClient;
import me.yuugao.pullhire.fsp.application.FspQueryService;
import me.yuugao.pullhire.fsp.domain.FspLink;
import me.yuugao.pullhire.fsp.web.dto.AchievementsResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /candidate/fsp/achievements} ({@code docs/api.md §3.3}). No active link
 * → 404; {@code history_status = no_history} → 200 with an empty list
 * ({@code docs/fsp_integration.md §6}). The JWT {@code sub} is resolved to the
 * logical candidate id before the read ({@code docs/fsp_integration.md §2}).
 */
@RestController
@RequestMapping("/candidate/fsp/achievements")
public class FspAchievementsController {

    private final FspQueryService query;
    private final CandidateProfileClient identities;

    public FspAchievementsController(FspQueryService query, CandidateProfileClient identities) {
        this.query = query;
        this.identities = identities;
    }

    @GetMapping
    public AchievementsResponse get(HttpServletRequest request) {
        long candidateId = identities.resolveProfileId(CurrentUser.id(request));
        FspLink link = query.requireLinked(candidateId);
        return AchievementsResponse.from(link, query.achievements(link.id()));
    }
}
