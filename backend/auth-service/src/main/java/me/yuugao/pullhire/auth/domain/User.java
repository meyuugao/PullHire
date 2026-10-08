package me.yuugao.pullhire.auth.domain;

import java.time.Instant;

/** Row of {@code auth.users} ({@code docs/data_model.md §3.1}). */
public record User(
        long id,
        String email,
        String passwordHash,
        Role role,
        UserStatus status,
        Instant emailVerifiedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt) {
}
