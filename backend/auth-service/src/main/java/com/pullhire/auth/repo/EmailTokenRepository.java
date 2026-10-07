package com.pullhire.auth.repo;

import com.pullhire.auth.domain.EmailToken;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** JdbcTemplate access to {@code auth.email_tokens} ({@code docs/data_model.md §3.2}). */
@Repository
public class EmailTokenRepository {

    private static final String COLUMNS =
            "id, user_id, token_hash, purpose, expires_at, used_at, created_at";

    private static final RowMapper<EmailToken> MAPPER = (rs, rowNum) -> new EmailToken(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("token_hash"),
            rs.getString("purpose"),
            JdbcRows.instant(rs, "expires_at"),
            JdbcRows.instant(rs, "used_at"),
            JdbcRows.instant(rs, "created_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public EmailTokenRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long userId, String tokenHash, Instant expiresAt) {
        Long id = jdbc.queryForObject(
                "INSERT INTO email_tokens (user_id, token_hash, expires_at) "
                        + "VALUES (:userId, :tokenHash, :expiresAt) RETURNING id",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("tokenHash", tokenHash)
                        .addValue("expiresAt", JdbcRows.utc(expiresAt)),
                Long.class);
        return id == null ? -1L : id;
    }

    public Optional<EmailToken> findByHash(String tokenHash) {
        List<EmailToken> tokens = jdbc.query(
                "SELECT " + COLUMNS + " FROM email_tokens WHERE token_hash = :tokenHash",
                new MapSqlParameterSource("tokenHash", tokenHash),
                MAPPER);
        return tokens.stream().findFirst();
    }

    public void markUsed(long id) {
        jdbc.update("UPDATE email_tokens SET used_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }
}
