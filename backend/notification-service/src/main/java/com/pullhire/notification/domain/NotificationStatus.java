package com.pullhire.notification.domain;

/**
 * Lifecycle of a queued notification ({@code docs/data_model.md §10.1}).
 * {@code queued} on ingest, then a terminal {@code sent} or {@code failed}.
 */
public enum NotificationStatus {
    QUEUED("queued"),
    SENT("sent"),
    FAILED("failed");

    private final String wire;

    NotificationStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static NotificationStatus fromWire(String value) {
        for (NotificationStatus status : values()) {
            if (status.wire.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Неизвестный статус уведомления: " + value);
    }
}
