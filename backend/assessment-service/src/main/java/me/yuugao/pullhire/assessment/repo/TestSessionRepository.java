package me.yuugao.pullhire.assessment.repo;

import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Raw JdbcTemplate access to {@code assessment.test_sessions}
 * ({@code docs/data_model.md §6.1}): session creation, loading, progress updates
 * (status / theta / theta_se / item_count / form_seed / completed_at) and the
 * candidate's session list. No JPA and no cross-schema access
 * ({@code candidate_id} is a logical bigint reference only).
 */
@Repository
public class TestSessionRepository {

    private static final String COLUMNS =
            "id, candidate_id, industry_code, specialization_code, target_grade, "
                    + "survey_answers::text AS survey_answers, declared_dimensions, form_seed, "
                    + "status, theta, theta_se, item_count, started_at, completed_at";

    private static final RowMapper<TestSession> MAPPER = (rs, rowNum) -> new TestSession(
            rs.getLong("id"),
            rs.getLong("candidate_id"),
            rs.getString("industry_code"),
            rs.getString("specialization_code"),
            rs.getString("target_grade"),
            JdbcRows.jsonb(rs, "survey_answers"),
            stringArray(rs, "declared_dimensions"),
            rs.getObject("form_seed", Long.class),
            SessionStatus.fromWire(rs.getString("status")),
            rs.getBigDecimal("theta"),
            rs.getBigDecimal("theta_se"),
            rs.getInt("item_count"),
            JdbcRows.instant(rs, "started_at"),
            JdbcRows.instant(rs, "completed_at"));

    private static final String INSERT = """
            INSERT INTO test_sessions (candidate_id, industry_code, specialization_code, target_grade,
                    survey_answers, declared_dimensions, form_seed, status, theta, theta_se, item_count,
                    started_at, completed_at)
            VALUES (:candidateId, :industryCode, :specializationCode, :targetGrade,
                    CAST(:surveyAnswers AS jsonb), :declaredDimensions, :formSeed, :status, :theta,
                    :thetaSe, :itemCount, COALESCE(CAST(:startedAt AS timestamptz), now()),
                    CAST(:completedAt AS timestamptz))
            """;

    private static final String UPDATE = """
            UPDATE test_sessions
            SET status = :status, target_grade = :targetGrade, theta = :theta, theta_se = :thetaSe,
                item_count = :itemCount, form_seed = :formSeed,
                completed_at = CAST(:completedAt AS timestamptz)
            WHERE id = :id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TestSessionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts one session and returns its generated id. A {@code null}
     * {@code startedAt} falls back to the DB default {@code now()}; {@code id} is
     * ignored on insert.
     */
    public long create(TestSession session) {
        Long id = jdbc.queryForObject(INSERT + System.lineSeparator() + "RETURNING id",
                params(session), Long.class);
        return id == null ? -1L : id;
    }

    /** Loads a session by primary key, empty when missing. */
    public Optional<TestSession> findById(long id) {
        List<TestSession> found = jdbc.query(
                "SELECT " + COLUMNS + " FROM test_sessions WHERE id = :id",
                new MapSqlParameterSource("id", id),
                MAPPER);
        return found.stream().findFirst();
    }

    /**
     * Writes back the mutable progress columns — {@code status},
     * {@code target_grade} (declared grade, S5.19), {@code theta},
     * {@code theta_se}, {@code item_count}, {@code form_seed} and
     * {@code completed_at} — for an existing session. Returns the rows changed
     * ({@code 0} when the id is unknown).
     */
    public int update(TestSession session) {
        return jdbc.update(UPDATE, new MapSqlParameterSource()
                .addValue("id", session.id())
                .addValue("status", session.status() == null ? null : session.status().wire())
                .addValue("targetGrade", session.targetGrade())
                .addValue("theta", session.theta())
                .addValue("thetaSe", session.thetaSe())
                .addValue("itemCount", session.itemCount())
                .addValue("formSeed", session.formSeed())
                .addValue("completedAt", JdbcRows.utc(session.completedAt())));
    }

    /** Sessions of one candidate, newest first by {@code started_at}. */
    public List<TestSession> findByCandidate(long candidateId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM test_sessions WHERE candidate_id = :candidateId "
                        + "ORDER BY started_at DESC",
                new MapSqlParameterSource("candidateId", candidateId),
                MAPPER);
    }

    private static MapSqlParameterSource params(TestSession session) {
        return new MapSqlParameterSource()
                .addValue("candidateId", session.candidateId())
                .addValue("industryCode", session.industryCode())
                .addValue("specializationCode", session.specializationCode())
                .addValue("targetGrade", session.targetGrade())
                .addValue("surveyAnswers", session.surveyAnswers())
                .addValue("declaredDimensions", textArray(session.declaredDimensions()))
                .addValue("formSeed", session.formSeed())
                .addValue("status", session.status() == null ? null : session.status().wire())
                .addValue("theta", session.theta())
                .addValue("thetaSe", session.thetaSe())
                .addValue("itemCount", session.itemCount())
                .addValue("startedAt", JdbcRows.utc(session.startedAt()))
                .addValue("completedAt", JdbcRows.utc(session.completedAt()));
    }

    /**
     * Binds a {@link List} of strings to a PostgreSQL {@code text[]} column by
     * creating the SQL array on the live connection (Spring's JDBC layer invokes
     * the returned {@link SqlTypeValue} with the connection).
     */
    private static SqlTypeValue textArray(List<String> values) {
        if (values == null) {
            return null;
        }
        return (ps, paramIndex, sqlType, typeName) -> {
            Array array = ps.getConnection().createArrayOf("text", values.toArray());
            if (sqlType == SqlTypeValue.TYPE_UNKNOWN) {
                ps.setObject(paramIndex, array);
            } else {
                ps.setObject(paramIndex, array, sqlType);
            }
        };
    }

    private static List<String> stringArray(java.sql.ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return null;
        }
        try {
            Object raw = array.getArray();
            if (raw instanceof String[] text) {
                return List.of(text);
            }
            if (raw instanceof Object[] objects) {
                String[] text = new String[objects.length];
                for (int i = 0; i < objects.length; i++) {
                    text[i] = objects[i] == null ? null : objects[i].toString();
                }
                return List.of(text);
            }
            return null;
        } finally {
            array.free();
        }
    }
}
