package me.yuugao.pullhire.outreach.repo;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import me.yuugao.pullhire.outreach.domain.Invitation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JdbcTemplate access to {@code outreach.invitations} ({@code docs/data_model.md §9.1}). */
@Repository
public class InvitationRepository {

    private static final String COLUMNS =
            "id, employer_id, candidate_id, need_id, vacancy_id, message, company_name, "
                    + "employer_contact, salary_from, salary_to, salary_basis, status, "
                    + "released_contact, contact_released_at, created_at, updated_at";

    private static final RowMapper<Invitation> MAPPER = (rs, rowNum) -> new Invitation(
            rs.getLong("id"),
            rs.getLong("employer_id"),
            rs.getLong("candidate_id"),
            nullableLong(rs, "need_id"),
            nullableLong(rs, "vacancy_id"),
            rs.getString("message"),
            rs.getString("company_name"),
            rs.getString("employer_contact"),
            rs.getBigDecimal("salary_from"),
            rs.getBigDecimal("salary_to"),
            rs.getString("salary_basis"),
            rs.getString("status"),
            rs.getString("released_contact"),
            JdbcRows.instant(rs, "contact_released_at"),
            JdbcRows.instant(rs, "created_at"),
            JdbcRows.instant(rs, "updated_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public InvitationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts an invitation in {@code sent} status and returns the persisted row. */
    public Invitation insert(long employerId, long candidateId, Long needId, Long vacancyId,
                             String message, String companyName, String employerContactJson,
                             BigDecimal salaryFrom, BigDecimal salaryTo, String salaryBasis) {
        Long id = jdbc.queryForObject(
                "INSERT INTO invitations (employer_id, candidate_id, need_id, vacancy_id, message, "
                        + "company_name, employer_contact, salary_from, salary_to, salary_basis, status) "
                        + "VALUES (:employerId, :candidateId, :needId, :vacancyId, :message, "
                        + ":companyName, CAST(:employerContact AS jsonb), :salaryFrom, :salaryTo, :salaryBasis, 'sent') "
                        + "RETURNING id",
                new MapSqlParameterSource()
                        .addValue("employerId", employerId)
                        .addValue("candidateId", candidateId)
                        .addValue("needId", needId)
                        .addValue("vacancyId", vacancyId)
                        .addValue("message", message)
                        .addValue("companyName", companyName)
                        .addValue("employerContact", employerContactJson)
                        .addValue("salaryFrom", salaryFrom)
                        .addValue("salaryTo", salaryTo)
                        .addValue("salaryBasis", salaryBasis),
                Long.class);
        long invitationId = id == null ? -1L : id;
        return findById(invitationId)
                .orElseThrow(() -> new IllegalStateException("Invitation insert did not persist id=" + invitationId));
    }

    public Optional<Invitation> findById(long invitationId) {
        List<Invitation> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM invitations WHERE id = :id",
                new MapSqlParameterSource("id", invitationId),
                MAPPER);
        return rows.stream().findFirst();
    }

    /** Paged read of one employer's invitations, newest first, with optional status filter. */
    public List<Invitation> findByEmployerId(long employerId, String status, int limit, long offset) {
        return findByOwner("employer_id", employerId, status, limit, offset);
    }

    public long countByEmployerId(long employerId, String status) {
        return countByOwner("employer_id", employerId, status);
    }

    /** Paged read of one candidate's invitations, newest first, with optional status filter. */
    public List<Invitation> findByCandidateId(long candidateId, String status, int limit, long offset) {
        return findByOwner("candidate_id", candidateId, status, limit, offset);
    }

    public long countByCandidateId(long candidateId, String status) {
        return countByOwner("candidate_id", candidateId, status);
    }

    private List<Invitation> findByOwner(String column, long ownerId, String status, int limit, long offset) {
        String sql = "SELECT " + COLUMNS + " FROM invitations "
                + "WHERE " + column + " = :ownerId "
                + (status == null ? "" : "AND status = :status ")
                + "ORDER BY created_at DESC LIMIT :limit OFFSET :offset";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("limit", limit)
                .addValue("offset", offset);
        if (status != null) {
            params.addValue("status", status);
        }
        return jdbc.query(sql, params, MAPPER);
    }

    private long countByOwner(String column, long ownerId, String status) {
        String sql = "SELECT count(*) FROM invitations "
                + "WHERE " + column + " = :ownerId "
                + (status == null ? "" : "AND status = :status");
        MapSqlParameterSource params = new MapSqlParameterSource("ownerId", ownerId);
        if (status != null) {
            params.addValue("status", status);
        }
        Long total = jdbc.queryForObject(sql, params, Long.class);
        return total == null ? 0L : total;
    }

    /**
     * Conditional status transition (defense in depth vs. check-then-act race).
     * Returns affected rows; {@code 0} after an existence check means a
     * concurrent transition (caller maps to 409 {@code conflict}).
     */
    public int transitionUpdate(long invitationId, String expectedStatus, String newStatus) {
        return jdbc.update(
                "UPDATE invitations SET status = :newStatus, updated_at = now() "
                        + "WHERE id = :id AND status = :expectedStatus",
                new MapSqlParameterSource()
                        .addValue("id", invitationId)
                        .addValue("expectedStatus", expectedStatus)
                        .addValue("newStatus", newStatus));
    }

    /**
     * Fills the released contact snapshot on every accepted, not-yet-released
     * invitation of the candidate. Returns affected rows; {@code 0} means
     * nothing to release (no-op for the caller).
     */
    public int markReleased(long candidateId, String releasedContactJson, Instant releasedAt) {
        return jdbc.update(
                "UPDATE invitations SET released_contact = CAST(:releasedContact AS jsonb), "
                        + "contact_released_at = :releasedAt, updated_at = now() "
                        + "WHERE candidate_id = :candidateId AND status = 'accepted' "
                        + "AND released_contact IS NULL",
                new MapSqlParameterSource()
                        .addValue("candidateId", candidateId)
                        .addValue("releasedContact", releasedContactJson)
                        .addValue("releasedAt", JdbcRows.utc(releasedAt)));
    }

    /** Ids that {@link #markReleased} would fill — used to append one audit event per row. */
    public List<Long> findAcceptedIdsPendingRelease(long candidateId) {
        return jdbc.queryForList(
                "SELECT id FROM invitations WHERE candidate_id = :candidateId "
                        + "AND status = 'accepted' AND released_contact IS NULL",
                new MapSqlParameterSource("candidateId", candidateId),
                Long.class);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
