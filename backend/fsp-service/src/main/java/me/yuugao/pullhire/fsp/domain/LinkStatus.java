package me.yuugao.pullhire.fsp.domain;

/**
 * Technical state of an FSP link ({@code docs/data_model.md §5.1}):
 * {@code pending} before the first adapter call, {@code linked} once a registry id
 * is bound, {@code unlinked} after an explicit unbind, and {@code error} when the
 * adapter failed. Kept distinct from {@link HistoryStatus} so a registry failure is
 * never confused with a candidate having no achievements
 * ({@code docs/fsp_integration.md §6}).
 */
public enum LinkStatus {
    PENDING("pending"),
    LINKED("linked"),
    UNLINKED("unlinked"),
    ERROR("error");

    private final String wire;

    LinkStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static LinkStatus fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("link_status is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "pending" -> PENDING;
            case "linked" -> LINKED;
            case "unlinked" -> UNLINKED;
            case "error" -> ERROR;
            default -> throw new IllegalArgumentException("unknown link_status: " + value);
        };
    }
}
