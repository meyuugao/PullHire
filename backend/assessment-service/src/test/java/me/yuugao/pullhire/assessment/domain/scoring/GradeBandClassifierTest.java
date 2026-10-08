package me.yuugao.pullhire.assessment.domain.scoring;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behaviour checks of the cut-score → grade-band classifier with
 * {@code needs_review} detection ({@code docs/testing_model.md §6.1–§6.3}).
 * Pure JUnit 5 + AssertJ, no Spring context.
 */
class GradeBandClassifierTest {

    private static final double SE_THRESHOLD = 0.3;
    private static final double CI_CONFIDENCE = 1.96;
    private static final double PRECISE_SE = 0.05;

    private static Map<String, Double> cutScores() {
        Map<String, Double> cuts = new LinkedHashMap<>();
        cuts.put("intern", -1.5);
        cuts.put("junior", -0.5);
        cuts.put("middle", 0.5);
        cuts.put("senior", 1.5);
        cuts.put("lead", 2.5);
        return cuts;
    }

    private static GradeBandClassifier.GradeBand classify(double theta, double se) {
        return GradeBandClassifier.classify(theta, se, cutScores(), SE_THRESHOLD, CI_CONFIDENCE);
    }

    @Test
    void mapsToInternBand() {
        assertThat(classify(-2.0, PRECISE_SE).grade()).isEqualTo("intern");
    }

    @Test
    void mapsToJuniorBand() {
        assertThat(classify(-0.25, PRECISE_SE).grade()).isEqualTo("junior");
    }

    @Test
    void mapsToMiddleBand() {
        assertThat(classify(0.75, PRECISE_SE).grade()).isEqualTo("middle");
    }

    @Test
    void mapsToSeniorBand() {
        assertThat(classify(2.0, PRECISE_SE).grade()).isEqualTo("senior");
    }

    @Test
    void mapsToLeadBand() {
        assertThat(classify(3.0, PRECISE_SE).grade()).isEqualTo("lead");
    }

    @Test
    void preciseInBandEstimateDoesNotNeedReview() {
        assertThat(classify(0.0, PRECISE_SE).needsReview()).isFalse();
    }

    @Test
    void confidenceIntervalCrossingBandBoundaryNeedsReview() {
        // θ=0.45, z·SE=0.196 → CI [0.254, 0.646] straddles the 0.5 junior/middle cut.
        GradeBandClassifier.GradeBand result = classify(0.45, 0.1);

        assertThat(result.grade()).isEqualTo("junior");
        assertThat(result.needsReview()).isTrue();
        assertThat(0.1).isLessThan(SE_THRESHOLD);
    }

    @Test
    void standardErrorAboveThresholdNeedsReview() {
        assertThat(classify(0.0, 0.5).needsReview()).isTrue();
    }

    @Test
    void resultExposesOnlyGradeAndNeedsReviewNoPercent() {
        String[] components = Arrays.stream(GradeBandClassifier.GradeBand.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);
        assertThat(components).containsExactly("grade", "needsReview");
        assertThat(GradeBandClassifier.GradeBand.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("percent"));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> GradeBandClassifier.classify(
                Double.NaN, PRECISE_SE, cutScores(), SE_THRESHOLD, CI_CONFIDENCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GradeBandClassifier.classify(
                0.0, 0.0, cutScores(), SE_THRESHOLD, CI_CONFIDENCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GradeBandClassifier.classify(
                0.0, PRECISE_SE, Map.of(), SE_THRESHOLD, CI_CONFIDENCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GradeBandClassifier.classify(
                0.0, PRECISE_SE, cutScores(), SE_THRESHOLD, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
