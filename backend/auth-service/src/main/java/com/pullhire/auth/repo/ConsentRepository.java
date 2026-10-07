package com.pullhire.auth.repo;

import com.pullhire.auth.domain.Consent;
import com.pullhire.auth.domain.ConsentType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JdbcTemplate access to {@code auth.consents} ({@code docs/data_model.md §3.4}).
 * Append-only by design: there is intentionally no update method.
 */
@Repository
public class ConsentRepository {

    private static final String COLUMNS =
            "id, user_id, consent_type, granted, policy_version, granted_at, revoked_at, created_at";

    private static final RowMapper<Consent> MAPPER = (rs, rowNum) -> new Consent(
            rs.getLong("id"),
            rs.getLong("user_id"),
            ConsentType.fromWire(rs.getString("consent_type")),
            rs.getBoolean("granted"),
            rs.getString("policy_version"),
            JdbcRows.instant(rs, "granted_at"),
            JdbcRows.instant(rs, "revoked_at"),
            JdbcRows.instant(rs, "created_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public ConsentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Appends a consent change; timestamps are derived from {@code granted}. */
    public void insert(long userId, ConsentType type, boolean granted, String policyVersion) {
        jdbc.update("""
                INSERT INTO consents (user_id, consent_type, granted, policy_version, granted_at, revoked_at)
                VALUES (:userId, :type, :granted, :policyVersion,
                        CASE WHEN :granted THEN now() ELSE NULL END,
                        CASE WHEN :granted THEN NULL ELSE now() END)
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("type", type.wire())
                        .addValue("granted", granted)
                        .addValue("policyVersion", policyVersion));
    }

    /** Latest row per {@code consent_type} — the current consent state. */
    public List<Consent> findLatestByUser(long userId) {
        String sql = "SELECT DISTINCT ON (consent_type) " + COLUMNS
                + " FROM consents WHERE user_id = :userId"
                + " ORDER BY consent_type, created_at DESC, id DESC";
        return jdbc.query(sql, new MapSqlParameterSource("userId", userId), MAPPER);
    }
}
