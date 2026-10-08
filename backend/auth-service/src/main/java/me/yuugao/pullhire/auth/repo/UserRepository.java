package me.yuugao.pullhire.auth.repo;

import me.yuugao.pullhire.auth.domain.Role;
import me.yuugao.pullhire.auth.domain.User;
import me.yuugao.pullhire.auth.domain.UserStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** JdbcTemplate access to {@code auth.users} ({@code docs/data_model.md §3.1}). */
@Repository
public class UserRepository {

    private static final String COLUMNS =
            "id, email, password_hash, role, status, email_verified_at, deleted_at, created_at, updated_at";

    private static final RowMapper<User> MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            Role.fromWire(rs.getString("role")),
            UserStatus.fromWire(rs.getString("status")),
            JdbcRows.instant(rs, "email_verified_at"),
            JdbcRows.instant(rs, "deleted_at"),
            JdbcRows.instant(rs, "created_at"),
            JdbcRows.instant(rs, "updated_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public UserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByEmail(String email) {
        List<User> users = jdbc.query(
                "SELECT " + COLUMNS + " FROM users WHERE email = :email",
                new MapSqlParameterSource("email", email),
                MAPPER);
        return users.stream().findFirst();
    }

    public Optional<User> findById(long id) {
        List<User> users = jdbc.query(
                "SELECT " + COLUMNS + " FROM users WHERE id = :id",
                new MapSqlParameterSource("id", id),
                MAPPER);
        return users.stream().findFirst();
    }

    /** Inserts a {@code pending} user and returns the generated id. */
    public long insert(String email, String passwordHash, Role role) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, role, status) "
                        + "VALUES (:email, :passwordHash, :role, 'pending') RETURNING id",
                new MapSqlParameterSource()
                        .addValue("email", email)
                        .addValue("passwordHash", passwordHash)
                        .addValue("role", role.wire()),
                Long.class);
        return id == null ? -1L : id;
    }

    /** Marks the account verified and activates it ({@code docs/data_model.md §3.1}). */
    public void markEmailVerified(long id) {
        jdbc.update("UPDATE users SET email_verified_at = now(), status = 'active', updated_at = now() "
                        + "WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }
}
