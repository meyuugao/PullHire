package me.yuugao.pullhire.assessment.domain.form;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.ItemKind;
import me.yuugao.pullhire.assessment.domain.ItemStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure behaviour checks of the adaptive stop condition and next-item selection
 * ({@code docs/testing_model.md §5.1–§5.2}). No Spring context.
 */
class RoutingRulesTest {

    private static final double SE_THRESHOLD = 0.3;
    private static final int MIN_ITEMS = 10;
    private static final int MAX_ITEMS = 30;

    @Test
    void neverStopsBeforeMinimumEvenWhenSeIsBelowThreshold() {
        for (int count = 0; count < MIN_ITEMS; count++) {
            assertThat(RoutingRules.shouldStop(0.0, 0.0, count, SE_THRESHOLD, MIN_ITEMS, MAX_ITEMS))
                    .as("count=%s", count)
                    .isFalse();
        }
    }

    @Test
    void alwaysStopsAtMaximumRegardlessOfSe() {
        assertThat(RoutingRules.shouldStop(0.0, 10.0, MAX_ITEMS, SE_THRESHOLD, MIN_ITEMS, MAX_ITEMS)).isTrue();
        assertThat(RoutingRules.shouldStop(0.0, Double.MAX_VALUE, MAX_ITEMS, SE_THRESHOLD, MIN_ITEMS, MAX_ITEMS)).isTrue();
        assertThat(RoutingRules.shouldStop(0.0, 10.0, MAX_ITEMS + 5, SE_THRESHOLD, MIN_ITEMS, MAX_ITEMS)).isTrue();
    }

    @Test
    void stopsEarlyOnlyWhenSeAtThresholdAndCountAtMinimum() {
        // Both conditions hold exactly at the min boundary.
        assertThat(RoutingRules.shouldStop(0.0, SE_THRESHOLD, MIN_ITEMS, SE_THRESHOLD, MIN_ITEMS, MAX_ITEMS)).isTrue();
        // SE above threshold -> continue even past the minimum.
        assertThat(RoutingRules.shouldStop(0.0, SE_THRESHOLD + 0.01, MIN_ITEMS, SE_THRESHOLD, MIN_ITEMS, MAX_ITEMS)).isFalse();
        // SE at threshold but below the minimum -> continue.
        assertThat(RoutingRules.shouldStop(0.0, SE_THRESHOLD, MIN_ITEMS - 1, SE_THRESHOLD, MIN_ITEMS, MAX_ITEMS)).isFalse();
    }

    @Test
    void stopsWhenBothConditionsHoldBetweenMinimumAndMaximum() {
        int count = MIN_ITEMS + 3;
        assertThat(RoutingRules.shouldStop(1.2, SE_THRESHOLD / 2.0, count, SE_THRESHOLD, MIN_ITEMS, MAX_ITEMS)).isTrue();
    }

    @Test
    void continuesWhileSeRemainsAboveThresholdAfterMinimum() {
        for (int count = MIN_ITEMS; count < MAX_ITEMS; count++) {
            assertThat(RoutingRules.shouldStop(0.0, SE_THRESHOLD + 0.001, count, SE_THRESHOLD, MIN_ITEMS, MAX_ITEMS))
                    .as("count=%s", count)
                    .isFalse();
        }
    }

    @Test
    void rejectsInconsistentBounds() {
        assertThatThrownBy(() -> RoutingRules.shouldStop(0.0, 0.0, 5, SE_THRESHOLD, -1, MAX_ITEMS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoutingRules.shouldStop(0.0, 0.0, 5, SE_THRESHOLD, MIN_ITEMS, MIN_ITEMS - 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextItemChoosesDifficultyClosestToTheta() {
        Item near = item(2, 0.4);
        List<Item> available = List.of(item(1, -2.0), near, item(3, 2.0));

        assertThat(RoutingRules.nextItem(0.5, available)).contains(near);
    }

    @Test
    void nextItemUsesAbsoluteDistanceOnBothSidesOfTheta() {
        Item below = item(1, -1.7);
        assertThat(RoutingRules.nextItem(-1.0, List.of(below, item(2, 5.0)))).contains(below);

        Item above = item(4, 1.9);
        assertThat(RoutingRules.nextItem(2.0, List.of(item(3, -5.0), above))).contains(above);
    }

    @Test
    void nextItemBreaksTiesByIterationOrder() {
        Item first = item(1, 0.0);
        assertThat(RoutingRules.nextItem(0.5, List.of(first, item(2, 1.0)))).contains(first);
    }

    @Test
    void nextItemReturnsEmptyWhenNoneAvailable() {
        assertThat(RoutingRules.nextItem(0.0, List.of())).isEmpty();
        assertThat(RoutingRules.nextItem(0.0, null)).isEmpty();
    }

    @Test
    void nextItemSkipsNullItemAndNullDifficulty() {
        Item real = item(5, 0.75);
        assertThat(RoutingRules.nextItem(0.0, Arrays.asList(null, real))).contains(real);
        assertThat(RoutingRules.nextItem(0.0, Arrays.asList(itemWithoutDifficulty(9), real))).contains(real);
    }

    private static Item item(long id, double difficulty) {
        return new Item(
                id,
                ItemKind.TEMPLATE,
                "family-" + id,
                "concept",
                "spec",
                BigDecimal.valueOf(difficulty),
                true,
                null,
                0,
                "{}",
                ItemStatus.ACTIVE,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static Item itemWithoutDifficulty(long id) {
        return new Item(
                id,
                ItemKind.TEMPLATE,
                "family-" + id,
                "concept",
                "spec",
                null,
                true,
                null,
                0,
                "{}",
                ItemStatus.ACTIVE,
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
