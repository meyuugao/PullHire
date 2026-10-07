package com.pullhire.candidate.domain;

import java.time.Instant;

/**
 * Row of {@code candidate.public_cards} ({@code docs/data_model.md §4.4}).
 * Privacy-filtered snapshot and source of {@code candidate.card_updated}; carries
 * no PII beyond the masked {@code displayName}.
 */
public record PublicCard(
        long id,
        long profileId,
        String displayName,
        String headline,
        String industryCode,
        String specializationCode,
        String grade,
        String[] stack,
        boolean fspVerified,
        boolean contactReleased,
        Instant updatedAt) {
}
