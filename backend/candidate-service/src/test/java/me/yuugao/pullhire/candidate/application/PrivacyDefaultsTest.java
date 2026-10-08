package me.yuugao.pullhire.candidate.application;

import me.yuugao.pullhire.candidate.domain.PrivacySettings;
import me.yuugao.pullhire.candidate.domain.Profile;
import me.yuugao.pullhire.candidate.domain.PublicCard;
import me.yuugao.pullhire.candidate.repo.PrivacySettingsRepository;
import me.yuugao.pullhire.candidate.repo.ProfileRepository;
import me.yuugao.pullhire.candidate.repo.PublicCardRepository;
import me.yuugao.pullhire.events.EventEnvelope;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Defaults for {@link PrivacyService} and the projected {@code contact_released} flag. */
class PrivacyDefaultsTest {

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void privacyServiceReturnsDefaultsWithoutPersisting() {
        ProfileRepository profiles = mock(ProfileRepository.class);
        PrivacySettingsRepository privacy = mock(PrivacySettingsRepository.class);
        CardProjectorService cards = mock(CardProjectorService.class);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile()));
        when(privacy.findByProfileId(7L)).thenReturn(Optional.empty());

        PrivacySettings settings = new PrivacyService(profiles, privacy, cards).get(1L);

        assertThat(settings.visibleToEmployers()).isTrue();
        assertThat(settings.showFsp()).isTrue();
        assertThat(settings.showContacts()).isFalse();
        verify(privacy, never()).upsert(any());
    }

    @Test
    void projectorDefaultsContactReleasedFalseWhenPrivacyMissing() {
        ProfileRepository profiles = mock(ProfileRepository.class);
        PrivacySettingsRepository privacy = mock(PrivacySettingsRepository.class);
        PublicCardRepository cards = mock(PublicCardRepository.class);
        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        when(profiles.findById(7L)).thenReturn(Optional.of(profile()));
        when(privacy.findByProfileId(7L)).thenReturn(Optional.empty());
        when(cards.findByProfileId(7L)).thenReturn(Optional.empty());

        new CardProjectorService(profiles, privacy, cards, new EventPublisher(outbox)).rebuild(7L);

        ArgumentCaptor<PublicCard> card = ArgumentCaptor.forClass(PublicCard.class);
        verify(cards).upsert(card.capture());
        assertThat(card.getValue().contactReleased()).isFalse();

        ArgumentCaptor<EventEnvelope> envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).publish(envelope.capture());
        Map<String, Object> payload = JSON.readValue(envelope.getValue().payloadJson(), Map.class);
        assertThat(payload.get("visible_to_employers")).isEqualTo(true);
        assertThat(payload.get("contact_released")).isEqualTo(false);
    }

    private static Profile profile() {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Profile(7L, 1L, "Ivan Petrov", null, null, null, null, null, null, null, null,
                null, null, null, "published", now, now);
    }
}
