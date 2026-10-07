package com.pullhire.candidate.web.dto;

import com.pullhire.candidate.domain.PublicCard;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * {@code GET /internal/candidates/{id}/card} response ({@code docs/api.md §3.2}) -
 * the privacy-filtered snapshot other services may read. Carries no PII.
 */
public record CardResponse(
        String candidateId,
        String displayName,
        String headline,
        String industryCode,
        String specializationCode,
        String grade,
        List<String> stack,
        boolean fspVerified,
        boolean contactReleased) {

    public static CardResponse from(PublicCard card) {
        return new CardResponse(
                Long.toString(card.profileId()),
                card.displayName(),
                card.headline(),
                card.industryCode(),
                card.specializationCode(),
                card.grade(),
                card.stack() == null ? List.of() : Arrays.stream(card.stack()).filter(Objects::nonNull).toList(),
                card.fspVerified(),
                card.contactReleased());
    }
}
