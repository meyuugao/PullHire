package me.yuugao.pullhire.assessment.domain.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Maps a candidate-ability estimate ({@code θ}, {@code SE}) onto a grade band
 * and flags borderline attempts for manual review
 * ({@code docs/testing_model.md §6.2, §6.3}).
 *
 * <p><b>Band</b> ({@code §6.2}). The {@code θ} scale is split into grade bands
 * by ordered cut-scores: a value belongs to the highest grade whose cut-score
 * is at or below it, and values below the lowest cut-score stay in the lowest
 * grade. The result is the candidate category grade — never a percent score
 * ({@code §6.1}).
 *
 * <p><b>Needs review</b> ({@code §6.3}). An attempt is flagged when the estimate
 * is imprecise ({@code SE > seThreshold}) or when the confidence interval
 * {@code [θ − z·SE, θ + z·SE]} crosses a band boundary, i.e. its lower and
 * upper ends fall in different bands. The flag only marks the attempt for a
 * human in the loop; this class never auto-forces a grade.
 *
 * <p>Pure domain: final class, static methods, no framework/JDBC/JSON. Every
 * numeric parameter — the cut-scores, the SE threshold and the {@code z}
 * CI multiplier ({@code ciConfidence}) — arrives as an argument from
 * {@code AssessmentProperties}; no tunable value is hardcoded here.
 */
public final class GradeBandClassifier {

    private GradeBandClassifier() {
    }

    /**
     * Classification outcome: the grade band and whether the attempt is
     * borderline enough to require human review ({@code §6.3}).
     */
    public record GradeBand(String grade, boolean needsReview) {

        public GradeBand {
            if (grade == null || grade.isBlank()) {
                throw new IllegalArgumentException("grade must be non-blank");
            }
        }
    }

    /**
     * Classifies an estimate into a grade band and decides whether it needs
     * review ({@code docs/testing_model.md §6.2, §6.3}).
     *
     * @param theta          ability estimate θ
     * @param standardError  standard error SE of θ; must be positive and finite
     * @param cutScores      grade → lower θ cut-score, unsorted is fine
     * @param seThreshold    SE above which the attempt is imprecise
     * @param ciConfidence   {@code z} multiplier for the θ confidence interval
     * @return the band plus the {@code needsReview} flag; never a percent score
     * @throws IllegalArgumentException if any argument is invalid
     */
    public static GradeBand classify(double theta,
                                     double standardError,
                                     Map<String, Double> cutScores,
                                     double seThreshold,
                                     double ciConfidence) {
        if (!Double.isFinite(theta)) {
            throw new IllegalArgumentException("theta must be finite");
        }
        if (!Double.isFinite(standardError) || standardError <= 0.0) {
            throw new IllegalArgumentException("standard error must be positive and finite");
        }
        if (cutScores == null || cutScores.isEmpty()) {
            throw new IllegalArgumentException("cutScores must not be empty");
        }
        if (!Double.isFinite(seThreshold) || seThreshold < 0.0) {
            throw new IllegalArgumentException("seThreshold must be finite and non-negative");
        }
        if (!Double.isFinite(ciConfidence) || ciConfidence <= 0.0) {
            throw new IllegalArgumentException("ciConfidence must be positive and finite");
        }

        List<Map.Entry<String, Double>> ordered = orderedBands(cutScores);
        String band = bandAt(theta, ordered);
        double margin = ciConfidence * standardError;
        boolean boundaryCrossed = !bandAt(theta - margin, ordered)
                .equals(bandAt(theta + margin, ordered));
        boolean needsReview = standardError > seThreshold || boundaryCrossed;
        return new GradeBand(band, needsReview);
    }

    private static List<Map.Entry<String, Double>> orderedBands(Map<String, Double> cutScores) {
        List<Map.Entry<String, Double>> ordered = new ArrayList<>(cutScores.entrySet());
        for (Map.Entry<String, Double> entry : ordered) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("cut-score grade must be non-blank");
            }
            if (entry.getValue() == null || !Double.isFinite(entry.getValue())) {
                throw new IllegalArgumentException("cut-score value must be finite");
            }
        }
        ordered.sort(Comparator.comparingDouble(Map.Entry::getValue));
        return ordered;
    }

    private static String bandAt(double value, List<Map.Entry<String, Double>> ordered) {
        String band = ordered.get(0).getKey();
        for (Map.Entry<String, Double> entry : ordered) {
            if (value >= entry.getValue()) {
                band = entry.getKey();
            } else {
                break;
            }
        }
        return band;
    }
}
