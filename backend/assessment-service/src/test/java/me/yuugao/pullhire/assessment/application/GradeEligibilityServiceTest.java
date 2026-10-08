package me.yuugao.pullhire.assessment.application;

import java.time.Instant;
import java.util.Optional;

import me.yuugao.pullhire.assessment.application.GradeEligibilityService.GradeEligibility;
import me.yuugao.pullhire.assessment.domain.Cooldown;
import me.yuugao.pullhire.assessment.repo.CooldownRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cooldown comparison ({@code docs/testing_model.md §7.3},
 * {@code docs/assumptions.md §2}): eligible with no row or once
 * {@code available_after} has passed; refused while it is still in the future.
 * No Spring context; the {@code now} instant is injected.
 */
class GradeEligibilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final long CANDIDATE = 42L;

    private CooldownRepository cooldowns;
    private GradeEligibilityService service;

    @BeforeEach
    void setUp() {
        cooldowns = mock(CooldownRepository.class);
        service = new GradeEligibilityService(cooldowns);
    }

    @Test
    void eligibleWhenNoCooldown() {
        when(cooldowns.findActive(CANDIDATE)).thenReturn(Optional.empty());

        GradeEligibility result = service.check(CANDIDATE, NOW);

        assertThat(result.eligible()).isTrue();
        assertThat(result.availableAfter()).isNull();
    }

    @Test
    void refusedWithAvailableAfterWhenActive() {
        Instant availableAfter = NOW.plusSeconds(86_400);
        when(cooldowns.findActive(CANDIDATE)).thenReturn(Optional.of(cooldown(availableAfter)));

        GradeEligibility result = service.check(CANDIDATE, NOW);

        assertThat(result.eligible()).isFalse();
        assertThat(result.availableAfter()).isEqualTo(availableAfter);
        verify(cooldowns).findActive(CANDIDATE);
    }

    @Test
    void eligibleWhenCooldownExpired() {
        Instant availableAfter = NOW.minusSeconds(86_400);
        when(cooldowns.findActive(CANDIDATE)).thenReturn(Optional.of(cooldown(availableAfter)));

        GradeEligibility result = service.check(CANDIDATE, NOW);

        assertThat(result.eligible()).isTrue();
        assertThat(result.availableAfter()).isNull();
    }

    @Test
    void eligibleWhenAvailableAfterEqualsNow() {
        when(cooldowns.findActive(CANDIDATE)).thenReturn(Optional.of(cooldown(NOW)));

        assertThat(service.check(CANDIDATE, NOW).eligible()).isTrue();
    }

    private static Cooldown cooldown(Instant availableAfter) {
        return new Cooldown(1L, CANDIDATE, CooldownRepository.SCOPE_GRADE_CHANGE,
                availableAfter, Instant.parse("2026-05-15T12:00:00Z"));
    }
}
