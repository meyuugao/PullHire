package me.yuugao.pullhire.assessment.domain.scoring;

/**
 * Pure 1PL (Rasch) IRT-lite scorer for adaptive grade testing
 * ({@code docs/testing_model.md §5.1, §6.1}).
 *
 * <p>The correct-response probability is the logistic function of the
 * difference between the candidate level {@code θ} and the item difficulty
 * {@code b}:
 *
 * <pre>p(correct) = 1 / (1 + exp(-(θ − b)))</pre>
 *
 * <p>After each answered item, {@code θ} is moved by a simplified Bayesian
 * step: the Gaussian prior {@code N(θ, SE²)} is combined with the single-item
 * Bernoulli likelihood, whose observed Fisher information is
 * {@code p · (1 − p)}. The posterior mean and standard error are therefore
 *
 * <pre>
 * SE'² = 1 / (1/SE² + p·(1 − p))
 * θ'  = θ + SE'² · (u − p),   u ∈ {0, 1}
 * </pre>
 *
 * <p>The estimate carries both {@code θ} and {@code SE} (§6.1); a separate
 * percent-correct metric is deliberately never produced (§6.1, §9.4).
 *
 * <p>Pure domain: no framework, JDBC or JSON dependencies. All state is
 * primitive; the caller converts {@code Item.difficulty} (BigDecimal) to a
 * {@code double} at the boundary, so this class never depends on the
 * persistence {@code Item} type. Numeric thresholds (the SE stop threshold,
 * the per-grade start θ) are supplied by the caller from configuration and are
 * not hardcoded here.
 */
public final class IrtScorer {

    private IrtScorer() {
    }

    /**
     * A candidate-ability estimate: the level {@code θ} and its standard error
     * {@code SE} ({@code docs/testing_model.md §6.1}).
     */
    public record Estimate(double theta, double standardError) {

        public Estimate {
            if (!Double.isFinite(theta)) {
                throw new IllegalArgumentException("theta must be finite");
            }
            if (!Double.isFinite(standardError) || standardError <= 0.0) {
                throw new IllegalArgumentException("standard error must be positive and finite");
            }
        }
    }

    /**
     * Probability of a correct response under the 1PL model
     * ({@code docs/testing_model.md §5.1}):
     * {@code p = 1 / (1 + exp(-(θ − b)))}. Always lies in the open interval
     * {@code (0, 1)} and equals {@code 0.5} exactly when {@code θ == b}.
     *
     * @param theta      candidate level θ
     * @param difficulty item difficulty b
     */
    public static double probabilityCorrect(double theta, double difficulty) {
        return 1.0 / (1.0 + Math.exp(-(theta - difficulty)));
    }

    /**
     * Start estimate before the first item: the per-grade start θ and its
     * initial standard error, both supplied by the caller (configuration).
     *
     * @param startTheta         initial θ ({@code AssessmentProperties.startThetaByGrade})
     * @param startStandardError initial SE
     */
    public static Estimate initial(double startTheta, double startStandardError) {
        return new Estimate(startTheta, startStandardError);
    }

    /**
     * One simplified Bayesian update step after an answered item
     * ({@code docs/testing_model.md §5.1}).
     *
     * <p>A correct response raises θ and an incorrect one lowers it; each step
     * can only add information, so {@code SE} never increases. For extreme
     * {@code b} the sigmoid saturates, the likelihood information tends to
     * zero, and both θ and SE stay bounded.
     *
     * @param prior      estimate before this item
     * @param difficulty item difficulty b (converted from {@code Item.difficulty})
     * @param correct    whether the candidate answered correctly
     * @return the posterior estimate after the item
     */
    public static Estimate update(Estimate prior, double difficulty, boolean correct) {
        double p = probabilityCorrect(prior.theta(), difficulty);
        double likelihoodInformation = p * (1.0 - p);
        double priorPrecision = 1.0 / (prior.standardError() * prior.standardError());
        double posteriorPrecision = priorPrecision + likelihoodInformation;
        double posteriorVariance = 1.0 / posteriorPrecision;
        double observed = correct ? 1.0 : 0.0;
        double posteriorTheta = prior.theta() + posteriorVariance * (observed - p);
        return new Estimate(posteriorTheta, Math.sqrt(posteriorVariance));
    }
}
