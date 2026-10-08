package me.yuugao.pullhire.selection.repo;

import me.yuugao.pullhire.selection.web.dto.CandidateCardResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only search over {@code selection.candidate_cards ⋈ candidate_categories}
 * ({@code docs/data_model.md §7.2}, {@code §7.3}, {@code docs/api.md §3.5},
 * {@code docs/selection.md §5}).
 *
 * <p>Visibility: {@code INNER JOIN} on {@code candidate_id} — a candidate
 * without a {@code category_assigned} row is never returned.
 * {@code category_confirmed} is derived: declared {@code candidate_cards.grade}
 * equals assigned {@code candidate_categories.grade}. Stable order by
 * {@code candidate_id} (ranking integration is ST-7.14's concern).
 */
@Repository
public class CandidateSearchRepository {

    private static final RowMapper<CandidateCardResponse> MAPPER = (rs, rowNum) -> {
        String grade = rs.getString("grade");
        String categoryGrade = rs.getString("category_grade");
        return new CandidateCardResponse(
                rs.getLong("candidate_id"),
                rs.getString("display_name"),
                rs.getString("specialization_code"),
                grade,
                CandidateCardResponse.toList(strings(rs, "stack")),
                rs.getBoolean("fsp_verified"),
                rs.getInt("fsp_achievements_count"),
                grade != null && grade.equals(categoryGrade),
                categoryGrade);
    };

    private final NamedParameterJdbcTemplate jdbc;

    public CandidateSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Paged search; {@code tags} null/empty disables the stack overlap filter. */
    public List<CandidateCardResponse> search(String specialization, String grade, String category,
                                             String[] tags, Boolean fspAchievements,
                                             int limit, long offset) {
        Where where = where(specialization, grade, category, tags, fspAchievements);
        String sql = "SELECT cc.candidate_id, cc.display_name, cc.specialization_code,"
                + " cc.grade, cc.stack, cc.fsp_verified, cc.fsp_achievements_count,"
                + " cat.grade AS category_grade"
                + " FROM candidate_cards cc"
                + " INNER JOIN candidate_categories cat ON cat.candidate_id = cc.candidate_id"
                + where.clause()
                + " ORDER BY cc.candidate_id LIMIT :limit OFFSET :offset";
        return jdbc.query(sql,
                where.params().addValue("limit", limit).addValue("offset", offset),
                MAPPER);
    }

    /** Total count for the same filter set (mirror employer {@code NeedRepository} pattern). */
    public long count(String specialization, String grade, String category,
                      String[] tags, Boolean fspAchievements) {
        Where where = where(specialization, grade, category, tags, fspAchievements);
        String sql = "SELECT count(*)"
                + " FROM candidate_cards cc"
                + " INNER JOIN candidate_categories cat ON cat.candidate_id = cc.candidate_id"
                + where.clause();
        Long total = jdbc.queryForObject(sql, where.params(), Long.class);
        return total == null ? 0L : total;
    }

    private static Where where(String specialization, String grade, String category,
                               String[] tags, Boolean fspAchievements) {
        List<String> clauses = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (specialization != null) {
            clauses.add("cat.specialization_code = :specialization");
            params.addValue("specialization", specialization);
        }
        if (grade != null) {
            clauses.add("cat.grade = :grade");
            params.addValue("grade", grade);
        }
        if (category != null) {
            clauses.add("cat.category_code = :category");
            params.addValue("category", category);
        }
        if (tags != null && tags.length > 0) {
            clauses.add("cc.stack && :tags");
            params.addValue("tags", textArray(tags));
        }
        if (fspAchievements != null) {
            if (fspAchievements) {
                clauses.add("cc.fsp_achievements_count > 0");
            } else {
                clauses.add("cc.fsp_achievements_count = 0");
            }
        }
        String clause = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        return new Where(clause, params);
    }

    private record Where(String clause, MapSqlParameterSource params) {
    }

    /**
     * Binds strings to a PostgreSQL {@code text[]} column by creating the SQL
     * array on the live connection (mirrors assessment
     * {@code TestSessionRepository.textArray}); a bare {@code String[]} binds
     * as {@code varchar[]} and {@code &&} has no {@code text[]}/{@code varchar[]}
     * overload.
     */
    private static SqlTypeValue textArray(String[] values) {
        return (ps, paramIndex, sqlType, typeName) -> {
            Array array = ps.getConnection().createArrayOf("text", values);
            if (sqlType == SqlTypeValue.TYPE_UNKNOWN) {
                ps.setObject(paramIndex, array);
            } else {
                ps.setObject(paramIndex, array, sqlType);
            }
        };
    }

    private static String[] strings(java.sql.ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return null;
        }
        Object value = array.getArray();
        return value instanceof String[] strings ? strings : null;
    }
}
