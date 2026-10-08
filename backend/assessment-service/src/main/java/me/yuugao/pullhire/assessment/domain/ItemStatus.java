package me.yuugao.pullhire.assessment.domain;

/**
 * Lifecycle status of an {@code item_bank} row ({@code docs/data_model.md §6.1}):
 * {@code active} items may be served, {@code retired} items never are.
 */
public enum ItemStatus {
    ACTIVE("active"),
    RETIRED("retired");

    private final String wire;

    ItemStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static ItemStatus fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("status is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "active" -> ACTIVE;
            case "retired" -> RETIRED;
            default -> throw new IllegalArgumentException("unknown status: " + value);
        };
    }
}
