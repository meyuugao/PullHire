package me.yuugao.pullhire.events;

/**
 * Canonical event names and the shared exchange, frozen to
 * {@code docs/architecture.md §1.2/§3.1}. These string values are a wire
 * contract: consumers bind routing keys to them and JSON Schemas under
 * {@code contracts/events/} reference them, so do not rename in place.
 */
public final class EventNames {

    /** Topic exchange every service publishes to and subscribes from. */
    public static final String EXCHANGE = "pullhire.events";

    // auth-service (architecture.md §3.1 row 1)
    public static final String USER_REGISTERED = "user.registered";
    public static final String USER_EMAIL_VERIFIED = "user.email_verified";
    public static final String CONSENT_UPDATED = "consent.updated";

    // candidate-service (row 2)
    public static final String CANDIDATE_CARD_UPDATED = "candidate.card_updated";
    public static final String CANDIDATE_CONTACT_RELEASED = "candidate.contact_released";

    // fsp-service (row 3)
    public static final String FSP_LINKED = "fsp.linked";
    public static final String FSP_ACHIEVEMENTS_UPDATED = "fsp.achievements_updated";

    // assessment-service (row 4)
    public static final String ASSESSMENT_CATEGORY_ASSIGNED = "assessment.category_assigned";
    public static final String ASSESSMENT_GRADE_CHANGED = "assessment.grade_changed";

    // employer-service (row 6)
    public static final String EMPLOYER_NEED_DELETED = "employer.need_deleted";

    // outreach-service (row 7)
    public static final String INVITATION_SENT = "invitation.sent";
    public static final String INVITATION_VIEWED = "invitation.viewed";
    public static final String INVITATION_ACCEPTED = "invitation.accepted";
    public static final String INVITATION_DECLINED = "invitation.declined";

    private EventNames() {
    }
}
