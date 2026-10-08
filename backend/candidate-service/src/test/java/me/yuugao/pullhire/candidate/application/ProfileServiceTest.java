package me.yuugao.pullhire.candidate.application;

import me.yuugao.pullhire.candidate.domain.Profile;
import me.yuugao.pullhire.candidate.domain.ProfileCommand;
import me.yuugao.pullhire.candidate.error.ApiException;
import me.yuugao.pullhire.candidate.repo.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Profile upsert semantics (OQ-21) and validation, with a mocked repository. */
class ProfileServiceTest {

    private ProfileRepository profiles;
    private CardProjectorService cards;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileRepository.class);
        cards = mock(CardProjectorService.class);
        service = new ProfileService(profiles, cards);
    }

    @Test
    void firstUpsertSetsStatusPublishedAndRebuildsCard() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.empty());
        when(profiles.upsertByUserId(any())).thenReturn(7L);
        when(profiles.findById(7L)).thenReturn(Optional.of(profile(7L, "published")));

        Profile result = service.upsert(1L, command("Ivan Petrov", null));

        ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).upsertByUserId(saved.capture());
        assertThat(saved.getValue().id()).isZero();
        assertThat(saved.getValue().status()).isEqualTo("published");
        verify(cards).rebuild(7L);
        assertThat(result.status()).isEqualTo("published");
    }

    @Test
    void existingDraftBecomesPublished() {
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(7L, "draft")));
        when(profiles.upsertByUserId(any())).thenReturn(7L);
        when(profiles.findById(7L)).thenReturn(Optional.of(profile(7L, "published")));

        service.upsert(1L, command("Ivan Petrov", null));

        ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).upsertByUserId(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(7L);
        assertThat(saved.getValue().status()).isEqualTo("published");
    }

    @Test
    void blankFullNameIsValidationError() {
        assertThatThrownBy(() -> service.upsert(1L, command("   ", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void negativeExperienceIsValidationError() {
        assertThatThrownBy(() -> service.upsert(1L, command("Ivan", new BigDecimal("-1"))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static ProfileCommand command(String fullName, BigDecimal years) {
        return new ProfileCommand(fullName, null, null, null, null, years, null, null, null, null, null, null);
    }

    private static Profile profile(long id, String status) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Profile(id, 1L, "Ivan Petrov", null, null, null, null, null, null, null, null,
                null, null, null, status, now, now);
    }
}
