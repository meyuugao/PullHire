package me.yuugao.pullhire.employer.domain;

import java.time.Instant;

/**
 * Row of {@code employer.needs} ({@code docs/data_model.md §8.2}).
 * {@code companyId} is the owning company; {@code stack} maps the {@code text[]} column.
 */
public record Need(
        long id,
        long companyId,
        String title,
        String description,
        String specializationCode,
        String grade,
        String[] stack,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
