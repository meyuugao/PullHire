package me.yuugao.pullhire.assessment.domain.form;

import java.util.List;
import java.util.Optional;

import me.yuugao.pullhire.assessment.domain.Item;

/**
 * Adaptive routing rules: the stop condition and next-item selection for the
 * IRT-lite grade test ({@code docs/testing_model.md §5.1–§5.2}).
 *
 * <p><b>Stop condition</b> ({@code §5.2}). The attempt ends once the estimate
 * is precise enough — {@code SE ≤ seThreshold} — but only after at least
 * {@code minItems} items; it never ends before {@code minItems}, and it is
 * force-ended at {@code maxItems} regardless of {@code SE}:
 *
 * <pre>
 * stop(θ, SE, n) = n ≥ maxItems
 *                ∨ (n ≥ minItems ∧ SE ≤ seThreshold)
 * </pre>
 *
 * <p><b>Next item</b> ({@code §5.1}). The item whose difficulty {@code b} is
 * closest to the current level {@code θ} — minimum {@code |θ − b|} — is served
 * next. The caller passes only the items that are still available: filtering by
 * exposure cap and already-served variants is the caller's responsibility
 * ({@code §3.4, §9.1}).
 *
 * <p><b>MST fallback</b> ({@code §5.3}) is deferred: the multistage-testing
 * route falls outside the MVP and is not implemented here.
 *
 * <p>Pure domain: final class, static methods, no framework/JDBC/JSON. Every
 * numeric parameter — the SE threshold and the min/max item counts supplied by
 * {@code AssessmentProperties} — arrives as an argument; no value is hardcoded.
 */
public final class RoutingRules {

    private RoutingRules() {
    }

    /**
     * Whether the adaptive attempt should stop after {@code itemCount} served
     * items ({@code docs/testing_model.md §5.2}).
     *
     * @param theta       current ability estimate θ; the MVP stop rule itself
     *                    does not depend on θ (precision alone drives the SE
     *                    rule), it is accepted for call-site symmetry with
     *                    {@link #nextItem}
     * @param se          current standard error SE
     * @param itemCount   items served so far
     * @param seThreshold SE at or below which the attempt may stop
     *                    ({@code AssessmentProperties.seThreshold})
     * @param minItems    minimum items before the SE rule may fire
     * @param maxItems    hard upper bound that force-stops the attempt
     * @return {@code true} to stop, {@code false} to serve another item
     * @throws IllegalArgumentException if {@code minItems} is negative or
     *                                  {@code maxItems < minItems}
     */
    public static boolean shouldStop(double theta,
                                     double se,
                                     int itemCount,
                                     double seThreshold,
                                     int minItems,
                                     int maxItems) {
        if (minItems < 0) {
            throw new IllegalArgumentException("minItems must be non-negative");
        }
        if (maxItems < minItems) {
            throw new IllegalArgumentException("maxItems must be >= minItems");
        }
        // Hard upper bound wins regardless of SE.
        if (itemCount >= maxItems) {
            return true;
        }
        // Never stop before the minimum, even if SE is already below threshold.
        if (itemCount < minItems) {
            return false;
        }
        return se <= seThreshold;
    }

    /**
     * Selects the next item as the one whose difficulty is closest to the
     * current estimate — minimum {@code |θ − b|} ({@code docs/testing_model.md
     * §5.1}).
     *
     * <p>Only genuinely available items should be passed; exposure-cap and
     * already-served-variant filtering is the caller's job
     * ({@code §3.4, §9.1}). Null items and items with a null difficulty are
     * ignored. On a tie the first item in iteration order wins, so a caller
     * that supplies a deterministic list gets a deterministic choice.
     *
     * @param theta     current ability estimate θ
     * @param available caller-filtered available items (may be null or empty)
     * @return the closest item, or empty when no item is available
     */
    public static Optional<Item> nextItem(double theta, List<Item> available) {
        if (available == null || available.isEmpty()) {
            return Optional.empty();
        }
        Item best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Item item : available) {
            if (item == null || item.difficulty() == null) {
                continue;
            }
            double distance = Math.abs(theta - item.difficulty().doubleValue());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        return Optional.ofNullable(best);
    }
}
