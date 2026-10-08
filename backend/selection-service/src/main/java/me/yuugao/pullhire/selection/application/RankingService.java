package me.yuugao.pullhire.selection.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import me.yuugao.pullhire.selection.config.SelectionProperties;
import org.springframework.stereotype.Service;

/**
 * Pure scorer/orderer of candidates within one category
 * ({@code docs/selection.md §3}, weights OQ-1 via
 * {@code SelectionProperties.Rank}, storage {@code docs/data_model.md §7.4}).
 *
 * <p>Formula:
 * {@code score = thetaWeight * coalesce(theta, 0)
 * + fspAchievementsWeight * fsp_achievements_weighted
 * + (fsp_verified ? fspVerifiedBonus : 0)
 * - (gradeConfirmed ? 0 : unconfirmedGradePenalty)}.
 * NULL {@code theta} is neutral 0 (coalesce) — no imputation, no penalty.
 * Unconfirmed-grade candidates rank below confirmed ones via the penalty;
 * the {@code gradeConfirmed} flag is passed by the caller
 * (derived {@code cards.grade ≠ categories.grade}) so this service stays pure.
 *
 * <p>Forbidden inputs ({@code ProfileFreshness}, resume-derived data, stack as
 * a position signal) are not even accepted as parameters; stack is filter-only.
 *
 * <p>Scores are internal and never serialized (θ is never disclosed);
 * ordering ties break by {@code candidate_id} ascending for determinism.
 */
@Service
public class RankingService {

    private final SelectionProperties properties;

    public RankingService(SelectionProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * One candidate's ranking inputs. No freshness / resume / stack fields —
     * deliberately absent (selection.md §3).
     */
    public record RankInput(long candidateId, Double theta,
                            boolean fspVerified, double fspAchievementsWeighted,
                            boolean gradeConfirmed) {
    }

    /**
     * Internal scoring result: {@code candidate_id} + score. Never serialized.
     */
    public record CandidateScore(long candidateId, double score) {
    }

    /** Scores a single candidate with the configured weights. */
    public double score(RankInput input) {
        SelectionProperties.Rank rank = properties.getRank();
        double theta = input.theta() == null ? 0.0 : input.theta();
        double result = rank.getThetaWeight() * theta
                + rank.getFspAchievementsWeight() * input.fspAchievementsWeighted()
                + (input.fspVerified() ? rank.getFspVerifiedBonus() : 0.0);
        if (!input.gradeConfirmed()) {
            result -= rank.getUnconfirmedGradePenalty();
        }
        return result;
    }

    /**
     * Scores, orders (confirmed first, then score descending,
     * {@code candidate_id} ascending on ties)
     * and returns the top-N list ({@code topN} from
     * {@code SelectionProperties.Rank}).
     */
    public List<CandidateScore> rank(List<RankInput> inputs) {
        int topN = properties.getRank().getTopN();
        return inputs.stream()
                .map(input -> new Ranked(input, score(input)))
                .sorted(Comparator.comparing(Ranked::confirmed).reversed()
                        .thenComparing(Comparator.comparingDouble(Ranked::score).reversed())
                        .thenComparingLong(ranked -> ranked.input().candidateId()))
                .limit(topN)
                .map(ranked -> new CandidateScore(ranked.input().candidateId(), ranked.score()))
                .toList();
    }

    private record Ranked(RankInput input, double score) {
        boolean confirmed() {
            return input.gradeConfirmed();
        }
    }
}
