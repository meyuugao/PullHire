package me.yuugao.pullhire.assessment.domain;

import java.time.Instant;

/**
 * One {@code assessment.cooldowns} row ({@code docs/data_model.md §6.1}): the
 * per-candidate grade-change rate limit ({@code docs/testing_model.md §7.3}).
 *
 * <p>{@code candidateId} is a logical reference to {@code candidate.profiles.id}
 * (no cross-schema FK). {@code scope} is the cooldown domain, constrained to
 * {@code grade_change}; {@code availableAfter} is the instant from which a further
 * grade change becomes eligible; {@code setAt} is when the row was last written.
 */
public record Cooldown(
        long id,
        long candidateId,
        String scope,
        Instant availableAfter,
        Instant setAt) {
}
