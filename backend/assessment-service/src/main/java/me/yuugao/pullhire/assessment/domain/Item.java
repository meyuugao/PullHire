package me.yuugao.pullhire.assessment.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One {@code assessment.item_bank} row ({@code docs/data_model.md §6.1}): an anchor
 * item or a template family with its calibration parameters ({@code difficulty} is
 * the IRT-lite parameter {@code b}). {@code payload} is the raw {@code jsonb} model
 * text; {@code templateFamily} is {@code null} for anchors.
 */
public record Item(
        long id,
        ItemKind itemKind,
        String templateFamily,
        String conceptCode,
        String specializationCode,
        BigDecimal difficulty,
        boolean provisional,
        Integer exposureLimit,
        int exposureCount,
        String payload,
        ItemStatus status,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * True when a non-null {@code exposure_limit} has been reached
     * ({@code exposure_limit IS NOT NULL AND exposure_count >= exposure_limit});
     * such an item must be excluded from rotation (docs/data_model.md §6.1).
     */
    public boolean exposureExhausted() {
        return exposureLimit != null && exposureCount >= exposureLimit;
    }
}
