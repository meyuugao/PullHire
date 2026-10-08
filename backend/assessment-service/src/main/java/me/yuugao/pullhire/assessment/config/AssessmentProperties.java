package me.yuugao.pullhire.assessment.config;

import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * assessment-service grade-test parameters bound from {@code app.assessment.*}:
 * the adaptive stop rule ({@code docs/testing_model.md §5.2}), the cut-score
 * grade bands ({@code §6.2}), grade-change cooldown ({@code §7.3}) and item
 * calibration / exposure caps ({@code docs/data_model.md §6.1}).
 *
 * <p>Cut-scores, the SE threshold, the min/max item counts, the per-grade start
 * θ and the exposure default are <strong>not fixed by the docs</strong>: their
 * values are deferred per OQ-1 (see {@code TODO.md}). The defaults below are
 * illustrative placeholders only; they exist so that no numeric literal is
 * hardcoded outside this configuration class, and every value is overridable
 * via {@code app.assessment.*}. Cooldown is a decided value: 1 month
 * ({@code docs/assumptions.md §2}, Q-3).
 */
@Component
@ConfigurationProperties(prefix = "app.assessment")
public class AssessmentProperties {

    /** Bayesian stop threshold on θ standard error (illustrative, OQ-1). */
    private double seThreshold = 0.3;

    /** {@code z} multiplier for the θ confidence interval in the §6.3 band-boundary check; 1.96 ≈ 95% (illustrative, OQ-1). */
    private double ciConfidence = 1.96;

    /** Minimum served items before the SE stop rule may fire (illustrative, OQ-1). */
    private int minItems = 10;

    /** Hard upper bound on served items (illustrative, OQ-1). */
    private int maxItems = 30;

    /** Default per-item exposure cap ({@code item_bank.exposure_limit}; illustrative, OQ-1). */
    private int exposureDefault = 100;

    /** Grade-change cooldown; decided 1 month (Q-3, {@code docs/assumptions.md §2}). */
    private Period cooldownDuration = Period.ofMonths(1);

    /** Explicit valid grade codes (decided list, NOT derived from cut-scores keys). */
    private List<String> grades = new ArrayList<>(List.of("intern", "junior", "middle", "senior", "lead"));

    /** Illustrative lower θ bound per grade (OQ-1). */
    private final Map<String, Double> cutScores = defaultCutScores();

    /** Illustrative routing start θ per declared grade (OQ-1). */
    private final Map<String, Double> startThetaByGrade = defaultStartThetaByGrade();

    public double getSeThreshold() {
        return seThreshold;
    }

    public void setSeThreshold(double seThreshold) {
        this.seThreshold = seThreshold;
    }

    public double getCiConfidence() {
        return ciConfidence;
    }

    public void setCiConfidence(double ciConfidence) {
        this.ciConfidence = ciConfidence;
    }

    public int getMinItems() {
        return minItems;
    }

    public void setMinItems(int minItems) {
        this.minItems = minItems;
    }

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public int getExposureDefault() {
        return exposureDefault;
    }

    public void setExposureDefault(int exposureDefault) {
        this.exposureDefault = exposureDefault;
    }

    public Period getCooldownDuration() {
        return cooldownDuration;
    }

    public void setCooldownDuration(Period cooldownDuration) {
        this.cooldownDuration = cooldownDuration;
    }

    public Map<String, Double> getCutScores() {
        return cutScores;
    }

    public List<String> getGrades() {
        return grades;
    }

    public void setGrades(List<String> grades) {
        this.grades = grades;
    }

    public Map<String, Double> getStartThetaByGrade() {
        return startThetaByGrade;
    }

    private static Map<String, Double> defaultCutScores() {
        Map<String, Double> defaults = new LinkedHashMap<>();
        defaults.put("intern", -1.5);
        defaults.put("junior", -1.0);
        defaults.put("middle", -0.5);
        defaults.put("senior", 1.5);
        defaults.put("lead", 2.5);
        return defaults;
    }

    private static Map<String, Double> defaultStartThetaByGrade() {
        Map<String, Double> defaults = new LinkedHashMap<>();
        defaults.put("intern", -1.0);
        defaults.put("junior", -0.5);
        defaults.put("middle", 0.0);
        defaults.put("senior", 0.5);
        defaults.put("lead", 1.0);
        return defaults;
    }
}
