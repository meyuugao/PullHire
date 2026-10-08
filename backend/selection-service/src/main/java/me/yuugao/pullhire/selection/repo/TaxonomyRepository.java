package me.yuugao.pullhire.selection.repo;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only access to {@code taxonomy} ({@code docs/data_model.md §7.1}).
 */
@Repository
public class TaxonomyRepository {

    /** Single taxonomy row projection. */
    public record TaxonomyRow(String code, String name, Integer gradeOrder) {}

    private final JdbcTemplate jdbc;

    public TaxonomyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Active specializations ordered by code. */
    public List<TaxonomyRow> findActiveSpecializations() {
        return jdbc.query(
                "SELECT code, name, grade_order FROM taxonomy"
                        + " WHERE kind = 'specialization' AND is_active = true ORDER BY code",
                (rs, ignored) -> new TaxonomyRow(
                        rs.getString("code"),
                        rs.getString("name"),
                        (Integer) rs.getObject("grade_order")));
    }

    /** Active grades ordered by {@code grade_order}. */
    public List<TaxonomyRow> findActiveGrades() {
        return jdbc.query(
                "SELECT code, name, grade_order FROM taxonomy"
                        + " WHERE kind = 'grade' AND is_active = true ORDER BY grade_order, code",
                (rs, ignored) -> new TaxonomyRow(
                        rs.getString("code"),
                        rs.getString("name"),
                        (Integer) rs.getObject("grade_order")));
    }
}
