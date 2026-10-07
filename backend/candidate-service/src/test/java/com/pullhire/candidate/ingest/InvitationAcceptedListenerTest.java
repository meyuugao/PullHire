package com.pullhire.candidate.ingest;

import com.pullhire.candidate.application.CardProjectorService;
import com.pullhire.candidate.application.EventPublisher;
import com.pullhire.candidate.domain.PrivacySettings;
import com.pullhire.candidate.domain.Profile;
import com.pullhire.candidate.domain.PublicCard;
import com.pullhire.candidate.repo.PrivacySettingsRepository;
import com.pullhire.candidate.repo.ProcessedEventRepository;
import com.pullhire.candidate.repo.ProfileRepository;
import com.pullhire.candidate.repo.PublicCardRepository;
import com.pullhire.events.EventIdDedup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Durable (processed_events) + state-based idempotency of the contact-release consumer. */
class InvitationAcceptedListenerTest {

    private static final String TABLE = "candidate.processed_events";
    private static final String COLUMN = "event_id";

    private EventIdDedup dedup;
    private ProcessedEventRepository processedEvents;
    private PrivacySettingsRepository privacy;
    private CardProjectorService cards;
    private PublicCardRepository publicCards;
    private ProfileRepository profiles;
    private EventPublisher events;
    private InvitationAcceptedListener listener;

    @BeforeEach
    void setUp() {
        dedup = mock(EventIdDedup.class);
        processedEvents = mock(ProcessedEventRepository.class);
        privacy = mock(PrivacySettingsRepository.class);
        cards = mock(CardProjectorService.class);
        publicCards = mock(PublicCardRepository.class);
        profiles = mock(ProfileRepository.class);
        events = mock(EventPublisher.class);
        listener = new InvitationAcceptedListener(dedup, processedEvents, privacy, cards, publicCards,
                profiles, events);
    }

    @Test
    void duplicateEventIsNoOp() {
        when(dedup.isDuplicate("e-1", TABLE, COLUMN)).thenReturn(true);

        listener.onInvitationAccepted(event("7"));

        verifyNoInteractions(privacy, cards, publicCards, profiles, events);
    }

    @Test
    void showContactsFalsePublishesNothing() {
        when(processedEvents.claim("e-1")).thenReturn(true);
        when(privacy.findByProfileId(7L))
                .thenReturn(Optional.of(new PrivacySettings(7L, true, true, false, null)));

        listener.onInvitationAccepted(event("7"));

        verify(publicCards, never()).markContactReleased(anyLong());
        verifyNoInteractions(events);
    }

    @Test
    void releaseWhenEnabledPublishesContactReleased() {
        when(processedEvents.claim("e-1")).thenReturn(true);
        when(privacy.findByProfileId(7L))
                .thenReturn(Optional.of(new PrivacySettings(7L, true, true, true, null)));
        when(profiles.findById(7L)).thenReturn(Optional.of(profile()));
        when(cards.current(7L)).thenReturn(Optional.of(card()));
        when(publicCards.markContactReleased(7L)).thenReturn(1);

        listener.onInvitationAccepted(event("7"));

        ArgumentCaptor<Instant> releasedAt = ArgumentCaptor.forClass(Instant.class);
        verify(events).publishContactReleased(eq("7"), eq("candidate@example.com"), eq("+7 999 123-45-67"),
                releasedAt.capture());
        assertThat(releasedAt.getValue()).isNotNull();
    }

    @Test
    void rebuildsCardWhenMissingBeforeRelease() {
        when(processedEvents.claim("e-1")).thenReturn(true);
        when(privacy.findByProfileId(7L))
                .thenReturn(Optional.of(new PrivacySettings(7L, true, true, true, null)));
        when(profiles.findById(7L)).thenReturn(Optional.of(profile()));
        when(cards.current(7L)).thenReturn(Optional.empty());
        when(publicCards.markContactReleased(7L)).thenReturn(1);

        listener.onInvitationAccepted(event("7"));

        verify(cards).rebuild(7L);
        verify(events).publishContactReleased(eq("7"), any(), any(), any());
    }

    @Test
    void alreadyReleasedIsNoOp() {
        when(processedEvents.claim("e-1")).thenReturn(true);
        when(privacy.findByProfileId(7L))
                .thenReturn(Optional.of(new PrivacySettings(7L, true, true, true, null)));
        when(profiles.findById(7L)).thenReturn(Optional.of(profile()));
        when(cards.current(7L)).thenReturn(Optional.of(card()));
        when(publicCards.markContactReleased(7L)).thenReturn(0);

        listener.onInvitationAccepted(event("7"));

        verify(events, never()).publishContactReleased(any(), any(), any(), any());
    }

    @Test
    void emptyContactsIsNoOp() {
        when(processedEvents.claim("e-1")).thenReturn(true);
        when(privacy.findByProfileId(7L))
                .thenReturn(Optional.of(new PrivacySettings(7L, true, true, true, null)));
        Profile noContacts = new Profile(7L, 1L, "Ivan Petrov", null, null, null, null, null, null,
                null, null, null, null, null, "published", Instant.now(), Instant.now());
        when(profiles.findById(7L)).thenReturn(Optional.of(noContacts));

        listener.onInvitationAccepted(event("7"));

        verify(publicCards, never()).markContactReleased(anyLong());
        verify(events, never()).publishContactReleased(any(), any(), any(), any());
    }

    private static InvitationAcceptedEvent event(String candidateId) {
        return new InvitationAcceptedEvent("e-1", "invitation.accepted", 1, "2026-10-07T10:00:00Z",
                "outreach-service", null,
                new InvitationAcceptedEvent.Payload("inv-1", candidateId, "emp-1"));
    }

    private static Profile profile() {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Profile(7L, 1L, "Ivan Petrov", "candidate@example.com", "+7 999 123-45-67",
                null, null, null, null, null, null, null, null, null, "published", now, now);
    }

    private static PublicCard card() {
        return new PublicCard(3L, 7L, "Ivan P.", null, null, "backend", "senior",
                new String[]{"Java"}, false, false, Instant.now());
    }
}
