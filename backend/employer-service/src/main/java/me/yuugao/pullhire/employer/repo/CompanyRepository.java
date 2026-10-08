package me.yuugao.pullhire.employer.repo;

import me.yuugao.pullhire.employer.domain.Company;
import me.yuugao.pullhire.employer.domain.CompanyCommand;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** JdbcTemplate access to {@code employer.companies} ({@code docs/data_model.md §8.1}). */
@Repository
public class CompanyRepository {

    private static final String COLUMNS =
            "id, user_id, name, description, industry, contact_email, contact_phone, website, "
                    + "created_at, updated_at";

    private static final RowMapper<Company> MAPPER = (rs, rowNum) -> new Company(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("industry"),
            rs.getString("contact_email"),
            rs.getString("contact_phone"),
            rs.getString("website"),
            JdbcRows.instant(rs, "created_at"),
            JdbcRows.instant(rs, "updated_at"));

    /**
     * Single-statement upsert keyed by {@code user_id} (constraint
     * {@code uq_companies_user_id}). {@code created_at} is set only by the
     * column {@code DEFAULT now()} on insert; every conflicting write refreshes
     * only the profile fields plus {@code updated_at}. The atomic statement is
     * the concurrent-safety mechanism: two racing first-time PUTs for the same
     * user converge on one row instead of creating duplicates (one may see a
     * unique-violation retry at the driver level, never two rows).
     */
    private static final String UPSERT_BY_USER_ID = """
            INSERT INTO companies (user_id, name, description, industry, contact_email, contact_phone, website)
            VALUES (:userId, :name, :description, :industry, :contactEmail, :contactPhone, :website)
            ON CONFLICT (user_id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                industry = EXCLUDED.industry,
                contact_email = EXCLUDED.contact_email,
                contact_phone = EXCLUDED.contact_phone,
                website = EXCLUDED.website,
                updated_at = now()
            RETURNING id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CompanyRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Company> findById(long id) {
        List<Company> companies = jdbc.query(
                "SELECT " + COLUMNS + " FROM companies WHERE id = :id",
                new MapSqlParameterSource("id", id),
                MAPPER);
        return companies.stream().findFirst();
    }

    public Optional<Company> findByUserId(long userId) {
        List<Company> companies = jdbc.query(
                "SELECT " + COLUMNS + " FROM companies WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId),
                MAPPER);
        return companies.stream().findFirst();
    }

    /** Inserts or updates the company keyed by its logical owner {@code user_id}; returns the row. */
    public Company upsert(long userId, CompanyCommand command) {
        Long id = jdbc.queryForObject(UPSERT_BY_USER_ID, params(userId, command), Long.class);
        long companyId = id == null ? -1L : id;
        return findById(companyId).orElseThrow(() -> new IllegalStateException("Company upsert did not persist id=" + companyId));
    }

    private static MapSqlParameterSource params(long userId, CompanyCommand command) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("name", command.name())
                .addValue("description", command.description())
                .addValue("industry", command.industry())
                .addValue("contactEmail", command.contactEmail())
                .addValue("contactPhone", command.contactPhone())
                .addValue("website", command.website());
    }
}
