package me.yuugao.pullhire.employer.repo;

import me.yuugao.pullhire.employer.domain.Need;
import me.yuugao.pullhire.employer.domain.NeedCommand;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** JdbcTemplate access to {@code employer.needs} ({@code docs/data_model.md §8.2}). */
@Repository
public class NeedRepository {

    private static final String COLUMNS =
            "id, company_id, title, description, specialization_code, grade, stack, status, "
                    + "created_at, updated_at";

    private static final RowMapper<Need> MAPPER = (rs, rowNum) -> new Need(
            rs.getLong("id"),
            rs.getLong("company_id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("specialization_code"),
            rs.getString("grade"),
            JdbcRows.strings(rs, "stack"),
            rs.getString("status"),
            JdbcRows.instant(rs, "created_at"),
            JdbcRows.instant(rs, "updated_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public NeedRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Need> findById(long needId) {
        List<Need> needs = jdbc.query(
                "SELECT " + COLUMNS + " FROM needs WHERE id = :id",
                new MapSqlParameterSource("id", needId),
                MAPPER);
        return needs.stream().findFirst();
    }

    /**
     * Paged read of one company's needs. {@code sortColumn} must be a value
     * from the caller's whitelist (never raw user input).
     */
    public List<Need> findByCompanyId(long companyId, int limit, long offset,
                                      String sortColumn, boolean ascending) {
        String sql = "SELECT " + COLUMNS + " FROM needs "
                + "WHERE company_id = :companyId "
                + "ORDER BY " + sortColumn + (ascending ? " ASC" : " DESC")
                + " LIMIT :limit OFFSET :offset";
        return jdbc.query(sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                MAPPER);
    }

    public long countByCompanyId(long companyId) {
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM needs WHERE company_id = :companyId",
                new MapSqlParameterSource("companyId", companyId),
                Long.class);
        return total == null ? 0L : total;
    }

    /** Inserts a need under the given company and returns the persisted row. */
    public Need insert(long companyId, NeedCommand command) {
        Long id = jdbc.queryForObject(
                "INSERT INTO needs (company_id, title, description, specialization_code, grade, stack, status) "
                        + "VALUES (:companyId, :title, :description, :specializationCode, :grade, :stack, :status) "
                        + "RETURNING id",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("title", command.title())
                        .addValue("description", command.description())
                        .addValue("specializationCode", command.specializationCode())
                        .addValue("grade", command.grade())
                        .addValue("stack", command.stack())
                        .addValue("status", command.status()),
                Long.class);
        long needId = id == null ? -1L : id;
        return findById(needId)
                .orElseThrow(() -> new IllegalStateException("Need insert did not persist id=" + needId));
    }

    /**
     * Updates a need scoped to its owning company (defense in depth vs.
     * check-then-act race). Returns affected rows; {@code 0} after an existence
     * check means a concurrent cross-company move or delete.
     */
    public int update(long needId, long companyId, NeedCommand command) {
        return jdbc.update(
                "UPDATE needs SET title = :title, description = :description, "
                        + "specialization_code = :specializationCode, grade = :grade, "
                        + "stack = :stack, status = :status, updated_at = now() "
                        + "WHERE id = :id AND company_id = :companyId",
                new MapSqlParameterSource()
                        .addValue("id", needId)
                        .addValue("companyId", companyId)
                        .addValue("title", command.title())
                        .addValue("description", command.description())
                        .addValue("specializationCode", command.specializationCode())
                        .addValue("grade", command.grade())
                        .addValue("stack", command.stack())
                        .addValue("status", command.status()));
    }

    /**
     * Deletes a need scoped to its owning company (defense in depth vs.
     * check-then-act race). Returns affected rows. Plain row delete — the
     * {@code employer.need_deleted} outbox fan-out lives in
     * {@code NeedService.delete} via {@code EventPublisher}, sharing the same
     * transaction.
     */
    public int delete(long needId, long companyId) {
        return jdbc.update(
                "DELETE FROM needs WHERE id = :id AND company_id = :companyId",
                new MapSqlParameterSource()
                        .addValue("id", needId)
                        .addValue("companyId", companyId));
    }
}
