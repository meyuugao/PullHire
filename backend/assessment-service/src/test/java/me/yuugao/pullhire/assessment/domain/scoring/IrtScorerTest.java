package me.yuugao.pullhire.assessment.domain.scoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Pure property/behaviour checks of the 1PL IRT-lite scorer
 * ({@code docs/testing_model.md §5.1, §6.1}). No Spring context.
 */
class IrtScorerTest {

    @Test
    void probabilityIsInOpenUnitIntervalAcrossGrid() {
        for (double theta = -6.0; theta <= 6.0; theta += 0.5) {
            for (double b = -6.0; b <= 6.0; b += 0.5) {
                double p = IrtScorer.probabilityCorrect(theta, b);
                assertThat(p).isGreaterThan(0.0).isLessThan(1.0);
            }
        }
    }

    @Test
    void probabilityEqualsHalfWhenThetaEqualsDifficulty() {
        for (double b = -4.0; b <= 4.0; b += 1.0) {
            assertThat(IrtScorer.probabilityCorrect(b, b)).isCloseTo(0.5, within(1e-12));
        }
    }

    @Test
    void probabilityIsMonotonicIncreasingInTheta() {
        double previous = -1.0;
        for (double theta = -5.0; theta <= 5.0; theta += 0.25) {
            double p = IrtScorer.probabilityCorrect(theta, 0.0);
            assertThat(p).isGreaterThan(previous);
            previous = p;
        }
    }

    @Test
    void probabilityIsSmallBelowDifficultyAndLargeAboveIt() {
        assertThat(IrtScorer.probabilityCorrect(-20.0, 0.0)).isLessThan(1e-8);
        assertThat(IrtScorer.probabilityCorrect(20.0, 0.0)).isGreaterThan(1.0 - 1e-8);
    }

    @Test
    void thetaMovesUpAfterCorrectAnswer() {
        IrtScorer.Estimate prior = IrtScorer.initial(0.0, 1.0);
        IrtScorer.Estimate posterior = IrtScorer.update(prior, 0.0, true);
        assertThat(posterior.theta()).isGreaterThan(prior.theta());
    }

    @Test
    void thetaMovesDownAfterIncorrectAnswer() {
        IrtScorer.Estimate prior = IrtScorer.initial(0.0, 1.0);
        IrtScorer.Estimate posterior = IrtScorer.update(prior, 0.0, false);
        assertThat(posterior.theta()).isLessThan(prior.theta());
    }

    @Test
    void thetaMovesInExpectedDirectionAcrossGrid() {
        for (double b = -2.0; b <= 2.0; b += 0.5) {
            for (double theta = -2.0; theta <= 2.0; theta += 0.5) {
                IrtScorer.Estimate prior = IrtScorer.initial(theta, 1.0);
                assertThat(IrtScorer.update(prior, b, true).theta()).isGreaterThan(theta);
                assertThat(IrtScorer.update(prior, b, false).theta()).isLessThan(theta);
            }
        }
    }

    @Test
    void standardErrorDecreasesAsItemCountGrows() {
        IrtScorer.Estimate estimate = IrtScorer.initial(0.0, 1.0);
        double previousSe = estimate.standardError();
        for (int answered = 0; answered < 30; answered++) {
            estimate = IrtScorer.update(estimate, 0.0, answered % 2 == 0);
            assertThat(estimate.standardError()).isLessThan(previousSe);
            previousSe = estimate.standardError();
        }
    }

    @Test
    void resultsStayBoundedForExtremeDifficulty() {
        for (double b : new double[] {-1000.0, -100.0, 100.0, 1000.0}) {
            for (boolean correct : new boolean[] {true, false}) {
                IrtScorer.Estimate posterior = IrtScorer.update(IrtScorer.initial(0.0, 1.0), b, correct);
                assertThat(posterior.theta()).isFinite().isBetween(-10.0, 10.0);
                assertThat(posterior.standardError()).isFinite().isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
            }
        }
    }

    @Test
    void updateReturnsThetaAndStandardError() {
        IrtScorer.Estimate posterior = IrtScorer.update(IrtScorer.initial(-1.0, 2.0), 0.5, true);
        assertThat(posterior.theta()).isFinite();
        assertThat(posterior.standardError()).isFinite().isGreaterThan(0.0);
    }

    @Test
    void estimateRejectsInvalidStandardError() {
        assertThatThrownBy(() -> IrtScorer.initial(0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standard error");
        assertThatThrownBy(() -> IrtScorer.initial(0.0, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
