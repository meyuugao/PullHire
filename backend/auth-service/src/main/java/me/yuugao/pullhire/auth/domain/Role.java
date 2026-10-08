package me.yuugao.pullhire.auth.domain;

/**
 * Account role ({@code docs/data_model.md §3.1}); fixed at registration and immutable
 * thereafter (S1 exit criteria). Serialized as the english snake_case code.
 */
public enum Role {
    CANDIDATE("candidate"),
    EMPLOYER("employer");

    private final String wire;

    Role(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static Role fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("role is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "candidate" -> CANDIDATE;
            case "employer" -> EMPLOYER;
            default -> throw new IllegalArgumentException("unknown role: " + value);
        };
    }
}
