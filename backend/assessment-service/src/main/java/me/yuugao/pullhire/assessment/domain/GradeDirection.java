package me.yuugao.pullhire.assessment.domain;

/**
 * Direction of a grade/category change recorded in
 * {@code assessment.grade_history.direction} ({@code docs/data_model.md §6.1},
 * {@code docs/testing_model.md §7.1}): {@code initial} is the first assignment,
 * {@code up}/{@code down} a promotion/demotion, {@code recheck} a re-evaluation
 * that keeps the level. Wire values are the exact DB CHECK values.
 */
public enum GradeDirection {
    INITIAL("initial"),
    UP("up"),
    DOWN("down"),
    RECHECK("recheck");

    private final String wire;

    GradeDirection(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static GradeDirection fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("direction is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "initial" -> INITIAL;
            case "up" -> UP;
            case "down" -> DOWN;
            case "recheck" -> RECHECK;
            default -> throw new IllegalArgumentException("unknown direction: " + value);
        };
    }
}
