package me.yuugao.pullhire.employer.domain;

import java.time.Instant;

/**
 * Row of {@code employer.companies} ({@code docs/data_model.md §8.1}).
 * {@code userId} is a logical reference to {@code auth.users.id} (no FK).
 */
public record Company(
        long id,
        long userId,
        String name,
        String description,
        String industry,
        String contactEmail,
        String contactPhone,
        String website,
        Instant createdAt,
        Instant updatedAt) {
}
