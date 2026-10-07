package com.pullhire.auth.repo;

import com.pullhire.auth.domain.RefreshToken;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** JdbcTemplate access to {@code auth.refresh_tokens} ({@code docs/data_model.md §3.3}). */
@Repository
public class RefreshTokenRepository {

    private static final String COLUMNS = "id, user_id, token_hash, expires_at, revoked_at, created_at";

    private static final RowMapper<RefreshToken> MAPPER = (rs, rowNum) -> new RefreshToken(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("token_hash"),
            JdbcRows.instant(rs, "expires_at"),
            JdbcRows.instant(rs, "revoked_at"),
            JdbcRows.instant(rs, "created_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public RefreshTokenRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(long userId, String tokenHash, Instant expiresAt) {
        Long id = jdbc.queryForObject(
                "INSERT INTO refresh_tokens (user_id, token_hash, expires_at) "
                        + "VALUES (:userId, :tokenHash, :expiresAt) RETURNING id",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("tokenHash", tokenHash)
                        .addValue("expiresAt", JdbcRows.utc(expiresAt)),
                Long.class);
        return id == null ? -1L : id;
    }

    public Optional<RefreshToken> findByHash(String tokenHash) {
        List<RefreshToken> tokens = jdbc.query(
                "SELECT " + COLUMNS + " FROM refresh_tokens WHERE token_hash = :tokenHash",
                new MapSqlParameterSource("tokenHash", tokenHash),
                MAPPER);
        return tokens.stream().findFirst();
    }

    public void revoke(long id) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }
}
