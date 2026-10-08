package me.yuugao.pullhire.selection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * selection-service ranking weights bound from {@code app.selection.rank.*}:
 * the ranking inputs are &theta; + &#1060;&#1057;&#1055; achievements only
 * ({@code docs/selection.md §3}).
 *
 * <p>Formula weights are <strong>not fixed by the docs</strong>: their values
 * are deferred per OQ-1 (see {@code TODO.md}). The defaults below are
 * illustrative placeholders only; they exist so that no numeric literal is
 * hardcoded outside this configuration class, and every value is overridable
 * via {@code app.selection.rank.*} / env without code edits. Changing weights
 * in config changes ranking output: ranking logic must read this bean.
 */
@Component
@ConfigurationProperties(prefix = "app.selection")
public class SelectionProperties {

    private final Rank rank = new Rank();

    public Rank getRank() {
        return rank;
    }

    /**
     * Ranking-formula weights (illustrative defaults, OQ-1).
     */
    public static class Rank {

        /** Weight of &theta; in the ranking score (illustrative, OQ-1). */
        private double thetaWeight = 1.0;

        /** Weight of &#1060;&#1057;&#1055; achievements in the ranking score (illustrative, OQ-1). */
        private double fspAchievementsWeight = 0.5;

        /** Bonus for verified &#1060;&#1057;&#1055; achievements (illustrative, OQ-1). */
        private double fspVerifiedBonus = 0.25;

        /** Penalty applied to unconfirmed-grade candidates so they rank below confirmed ones (illustrative, OQ-1). */
        private double unconfirmedGradePenalty = 100.0;

        /** Number of top candidates returned by ranking (illustrative, OQ-1). */
        private int topN = 20;

        public double getThetaWeight() {
            return thetaWeight;
        }

        public void setThetaWeight(double thetaWeight) {
            if (thetaWeight < 0) {
                throw new IllegalArgumentException("rank.theta-weight must be >= 0");
            }
            this.thetaWeight = thetaWeight;
        }

        public double getFspAchievementsWeight() {
            return fspAchievementsWeight;
        }

        public void setFspAchievementsWeight(double fspAchievementsWeight) {
            if (fspAchievementsWeight < 0) {
                throw new IllegalArgumentException("rank.fsp-achievements-weight must be >= 0");
            }
            this.fspAchievementsWeight = fspAchievementsWeight;
        }

        public double getFspVerifiedBonus() {
            return fspVerifiedBonus;
        }

        public void setFspVerifiedBonus(double fspVerifiedBonus) {
            this.fspVerifiedBonus = fspVerifiedBonus;
        }

        public double getUnconfirmedGradePenalty() {
            return unconfirmedGradePenalty;
        }

        public void setUnconfirmedGradePenalty(double unconfirmedGradePenalty) {
            this.unconfirmedGradePenalty = unconfirmedGradePenalty;
        }

        public int getTopN() {
            return topN;
        }

        public void setTopN(int topN) {
            if (topN < 1) {
                throw new IllegalArgumentException("rank.top-n must be >= 1");
            }
            this.topN = topN;
        }
    }
}
