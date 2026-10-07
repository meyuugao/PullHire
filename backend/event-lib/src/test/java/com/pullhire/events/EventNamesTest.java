package com.pullhire.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes the event-name wire strings ({@code docs/architecture.md §1.2/§3.1}).
 * A failure here means a renamed routing key — a breaking contract change.
 */
class EventNamesTest {

    @Test
    void exchangeIsStable() {
        assertThat(EventNames.EXCHANGE).isEqualTo("pullhire.events");
    }

    @Test
    void allDocumentedEventNamesAreExact() {
        assertThat(EventNames.USER_REGISTERED).isEqualTo("user.registered");
        assertThat(EventNames.USER_EMAIL_VERIFIED).isEqualTo("user.email_verified");
        assertThat(EventNames.CONSENT_UPDATED).isEqualTo("consent.updated");
        assertThat(EventNames.CANDIDATE_CARD_UPDATED).isEqualTo("candidate.card_updated");
        assertThat(EventNames.CANDIDATE_CONTACT_RELEASED).isEqualTo("candidate.contact_released");
        assertThat(EventNames.FSP_LINKED).isEqualTo("fsp.linked");
        assertThat(EventNames.FSP_ACHIEVEMENTS_UPDATED).isEqualTo("fsp.achievements_updated");
        assertThat(EventNames.ASSESSMENT_CATEGORY_ASSIGNED).isEqualTo("assessment.category_assigned");
        assertThat(EventNames.ASSESSMENT_GRADE_CHANGED).isEqualTo("assessment.grade_changed");
        assertThat(EventNames.INVITATION_SENT).isEqualTo("invitation.sent");
        assertThat(EventNames.INVITATION_VIEWED).isEqualTo("invitation.viewed");
        assertThat(EventNames.INVITATION_ACCEPTED).isEqualTo("invitation.accepted");
        assertThat(EventNames.INVITATION_DECLINED).isEqualTo("invitation.declined");
    }
}
