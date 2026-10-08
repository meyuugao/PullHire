package me.yuugao.pullhire.selection.repo;

import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;

/**
 * JdbcTemplate access to {@code selection.candidate_cards}
 * ({@code docs/data_model.md §7.3}). Search projection per candidate, no PII.
 *
 * <p>{@code fsp_achievements_count} is mirrored from {@code fsp.achievements_updated}
 * (ST-7.10) and is never touched by the {@code candidate.card_updated} upsert.
 */
@Repository
public class CandidateCardRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CandidateCardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts or updates the search-projection card for a candidate.
     *
     * @param stack already filtered (no null entries); may be empty but not null
     */
    public void upsert(long candidateId, String displayName, String specializationCode,
                       String grade, String[] stack, boolean fspVerified) {
        jdbc.update("""
                INSERT INTO candidate_cards (candidate_id, display_name, specialization_code,
                        grade, stack, fsp_verified)
                VALUES (:candidateId, :displayName, :specializationCode,
                        :grade, :stack, :fspVerified)
                ON CONFLICT (candidate_id) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    specialization_code = EXCLUDED.specialization_code,
                    grade = EXCLUDED.grade,
                    stack = EXCLUDED.stack,
                    fsp_verified = EXCLUDED.fsp_verified,
                    updated_at = now()
                """,
                new MapSqlParameterSource()
                        .addValue("candidateId", candidateId)
                        .addValue("displayName", displayName)
                        .addValue("specializationCode", specializationCode)
                        .addValue("grade", grade)
                        .addValue("stack", textArray(stack))
                        .addValue("fspVerified", fspVerified));
    }

    /**
     * Binds strings to a PostgreSQL {@code text[]} column by creating the SQL
     * array on the live connection (mirrors assessment
     * {@code TestSessionRepository.textArray}).
     */
    private static SqlTypeValue textArray(String[] values) {
        if (values == null) {
            return null;
        }
        return (ps, paramIndex, sqlType, typeName) -> {
            Array array = ps.getConnection().createArrayOf("text", values);
            if (sqlType == SqlTypeValue.TYPE_UNKNOWN) {
                ps.setObject(paramIndex, array);
            } else {
                ps.setObject(paramIndex, array, sqlType);
            }
        };
    }

    /** Removes the card for a hidden candidate ({@code visible_to_employers=false}). */
    public void deleteByCandidateId(long candidateId) {
        jdbc.update("DELETE FROM candidate_cards WHERE candidate_id = :candidateId",
                new MapSqlParameterSource("candidateId", candidateId));
    }
}
