package me.yuugao.pullhire.notification.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.yuugao.pullhire.notification.domain.NotificationLog;

import java.time.Instant;

/**
 * One notification-log row as exposed by {@code GET /notifications}
 * ({@code docs/api.md §3.8}). Identifiers are strings; timestamps are ISO-8601 UTC.
 */
public record NotificationLogItem(
        @JsonProperty("id") String id,
        @JsonProperty("recipient_user_id") String recipientUserId,
        @JsonProperty("recipient_email") String recipientEmail,
        @JsonProperty("template_code") String templateCode,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("status") String status,
        @JsonProperty("provider_message_id") String providerMessageId,
        @JsonProperty("error") String error,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("sent_at") String sentAt) {

    public static NotificationLogItem from(NotificationLog log) {
        return new NotificationLogItem(
                Long.toString(log.id()),
                log.recipientUserId() == null ? null : Long.toString(log.recipientUserId()),
                log.recipientEmail(),
                log.templateCode(),
                log.eventType(),
                log.status().wire(),
                log.providerMessageId(),
                log.error(),
                iso(log.createdAt()),
                iso(log.sentAt()));
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
