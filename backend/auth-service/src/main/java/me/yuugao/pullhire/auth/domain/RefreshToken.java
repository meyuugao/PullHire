package me.yuugao.pullhire.auth.domain;

import java.time.Instant;

/** Row of {@code auth.refresh_tokens} ({@code docs/data_model.md §3.3}). */
public record RefreshToken(
        long id,
        long userId,
        String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt) {
}
