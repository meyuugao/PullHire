package me.yuugao.pullhire.candidate.web;

import me.yuugao.pullhire.candidate.application.CardProjectorService;
import me.yuugao.pullhire.candidate.error.ApiException;
import me.yuugao.pullhire.candidate.web.dto.CardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /internal/candidates/{id}/card} ({@code docs/api.md §3.2}): the
 * privacy-filtered public card for other services. Network-trust only (the JWT
 * filter exempts {@code /internal/} and the gateway does not route it).
 */
@RestController
public class InternalCardController {

    private final CardProjectorService cards;

    public InternalCardController(CardProjectorService cards) {
        this.cards = cards;
    }

    @GetMapping("/internal/candidates/{id}/card")
    public CardResponse card(@PathVariable("id") long id) {
        return cards.current(id)
                .map(CardResponse::from)
                .orElseThrow(() -> ApiException.notFound("Карточка кандидата не найдена"));
    }
}
