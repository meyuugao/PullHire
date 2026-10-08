package me.yuugao.pullhire.assessment.domain;

import java.time.Instant;

/**
 * One {@code assessment.attempts} row ({@code docs/data_model.md §6.1}): an item
 * served within a session plus the candidate's answer and proctoring flags.
 *
 * <p>{@code variantSeed} is the per-item parameter seed under the session's
 * {@code form_seed}; the {@code UNIQUE (session_id, item_order)} and
 * {@code UNIQUE (session_id, item_id)} constraints forbid re-serving the same
 * position or item inside a session ({@code docs/testing_model.md §3.4}).
 * {@code answer} and {@code proctoringFlags} are the raw {@code jsonb} text.
 */
public record Attempt(
        long id,
        long sessionId,
        long itemId,
        int itemOrder,
        Long variantSeed,
        String answer,
        Boolean isCorrect,
        Integer responseMs,
        String proctoringFlags,
        Instant servedAt) {
}
