package me.yuugao.pullhire.assessment.repo;

import me.yuugao.pullhire.assessment.domain.Cooldown;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Raw JdbcTemplate access to {@code assessment.cooldowns}
 * ({@code docs/data_model.md §6.1}, {@code docs/testing_model.md §7.3}): the
 * per-candidate grade-change rate limit, scoped to {@code grade_change} and keyed
 * by {@code CONSTRAINT uq_cooldowns_candidate_scope UNIQUE (candidate_id, scope)}.
 *
 * <p>{@link #upsert} writes or refreshes the single row per
 * {@code (candidate_id, scope)}; {@link #findActive} reads it. This repository
 * deliberately exposes no burn/eligibility decision: whether a successful grade
 * change sets a cooldown (and whether failure or a downgrade skips it) is a
 * service-level rule ({@code docs/assumptions.md §2}).
 *
 * <p>{@code candidate_id} is a logical bigint reference to
 * {@code candidate.profiles.id}; no JPA, no cross-schema FK.
 */
@Repository
public class CooldownRepository {

    /** The only scope allowed by the {@code cooldowns.scope} CHECK constraint. */
    public static final String SCOPE_GRADE_CHANGE = "grade_change";

    private static final String COLUMNS = "id, candidate_id, scope, available_after, set_at";

    private static final RowMapper<Cooldown> MAPPER = (rs, rowNum) -> new Cooldown(
            rs.getLong("id"),
            rs.getLong("candidate_id"),
            rs.getString("scope"),
            JdbcRows.instant(rs, "available_after"),
            JdbcRows.instant(rs, "set_at"));

    private static final String UPSERT = """
            INSERT INTO cooldowns (candidate_id, scope, available_after)
            VALUES (:candidateId, :scope, :availableAfter)
            ON CONFLICT (candidate_id, scope)
            DO UPDATE SET available_after = EXCLUDED.available_after, set_at = now()
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CooldownRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@link #upsert(long, String, Instant)} with the default {@code grade_change} scope. */
    public Cooldown upsert(long candidateId, Instant availableAfter) {
        return upsert(candidateId, SCOPE_GRADE_CHANGE, availableAfter);
    }

    /**
     * Inserts or refreshes the candidate's cooldown for {@code scope} through the
     * unique {@code (candidate_id, scope)} conflict target, returning the stored
     * row. A repeat call updates {@code available_after} and {@code set_at} in
     * place; it never creates a duplicate row.
     */
    public Cooldown upsert(long candidateId, String scope, Instant availableAfter) {
        return jdbc.queryForObject(UPSERT + System.lineSeparator() + "RETURNING " + COLUMNS,
                new MapSqlParameterSource()
                        .addValue("candidateId", candidateId)
                        .addValue("scope", scope)
                        .addValue("availableAfter", JdbcRows.utc(availableAfter)),
                MAPPER);
    }

    /**
     * The candidate's cooldown row for {@code scope}, or empty when none exists.
     * The btree index {@code idx_cooldowns_available_after} supports eligibility
     * sweeps over {@code available_after}.
     */
    public Optional<Cooldown> findActive(long candidateId, String scope) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM cooldowns "
                        + "WHERE candidate_id = :candidateId AND scope = :scope",
                new MapSqlParameterSource()
                        .addValue("candidateId", candidateId)
                        .addValue("scope", scope),
                MAPPER)
                .stream()
                .findFirst();
    }

    /** {@link #findActive(long, String)} with the default {@code grade_change} scope. */
    public Optional<Cooldown> findActive(long candidateId) {
        return findActive(candidateId, SCOPE_GRADE_CHANGE);
    }
}
