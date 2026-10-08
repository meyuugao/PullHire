package me.yuugao.pullhire.candidate.domain;

/**
 * Resume origin ({@code docs/data_model.md §4.2}): {@code uploaded} by the
 * candidate or {@code generated} from the profile.
 */
public enum ResumeKind {
    UPLOADED("uploaded"),
    GENERATED("generated");

    private final String wire;

    ResumeKind(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static ResumeKind fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("kind is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "uploaded" -> UPLOADED;
            case "generated" -> GENERATED;
            default -> throw new IllegalArgumentException("unknown resume kind: " + value);
        };
    }
}
