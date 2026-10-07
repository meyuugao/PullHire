package com.pullhire.auth.domain;

/**
 * 152-FZ consent kinds ({@code docs/data_model.md §3.4}). The set is append-only:
 * changes create new rows rather than mutating history.
 */
public enum ConsentType {
    DATA_PROCESSING("data_processing"),
    PROFILE_PUBLICATION("profile_publication"),
    FSP_LINK("fsp_link"),
    TASK_PARTICIPATION("task_participation");

    private final String wire;

    ConsentType(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static ConsentType fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("consent_type is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "data_processing" -> DATA_PROCESSING;
            case "profile_publication" -> PROFILE_PUBLICATION;
            case "fsp_link" -> FSP_LINK;
            case "task_participation" -> TASK_PARTICIPATION;
            default -> throw new IllegalArgumentException("unknown consent_type: " + value);
        };
    }
}
