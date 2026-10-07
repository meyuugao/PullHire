package com.pullhire.notification.domain;

import java.time.Instant;

/**
 * A row of {@code notification.notification_log} ({@code docs/data_model.md §10.1}).
 * {@code recipientUserId} is a logical reference to {@code auth.users.id} (no FK).
 */
public record NotificationLog(
        long id,
        Long recipientUserId,
        String recipientEmail,
        String templateCode,
        String eventType,
        String sourceEventId,
        NotificationStatus status,
        String providerMessageId,
        String error,
        Instant createdAt,
        Instant sentAt) {

    /** Creates a not-yet-persisted queued row ({@code id} assigned by the database). */
    public static NotificationLog queued(Long recipientUserId, String recipientEmail,
                                         String templateCode, String eventType, String sourceEventId) {
        return new NotificationLog(0L, recipientUserId, recipientEmail, templateCode, eventType,
                sourceEventId, NotificationStatus.QUEUED, null, null, null, null);
    }
}
