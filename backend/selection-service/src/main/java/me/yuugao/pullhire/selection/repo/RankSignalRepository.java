package me.yuugao.pullhire.selection.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@code selection.rank_signals} ({@code docs/data_model.md §7.4})
 * plus the {@code candidate_cards} FSP mirror ({@code docs/data_model.md §7.3}).
 */
@Repository
public class RankSignalRepository {

    private final JdbcTemplate jdbc;

    public RankSignalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Current {@code category_code} of the candidate
     * ({@code selection.candidate_categories}, {@code docs/data_model.md §7.2}).
     */
    public Optional<String> findCategoryCode(long candidateId) {
        return jdbc.query(
                "SELECT category_code FROM candidate_categories WHERE candidate_id = ?",
                (rs, i) -> rs.getString(1), candidateId).stream().findFirst();
    }

    /**
     * Upserts the FSP signal row for {@code (candidate_id, category_code)}.
     * The {@code theta} column is never modified here — ranking strength stays
     * owned by the {@code assessment.category_assigned} consumer
     * ({@code docs/selection.md §3}).
     */
    public void upsertFspSignals(long candidateId, String categoryCode,
                                 boolean fspVerified, int achievementsCount,
                                 double achievementsWeighted) {
        jdbc.update(
                "INSERT INTO rank_signals"
                        + " (candidate_id, category_code, fsp_verified,"
                        + " fsp_achievements_count, fsp_achievements_weighted, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, now())"
                        + " ON CONFLICT (candidate_id, category_code) DO UPDATE SET"
                        + " fsp_verified = EXCLUDED.fsp_verified,"
                        + " fsp_achievements_count = EXCLUDED.fsp_achievements_count,"
                        + " fsp_achievements_weighted = EXCLUDED.fsp_achievements_weighted,"
                        + " updated_at = now()",
                candidateId, categoryCode, fspVerified, achievementsCount, achievementsWeighted);
    }

    /**
     * Ranking inputs of one category ({@code docs/selection.md §3},
     * {@code docs/data_model.md §7.4}): {@code theta} (nullable — NULL means
     * no confirmed test result yet) plus FSP signals. Read-only for ranking;
     * write methods above stay owned by the batch-2 consumers.
     */
    public record RankSignalRow(long candidateId, Double theta,
                                boolean fspVerified, double fspAchievementsWeighted) {
    }

    /**
     * Reads all signal rows of a category for ranking. No ordering here —
     * ordering is {@code application.RankingService} business.
     */
    public List<RankSignalRow> findByCategoryCode(String categoryCode) {
        return jdbc.query(
                "SELECT candidate_id, theta, fsp_verified, fsp_achievements_weighted"
                        + " FROM rank_signals WHERE category_code = ?",
                (rs, i) -> {
                    Object thetaObj = rs.getObject("theta");
                    Double theta = thetaObj == null ? null : ((Number) thetaObj).doubleValue();
                    Object weightedObj = rs.getObject("fsp_achievements_weighted");
                    double weighted = weightedObj == null ? 0.0 : ((Number) weightedObj).doubleValue();
                    return new RankSignalRow(rs.getLong("candidate_id"), theta,
                            rs.getBoolean("fsp_verified"), weighted);
                },
                categoryCode);
    }

    /**
     * Mirrors {@code fsp_verified} + {@code fsp_achievements_count} into
     * {@code selection.candidate_cards} so the card column is not stuck at its
     * default; no-op when the card row does not exist yet.
     */
    public void mirrorCardFspFields(long candidateId, boolean fspVerified, int achievementsCount) {
        jdbc.update(
                "UPDATE candidate_cards SET fsp_verified = ?,"
                        + " fsp_achievements_count = ?, updated_at = now()"
                        + " WHERE candidate_id = ?",
                fspVerified, achievementsCount, candidateId);
    }
}
