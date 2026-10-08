package me.yuugao.pullhire.assessment.domain;

/**
 * Kind of an {@code item_bank} row ({@code docs/data_model.md §6.1}): {@code anchor}
 * calibrated items and {@code template} families. Only templates carry a
 * {@code template_family}; variability is seeded downstream
 * ({@code test_sessions.form_seed} / {@code attempts.variant_seed}).
 */
public enum ItemKind {
    ANCHOR("anchor"),
    TEMPLATE("template");

    private final String wire;

    ItemKind(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static ItemKind fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("item_kind is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "anchor" -> ANCHOR;
            case "template" -> TEMPLATE;
            default -> throw new IllegalArgumentException("unknown item_kind: " + value);
        };
    }
}
