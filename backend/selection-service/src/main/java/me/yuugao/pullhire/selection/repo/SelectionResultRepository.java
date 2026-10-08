package me.yuugao.pullhire.selection.repo;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Persistence for {@code selection.selection_results} snapshots (V6,
 * {@code docs/api.md §3.5}, {@code docs/selection.md §4}). Snapshots are
 * immutable: refinement creates a new row via {@code parent_selection_id};
 * originals are never overwritten. {@code items} holds only
 * {@code candidate_id} + qualitative explanation — no card enrichment, no
 * {@code theta}.
 *
 * <p>jsonb writes mirror assessment {@code TestSessionRepository}: the payload
 * is serialized with {@code tools.jackson JsonMapper} and bound via
 * {@code CAST(:x AS jsonb)}.
 */
@Repository
public class SelectionResultRepository {

    /** Inserted snapshot identity: generated id plus DB-assigned {@code created_at}. */
    public record Saved(long id, OffsetDateTime createdAt) {
    }

    /** Grade holding candidates of one specialization, with taxonomy order. */
    public record GradeRow(String grade, Integer gradeOrder) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public SelectionResultRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Immutable snapshot row: jsonb columns as raw strings (Jackson-free). */
    public record Snapshot(long id, Long parentSelectionId, String requestJson,
                           String recommendedCategoriesJson, String itemsJson,
                           OffsetDateTime createdAt) {
    }

    /**
     * Inserts one snapshot and returns its generated id plus
     * {@code created_at}. A {@code null} {@code parentSelectionId} stores
     * {@code parent_selection_id = NULL} (original подборка); a non-null
     * value links a refinement row to its original.
     */
    public Saved insert(Long parentSelectionId, String requestJson,
                        String recommendedCategoriesJson, String itemsJson) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("parentSelectionId", parentSelectionId)
                .addValue("request", requestJson)
                .addValue("recommendedCategories", recommendedCategoriesJson)
                .addValue("items", itemsJson);
        return jdbc.queryForObject(
                "INSERT INTO selection_results"
                        + " (parent_selection_id, request, recommended_categories, items)"
                        + " VALUES (:parentSelectionId, CAST(:request AS jsonb),"
                        + " CAST(:recommendedCategories AS jsonb), CAST(:items AS jsonb))"
                        + " RETURNING id, created_at",
                params,
                (rs, rowNum) -> new Saved(
                        rs.getLong("id"),
                        rs.getObject("created_at", OffsetDateTime.class)));
    }

    /** Loads a snapshot by primary key, empty when missing. */
    public java.util.Optional<Snapshot> findById(long id) {
        List<Snapshot> found = jdbc.query(
                "SELECT id, parent_selection_id, request::text AS request,"
                        + " recommended_categories::text AS recommended_categories,"
                        + " items::text AS items, created_at"
                        + " FROM selection_results WHERE id = :id",
                new MapSqlParameterSource("id", id),
                (rs, rowNum) -> new Snapshot(
                        rs.getLong("id"),
                        rs.getObject("parent_selection_id", Long.class),
                        rs.getString("request"),
                        rs.getString("recommended_categories"),
                        rs.getString("items"),
                        rs.getObject("created_at", OffsetDateTime.class)));
        return found.stream().findFirst();
    }

    /**
     * Distinct grades of {@code candidate_categories} rows for one
     * specialization, ordered by taxonomy {@code grade_order}. Feeds the frozen
     * illustrative recommended-categories rule ({@code docs/selection.md §4.2},
     * underdetermined — OQ-style default recorded in the worklog).
     */
    public List<GradeRow> findGradesBySpecialization(String specializationCode) {
        return jdbc.query(
                "SELECT DISTINCT cat.grade AS grade, t.grade_order AS grade_order"
                        + " FROM candidate_categories cat"
                        + " LEFT JOIN taxonomy t ON t.kind = 'grade' AND t.code = cat.grade"
                        + " WHERE cat.specialization_code = :specialization"
                        + " ORDER BY t.grade_order NULLS LAST, cat.grade",
                new MapSqlParameterSource("specialization", specializationCode),
                (rs, rowNum) -> new GradeRow(
                        rs.getString("grade"),
                        (Integer) rs.getObject("grade_order")));
    }
}
