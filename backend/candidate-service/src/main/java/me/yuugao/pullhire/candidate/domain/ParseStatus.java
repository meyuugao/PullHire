package me.yuugao.pullhire.candidate.domain;

/**
 * Parsing state of a resume ({@code docs/data_model.md §4.2}): {@code pending} on
 * ingest, then a terminal {@code parsed} or {@code failed}.
 */
public enum ParseStatus {
    PENDING("pending"),
    PARSED("parsed"),
    FAILED("failed");

    private final String wire;

    ParseStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static ParseStatus fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("parse_status is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "pending" -> PENDING;
            case "parsed" -> PARSED;
            case "failed" -> FAILED;
            default -> throw new IllegalArgumentException("unknown parse_status: " + value);
        };
    }
}
