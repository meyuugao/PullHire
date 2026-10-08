package me.yuugao.pullhire.candidate.application;

import me.yuugao.pullhire.candidate.domain.PrivacySettings;
import me.yuugao.pullhire.candidate.domain.Profile;
import me.yuugao.pullhire.candidate.repo.PrivacySettingsRepository;
import me.yuugao.pullhire.candidate.repo.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Privacy defaults and PUT semantics, with mocked repositories. */
class PrivacyServiceTest {

    private ProfileRepository profiles;
    private PrivacySettingsRepository privacy;
    private CardProjectorService cards;
    private PrivacyService service;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileRepository.class);
        privacy = mock(PrivacySettingsRepository.class);
        cards = mock(CardProjectorService.class);
        service = new PrivacyService(profiles, privacy, cards);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile()));
    }

    @Test
    void firstGetReturnsDefaultsWithoutPersisting() {
        when(privacy.findByProfileId(7L)).thenReturn(Optional.empty());

        PrivacySettings settings = service.get(1L);

        assertThat(settings.visibleToEmployers()).isTrue();
        assertThat(settings.showFsp()).isTrue();
        assertThat(settings.showContacts()).isFalse();
        verify(privacy, never()).upsert(any());
    }

    @Test
    void putUpdatesAndRebuildsCard() {
        when(privacy.findByProfileId(7L)).thenReturn(Optional.empty());

        PrivacySettings settings = service.update(1L, false, false, true);

        verify(privacy).upsert(any());
        verify(cards).rebuild(7L);
        assertThat(settings.visibleToEmployers()).isFalse();
        assertThat(settings.showFsp()).isFalse();
        assertThat(settings.showContacts()).isTrue();
    }

    private static Profile profile() {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Profile(7L, 1L, "Ivan Petrov", null, null, null, null, null, null, null, null,
                null, null, null, "published", now, now);
    }
}
