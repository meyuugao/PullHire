package me.yuugao.pullhire.assessment.domain;

/**
 * Lifecycle status of an {@code assessment.test_sessions} row
 * ({@code docs/data_model.md §6.1}): {@code started} on creation, advanced to
 * {@code in_progress} while items are served, then to a terminal
 * {@code completed} / {@code abandoned} / {@code needs_review} state.
 */
public enum SessionStatus {
    STARTED("started"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    ABANDONED("abandoned"),
    NEEDS_REVIEW("needs_review");

    private final String wire;

    SessionStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static SessionStatus fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("status is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "started" -> STARTED;
            case "in_progress" -> IN_PROGRESS;
            case "completed" -> COMPLETED;
            case "abandoned" -> ABANDONED;
            case "needs_review" -> NEEDS_REVIEW;
            default -> throw new IllegalArgumentException("unknown session status: " + value);
        };
    }
}
