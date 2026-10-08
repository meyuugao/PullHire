package me.yuugao.pullhire.auth.domain;

import java.time.Instant;

/** Row of {@code auth.email_tokens} ({@code docs/data_model.md §3.2}). */
public record EmailToken(
        long id,
        long userId,
        String tokenHash,
        String purpose,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt) {
}
