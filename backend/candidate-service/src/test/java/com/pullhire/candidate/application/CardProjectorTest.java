package com.pullhire.candidate.application;

import com.pullhire.candidate.domain.PrivacySettings;
import com.pullhire.candidate.domain.Profile;
import com.pullhire.candidate.domain.PublicCard;
import com.pullhire.candidate.repo.PrivacySettingsRepository;
import com.pullhire.candidate.repo.ProfileRepository;
import com.pullhire.candidate.repo.PublicCardRepository;
import com.pullhire.events.EventEnvelope;
import com.pullhire.events.OutboxEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Privacy projection matrix for {@link CardProjectorService}. */
class CardProjectorTest {

    private static final JsonMapper JSON = new JsonMapper();

    private ProfileRepository profiles;
    private PrivacySettingsRepository privacy;
    private PublicCardRepository cards;
    private OutboxEventPublisher outbox;
    private CardProjectorService projector;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileRepository.class);
        privacy = mock(PrivacySettingsRepository.class);
        cards = mock(PublicCardRepository.class);
        outbox = mock(OutboxEventPublisher.class);
        projector = new CardProjectorService(profiles, privacy, cards, new EventPublisher(outbox));
    }

    @Test
    void visibleProjectionUpsertsMaskedCardAndPublishes() throws Exception {
        when(profiles.findById(7L)).thenReturn(Optional.of(profile("Ivan Petrov")));
        when(privacy.findByProfileId(7L)).thenReturn(Optional.of(new PrivacySettings(7L, true, true, false, null)));
        when(cards.findByProfileId(7L)).thenReturn(Optional.empty());

        projector.rebuild(7L);

        ArgumentCaptor<PublicCard> card = ArgumentCaptor.forClass(PublicCard.class);
        verify(cards).upsert(card.capture());
        assertThat(card.getValue().displayName()).isEqualTo("Ivan P.");
        assertThat(card.getValue().contactReleased()).isFalse();
        assertThat(card.getValue().fspVerified()).isFalse();

        Map<String, Object> payload = capturedPayload();
        assertThat(payload.get("candidate_id")).isEqualTo("7");
        assertThat(payload.get("visible_to_employers")).isEqualTo(true);
        assertThat(payload.get("display_name")).isEqualTo("Ivan P.");
        assertThat(payload.get("contact_released")).isEqualTo(false);
    }

    @Test
    void hiddenProjectionDeletesRowAndPublishesNullContent() {
        when(profiles.findById(7L)).thenReturn(Optional.of(profile("Ivan Petrov")));
        when(privacy.findByProfileId(7L)).thenReturn(Optional.of(new PrivacySettings(7L, false, true, false, null)));
        when(cards.findByProfileId(7L)).thenReturn(Optional.of(
                new PublicCard(3L, 7L, "Ivan P.", "h", "it", "backend", "senior",
                        new String[]{"Java"}, false, true, Instant.now())));

        projector.rebuild(7L);

        verify(cards).deleteByProfileId(7L);
        verify(cards, never()).upsert(any());

        Map<String, Object> payload = capturedPayload();
        assertThat(payload.get("visible_to_employers")).isEqualTo(false);
        assertThat(payload.get("display_name")).isNull();
        assertThat(payload.get("headline")).isNull();
        assertThat(payload.get("stack")).isEqualTo(List.of());
        assertThat(payload.get("contact_released")).isEqualTo(true);
    }

    @Test
    void contactReleasedIsPreservedWhenCardExists() throws Exception {
        when(profiles.findById(7L)).thenReturn(Optional.of(profile("Ivan Petrov")));
        when(privacy.findByProfileId(7L)).thenReturn(Optional.of(new PrivacySettings(7L, true, true, false, null)));
        when(cards.findByProfileId(7L)).thenReturn(Optional.of(
                new PublicCard(3L, 7L, "Ivan P.", "h", "it", "backend", "senior",
                        new String[]{"Java"}, false, true, Instant.now())));

        projector.rebuild(7L);

        ArgumentCaptor<PublicCard> card = ArgumentCaptor.forClass(PublicCard.class);
        verify(cards).upsert(card.capture());
        assertThat(card.getValue().contactReleased()).isTrue();
    }

    @Test
    void maskIsDeterministic() {
        assertThat(CardProjectorService.mask("Иван Петров")).isEqualTo("Иван П.");
        assertThat(CardProjectorService.mask("Madonna")).isEqualTo("Madonna");
        assertThat(CardProjectorService.mask("   ")).isEqualTo("Кандидат");
        assertThat(CardProjectorService.mask(null)).isEqualTo("Кандидат");
    }

    private Map<String, Object> capturedPayload() {
        ArgumentCaptor<EventEnvelope> captured = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).publish(captured.capture());
        return JSON.readValue(captured.getValue().payloadJson(), Map.class);
    }

    private static Profile profile(String fullName) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Profile(7L, 1L, fullName, "candidate@example.com", "+7 999 123-45-67",
                "Senior Java Developer", "senior", new BigDecimal("8.0"),
                new String[]{"Java", "Spring"}, new String[]{"REST"}, new String[]{"Communication"},
                "Summary", "backend", "it", "published", now, now);
    }
}
