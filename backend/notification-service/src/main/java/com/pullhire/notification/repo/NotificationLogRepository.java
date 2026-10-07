package com.pullhire.notification.repo;

import com.pullhire.notification.domain.NotificationLog;
import com.pullhire.notification.domain.NotificationStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JdbcTemplate access to {@code notification.notification_log}
 * ({@code docs/data_model.md §10.1}). No JPA; the UNIQUE {@code source_event_id}
 * constraint is the per-event idempotency guard.
 */
@Repository
public class NotificationLogRepository {

    private static final String COLUMNS =
            "id, recipient_user_id, recipient_email, template_code, event_type, "
                    + "source_event_id, status, provider_message_id, error, created_at, sent_at";

    private static final RowMapper<NotificationLog> MAPPER = (rs, rowNum) -> new NotificationLog(
            rs.getLong("id"),
            rs.getObject("recipient_user_id", Long.class),
            rs.getString("recipient_email"),
            rs.getString("template_code"),
            rs.getString("event_type"),
            rs.getString("source_event_id"),
            NotificationStatus.fromWire(rs.getString("status")),
            rs.getString("provider_message_id"),
            rs.getString("error"),
            JdbcRows.instant(rs, "created_at"),
            JdbcRows.instant(rs, "sent_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationLogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The log row for a source event, if any. Status-aware dedup: a {@code sent}
     * row means the event is done, while a {@code failed}/{@code queued} row is a
     * retryable prior attempt whose id must be reused. The UNIQUE
     * {@code source_event_id} constraint guarantees at most one row.
     */
    public Optional<NotificationLog> findBySourceEventId(String sourceEventId) {
        if (sourceEventId == null) {
            return Optional.empty();
        }
        List<NotificationLog> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM notification_log WHERE source_event_id = :sourceEventId",
                new MapSqlParameterSource("sourceEventId", sourceEventId),
                MAPPER);
        return rows.stream().findFirst();
    }

    /** Inserts a {@code queued} row and returns the generated id. */
    public long insertQueued(NotificationLog log) {
        Long id = jdbc.queryForObject(
                "INSERT INTO notification_log "
                        + "(recipient_user_id, recipient_email, template_code, event_type, source_event_id, status) "
                        + "VALUES (:recipientUserId, :recipientEmail, :templateCode, :eventType, :sourceEventId, 'queued') "
                        + "RETURNING id",
                new MapSqlParameterSource()
                        .addValue("recipientUserId", log.recipientUserId())
                        .addValue("recipientEmail", log.recipientEmail())
                        .addValue("templateCode", log.templateCode())
                        .addValue("eventType", log.eventType())
                        .addValue("sourceEventId", log.sourceEventId()),
                Long.class);
        return id == null ? -1L : id;
    }

    /** Transitions a row {@code queued → sent}. */
    public void markSent(long id, String providerMessageId, Instant sentAt) {
        jdbc.update(
                "UPDATE notification_log SET status = 'sent', provider_message_id = :providerMessageId, "
                        + "error = NULL, sent_at = :sentAt WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("providerMessageId", providerMessageId)
                        .addValue("sentAt", JdbcRows.utc(sentAt)));
    }

    /** Transitions a row {@code queued → failed}, recording the reason. */
    public void markFailed(long id, String error) {
        jdbc.update(
                "UPDATE notification_log SET status = 'failed', error = :error WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("error", error));
    }

    /**
     * Paged read of a recipient's own log. {@code sortColumn} must be a value
     * from the caller's whitelist (never raw user input).
     */
    public PageResult<NotificationLog> findByRecipientUserId(long recipientUserId, int page, int size,
                                                             String sortColumn, boolean ascending) {
        String sql = "SELECT " + COLUMNS + " FROM notification_log "
                + "WHERE recipient_user_id = :recipientUserId "
                + "ORDER BY " + sortColumn + (ascending ? " ASC" : " DESC")
                + " LIMIT :limit OFFSET :offset";
        List<NotificationLog> items = jdbc.query(sql,
                new MapSqlParameterSource()
                        .addValue("recipientUserId", recipientUserId)
                        .addValue("limit", size)
                        .addValue("offset", (long) page * size),
                MAPPER);
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM notification_log WHERE recipient_user_id = :recipientUserId",
                new MapSqlParameterSource("recipientUserId", recipientUserId),
                Long.class);
        return new PageResult<>(items, total == null ? 0L : total);
    }
}
