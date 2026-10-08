package me.yuugao.pullhire.fsp.application;

import java.util.List;
import java.util.Optional;

import me.yuugao.pullhire.fsp.domain.AchievementCache;
import me.yuugao.pullhire.fsp.domain.FspLink;
import me.yuugao.pullhire.fsp.domain.LinkStatus;
import me.yuugao.pullhire.fsp.error.ApiException;
import me.yuugao.pullhire.fsp.repo.AchievementCacheRepository;
import me.yuugao.pullhire.fsp.repo.FspLinkRepository;
import org.springframework.stereotype.Service;

/**
 * Read side of the FSP slice ({@code docs/api.md §3.3}): the owner endpoint
 * {@code GET /candidate/fsp/achievements} and the network-trusted
 * {@code GET /internal/fsp/{candidateId}}.
 *
 * <p>An unlinked candidate has no link, so the owner read is a 404. When a link
 * exists but {@code history_status = no_history}, the read succeeds with an empty
 * item list and the explicit {@code no_history} status — distinct from "data
 * unavailable" ({@code docs/fsp_integration.md §5/§6}).
 */
@Service
public class FspQueryService {

    private final FspLinkRepository links;
    private final AchievementCacheRepository achievements;

    public FspQueryService(FspLinkRepository links, AchievementCacheRepository achievements) {
        this.links = links;
        this.achievements = achievements;
    }

    /** The candidate's active link, or 404 when unlinked/absent. */
    public FspLink requireLinked(long candidateId) {
        return findLinked(candidateId)
                .orElseThrow(() -> ApiException.notFound("ФСП ID не связан"));
    }

    /** The candidate's active link, empty when unlinked/absent. */
    public Optional<FspLink> findLinked(long candidateId) {
        return links.findByCandidateId(candidateId)
                .filter(link -> link.linkStatus() != LinkStatus.UNLINKED);
    }

    /** Cached achievements of an active link, oldest first. */
    public List<AchievementCache> achievements(long fspLinkId) {
        return achievements.listByLinkId(fspLinkId);
    }
}
