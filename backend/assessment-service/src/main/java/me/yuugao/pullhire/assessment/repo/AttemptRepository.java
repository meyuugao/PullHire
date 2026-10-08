package me.yuugao.pullhire.assessment.repo;

import me.yuugao.pullhire.assessment.domain.Attempt;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Raw JdbcTemplate access to {@code assessment.attempts}
 * ({@code docs/data_model.md §6.1}): serving an item into a session, recording the
 * answer / correctness / response time / proctoring flags, listing a session's
 * attempts in serving order, and the prior-served-variant ledger used to avoid
 * re-serving a parametric variant ({@code docs/testing_model.md §3.4}).
 *
 * <p>The UNIQUE constraints {@code (session_id, item_order)} and
 * {@code (session_id, item_id)} are enforced by the database; inserts that
 * violate them surface as {@code DataIntegrityViolationException}. No dedicated
 * ledger table exists, so prior-served variants are read by joining
 * {@code attempts ⋈ test_sessions ⋈ item_bank} inside the assessment schema.
 */
@Repository
public class AttemptRepository {

    private static final String COLUMNS =
            "id, session_id, item_id, item_order, variant_seed, answer::text AS answer, "
                    + "is_correct, response_ms, proctoring_flags::text AS proctoring_flags, served_at";

    private static final RowMapper<Attempt> MAPPER = (rs, rowNum) -> new Attempt(
            rs.getLong("id"),
            rs.getLong("session_id"),
            rs.getLong("item_id"),
            rs.getInt("item_order"),
            rs.getObject("variant_seed", Long.class),
            JdbcRows.jsonb(rs, "answer"),
            rs.getObject("is_correct", Boolean.class),
            rs.getObject("response_ms", Integer.class),
            JdbcRows.jsonb(rs, "proctoring_flags"),
            JdbcRows.instant(rs, "served_at"));

    private static final String INSERT = """
            INSERT INTO attempts (session_id, item_id, item_order, variant_seed, answer, is_correct,
                    response_ms, proctoring_flags, served_at)
            VALUES (:sessionId, :itemId, :itemOrder, :variantSeed, CAST(:answer AS jsonb), :isCorrect,
                    :responseMs, CAST(:proctoringFlags AS jsonb),
                    COALESCE(CAST(:servedAt AS timestamptz), now()))
            """;

    private static final String UPDATE_ANSWER = """
            UPDATE attempts
            SET answer = CAST(:answer AS jsonb), is_correct = :isCorrect, response_ms = :responseMs,
                proctoring_flags = CAST(:proctoringFlags AS jsonb)
            WHERE id = :attemptId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AttemptRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Serves one item into a session and returns the generated attempt id. A
     * {@code null} {@code servedAt} falls back to the DB default {@code now()};
     * {@code id} is ignored on insert. Duplicate {@code item_order} or
     * {@code item_id} within the session is rejected by the UNIQUE constraints.
     */
    public long insert(Attempt attempt) {
        Long id = jdbc.queryForObject(INSERT + System.lineSeparator() + "RETURNING id",
                new MapSqlParameterSource()
                        .addValue("sessionId", attempt.sessionId())
                        .addValue("itemId", attempt.itemId())
                        .addValue("itemOrder", attempt.itemOrder())
                        .addValue("variantSeed", attempt.variantSeed())
                        .addValue("answer", attempt.answer())
                        .addValue("isCorrect", attempt.isCorrect())
                        .addValue("responseMs", attempt.responseMs())
                        .addValue("proctoringFlags", attempt.proctoringFlags())
                        .addValue("servedAt", JdbcRows.utc(attempt.servedAt())),
                Long.class);
        return id == null ? -1L : id;
    }

    private static final String INSERT_IGNORE_CONFLICT = """
            INSERT INTO attempts (session_id, item_id, item_order, variant_seed, answer, is_correct,
                    response_ms, proctoring_flags, served_at)
            VALUES (:sessionId, :itemId, :itemOrder, :variantSeed, CAST(:answer AS jsonb), :isCorrect,
                    :responseMs, CAST(:proctoringFlags AS jsonb),
                    COALESCE(CAST(:servedAt AS timestamptz), now()))
            ON CONFLICT DO NOTHING
            RETURNING id
            """;

    /**
     * Conflict-safe serve: {@code INSERT ... ON CONFLICT DO NOTHING} covering
     * both UNIQUE arms {@code (session_id, item_order)} and
     * {@code (session_id, item_id)}. Never throws on a concurrent duplicate — the
     * statement itself does not abort the surrounding transaction (unlike a
     * plain insert whose {@code DuplicateKeyException} poisons PostgreSQL
     * transactions), so the caller can load the winner and re-serve it with 200.
     * Returns the generated id when this call won, empty when a row already won.
     */
    public Optional<Long> insertIgnoringConflict(Attempt attempt) {
        List<Long> ids = jdbc.query(INSERT_IGNORE_CONFLICT,
                new MapSqlParameterSource()
                        .addValue("sessionId", attempt.sessionId())
                        .addValue("itemId", attempt.itemId())
                        .addValue("itemOrder", attempt.itemOrder())
                        .addValue("variantSeed", attempt.variantSeed())
                        .addValue("answer", attempt.answer())
                        .addValue("isCorrect", attempt.isCorrect())
                        .addValue("responseMs", attempt.responseMs())
                        .addValue("proctoringFlags", attempt.proctoringFlags())
                        .addValue("servedAt", JdbcRows.utc(attempt.servedAt())),
                (rs, rowNum) -> rs.getLong("id"));
        return ids.stream().findFirst();
    }

    /**
     * Records the graded answer of a served attempt: {@code answer},
     * {@code is_correct}, {@code response_ms} and {@code proctoring_flags}.
     * Returns the rows changed ({@code 0} when the attempt id is unknown).
     */
    public int saveAnswer(long attemptId, String answer, Boolean isCorrect, Integer responseMs,
                          String proctoringFlags) {
        return jdbc.update(UPDATE_ANSWER, new MapSqlParameterSource()
                .addValue("attemptId", attemptId)
                .addValue("answer", answer)
                .addValue("isCorrect", isCorrect)
                .addValue("responseMs", responseMs)
                .addValue("proctoringFlags", proctoringFlags));
    }

    /** Attempts of one session in serving order ({@code item_order}). */
    public List<Attempt> findBySession(long sessionId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM attempts WHERE session_id = :sessionId "
                        + "ORDER BY item_order",
                new MapSqlParameterSource("sessionId", sessionId),
                MAPPER);
    }

    /**
     * Variant seeds already served to this candidate for a template family, read
     * by joining {@code attempts ⋈ test_sessions ⋈ item_bank}
     * ({@code docs/testing_model.md §3.4}): a candidate must not be served the
     * same parametric variant twice. Rows without a {@code variant_seed} are
     * ignored; the result is distinct and ascending so callers can test membership.
     */
    public List<Long> priorServedVariants(long candidateId, String templateFamily) {
        return jdbc.queryForList("""
                SELECT DISTINCT a.variant_seed
                FROM attempts a
                JOIN test_sessions s ON s.id = a.session_id
                JOIN item_bank i ON i.id = a.item_id
                WHERE s.candidate_id = :candidateId
                  AND i.template_family = :templateFamily
                  AND a.variant_seed IS NOT NULL
                ORDER BY a.variant_seed
                """,
                new MapSqlParameterSource()
                        .addValue("candidateId", candidateId)
                        .addValue("templateFamily", templateFamily),
                Long.class);
    }
}
