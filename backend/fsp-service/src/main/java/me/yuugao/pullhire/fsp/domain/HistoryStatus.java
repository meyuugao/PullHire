package me.yuugao.pullhire.fsp.domain;

/**
 * Availability of a candidate's FSP achievement history
 * ({@code docs/data_model.md §5.1}): {@code unknown} before the first sync,
 * {@code present} when achievements exist, {@code no_history} when the registry
 * answered successfully but the candidate has none. {@code no_history} is neutral
 * (not a penalty, not a failure) and must stay distinguishable from a technical
 * error ({@code docs/fsp_integration.md §6}).
 */
public enum HistoryStatus {
    UNKNOWN("unknown"),
    PRESENT("present"),
    NO_HISTORY("no_history");

    private final String wire;

    HistoryStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static HistoryStatus fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("history_status is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "unknown" -> UNKNOWN;
            case "present" -> PRESENT;
            case "no_history" -> NO_HISTORY;
            default -> throw new IllegalArgumentException("unknown history_status: " + value);
        };
    }
}
