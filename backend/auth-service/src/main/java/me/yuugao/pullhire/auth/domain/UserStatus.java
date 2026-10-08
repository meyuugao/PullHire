package me.yuugao.pullhire.auth.domain;

/**
 * Account lifecycle state ({@code docs/data_model.md §3.1}). A user is created
 * {@code pending} and becomes {@code active} only after email verification.
 */
public enum UserStatus {
    PENDING("pending"),
    ACTIVE("active"),
    BLOCKED("blocked");

    private final String wire;

    UserStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static UserStatus fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("status is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "pending" -> PENDING;
            case "active" -> ACTIVE;
            case "blocked" -> BLOCKED;
            default -> throw new IllegalArgumentException("unknown status: " + value);
        };
    }
}
