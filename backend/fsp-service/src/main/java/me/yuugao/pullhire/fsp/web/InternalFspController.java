package me.yuugao.pullhire.fsp.web;

import me.yuugao.pullhire.fsp.application.FspQueryService;
import me.yuugao.pullhire.fsp.domain.FspLink;
import me.yuugao.pullhire.fsp.error.ApiException;
import me.yuugao.pullhire.fsp.web.dto.InternalFspResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /internal/fsp/{candidateId}} ({@code docs/api.md §3.3}): internal read
 * of a link and its achievements for other services. Network-trust only (the JWT
 * filter exempts {@code /internal/} and the gateway does not route it).
 */
@RestController
public class InternalFspController {

    private final FspQueryService query;

    public InternalFspController(FspQueryService query) {
        this.query = query;
    }

    @GetMapping("/internal/fsp/{candidateId}")
    public InternalFspResponse get(@PathVariable("candidateId") long candidateId) {
        FspLink link = query.findLinked(candidateId)
                .orElseThrow(() -> ApiException.notFound("ФСП ID не связан"));
        return InternalFspResponse.from(link, query.achievements(link.id()));
    }
}
