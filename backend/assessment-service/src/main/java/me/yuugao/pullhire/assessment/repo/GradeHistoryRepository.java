package me.yuugao.pullhire.assessment.repo;

import me.yuugao.pullhire.assessment.domain.GradeDirection;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Raw JdbcTemplate access to {@code assessment.grade_history}
 * ({@code docs/data_model.md §6.1}, {@code docs/testing_model.md §7.1}).
 *
 * <p>The table is <strong>append-only</strong>: it has no UNIQUE constraint and
 * this repository deliberately exposes no update or delete method. Rows record
 * grade/category changes for audit (§13); the candidate's history is read
 * newest-last via the btree index {@code (candidate_id, created_at)}.
 *
 * <p>{@code candidate_id} is a logical bigint reference to
 * {@code candidate.profiles.id}; {@code session_id} references
 * {@code assessment.test_sessions} and may be null. No JPA, no cross-schema FK.
 */
@Repository
public class GradeHistoryRepository {

    private static final String COLUMNS =
            "id, candidate_id, grade, specialization_code, direction, session_id, "
                    + "category_code, reason, created_at";

    private static final RowMapper<GradeHistoryEntry> MAPPER = (rs, rowNum) -> new GradeHistoryEntry(
            rs.getLong("id"),
            rs.getLong("candidate_id"),
            rs.getString("grade"),
            rs.getString("specialization_code"),
            GradeDirection.fromWire(rs.getString("direction")),
            rs.getObject("session_id", Long.class),
            rs.getString("category_code"),
            rs.getString("reason"),
            JdbcRows.instant(rs, "created_at"));

    private static final String INSERT = """
            INSERT INTO grade_history (candidate_id, grade, specialization_code, direction,
                    session_id, category_code, reason, created_at)
            VALUES (:candidateId, :grade, :specializationCode, :direction,
                    :sessionId, :categoryCode, :reason, COALESCE(CAST(:createdAt AS timestamptz), now()))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public GradeHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Appends one immutable history row and returns its generated id. A
     * {@code null} {@code createdAt} falls back to the DB default {@code now()}.
     * There is no update/delete counterpart by design (append-only).
     */
    public long append(long candidateId, String grade, String specializationCode,
                       GradeDirection direction, Long sessionId, String categoryCode,
                       String reason, Instant createdAt) {
        Long id = jdbc.queryForObject(INSERT + System.lineSeparator() + "RETURNING id",
                new MapSqlParameterSource()
                        .addValue("candidateId", candidateId)
                        .addValue("grade", grade)
                        .addValue("specializationCode", specializationCode)
                        .addValue("direction", direction == null ? null : direction.wire())
                        .addValue("sessionId", sessionId)
                        .addValue("categoryCode", categoryCode)
                        .addValue("reason", reason)
                        .addValue("createdAt", JdbcRows.utc(createdAt)),
                Long.class);
        return id == null ? -1L : id;
    }

    /**
     * Full change history of one candidate, oldest first by {@code created_at},
     * driven by the btree index {@code (candidate_id, created_at)}.
     */
    public List<GradeHistoryEntry> listByCandidate(long candidateId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM grade_history WHERE candidate_id = :candidateId "
                        + "ORDER BY created_at",
                new MapSqlParameterSource("candidateId", candidateId),
                MAPPER);
    }

    /**
     * One {@code assessment.grade_history} row. Kept nested in the repository
     * because S5.7 scopes the domain package to {@link GradeDirection} only; the
     * full grade-change model lands with the grade-change service.
     */
    public record GradeHistoryEntry(
            long id,
            long candidateId,
            String grade,
            String specializationCode,
            GradeDirection direction,
            Long sessionId,
            String categoryCode,
            String reason,
            Instant createdAt) {
    }
}
