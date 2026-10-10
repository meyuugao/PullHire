package me.yuugao.pullhire.idempotency;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * JDBC result store for the Idempotency-Key middleware (docs/api.md §1).
 * Persists successful (2xx) responses per key in the owning service's own
 * {@code idempotency_keys} table via that service's {@link JdbcTemplate}.
 *
 * <p>Reference DDL:
 * {@code me/yuugao/pullhire/idempotency/idempotency-keys.reference.sql}.
 * This class owns no Flyway migration — consumers copy the reference DDL into
 * their per-schema migration.
 */
public class IdempotencyResultStore {

    /** Stored successful response replayed on a repeated key. */
    public record StoredResult(int statusCode, String contentType, byte[] body) {
    }

    private static final String SELECT =
            "SELECT status_code, content_type, response_body FROM idempotency_keys WHERE idempotency_key = ?";

    private static final String INSERT =
            "INSERT INTO idempotency_keys (idempotency_key, status_code, content_type, response_body) "
                    + "VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";

    private static final String CLEANUP =
            "DELETE FROM idempotency_keys WHERE created_at < now() - (? * interval '1 second')";

    private final JdbcTemplate jdbc;
    private final Duration ttl;

    public IdempotencyResultStore(JdbcTemplate jdbc, Duration ttl) {
        this.jdbc = jdbc;
        this.ttl = ttl != null ? ttl : Duration.ofHours(24);
    }

    /** Looks up a stored result by key; empty when never seen. */
    public Optional<StoredResult> find(String key) {
        try {
            StoredResult result = jdbc.queryForObject(SELECT, (rs, rowNum) -> new StoredResult(
                    rs.getInt("status_code"),
                    rs.getString("content_type"),
                    rs.getBytes("response_body")), key);
            return Optional.ofNullable(result);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Persists a successful result; first writer wins
     * ({@code ON CONFLICT DO NOTHING}). Lazily deletes expired rows in the same
     * batch (Решение F: TTL configurable, default 24h).
     */
    public void save(String key, int statusCode, String contentType, byte[] body) {
        jdbc.update(CLEANUP, ttl.toSeconds());
        jdbc.update(INSERT, key, statusCode, contentType, body);
    }
}
