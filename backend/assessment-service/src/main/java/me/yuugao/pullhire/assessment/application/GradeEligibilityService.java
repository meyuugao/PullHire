package me.yuugao.pullhire.assessment.application;

import me.yuugao.pullhire.assessment.domain.Cooldown;
import me.yuugao.pullhire.assessment.repo.CooldownRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Grade-change eligibility ({@code docs/api.md §3.4},
 * {@code docs/testing_model.md §7.3}, {@code docs/assumptions.md §2}).
 *
 * <p>The single {@code grade_change} cooldown row is compared against the current
 * instant: with no row, or a row whose {@code available_after} has already passed,
 * a grade change is eligible. {@link CooldownRepository#findActive} deliberately
 * returns the row regardless of expiry, so the {@code available_after} vs now
 * decision lives here rather than in the repository.
 *
 * <p>Failure and a move to a lower grade never burn a cooldown
 * ({@code docs/assumptions.md §2}); a missing row therefore always means eligible.
 */
@Service
public class GradeEligibilityService {

    private final CooldownRepository cooldowns;

    public GradeEligibilityService(CooldownRepository cooldowns) {
        this.cooldowns = cooldowns;
    }

    /** Eligibility for the candidate at the current instant. */
    public GradeEligibility check(long candidateId) {
        return check(candidateId, Instant.now());
    }

    /** Eligibility for the candidate at {@code now}; deterministic seam for tests. */
    GradeEligibility check(long candidateId, Instant now) {
        Optional<Cooldown> cooldown = cooldowns.findActive(candidateId);
        if (cooldown.isPresent()) {
            Instant availableAfter = cooldown.get().availableAfter();
            if (availableAfter != null && availableAfter.isAfter(now)) {
                return GradeEligibility.ofRefused(availableAfter);
            }
        }
        return GradeEligibility.ofEligible();
    }

    /** Outcome: eligible now, or refused until {@code availableAfter}. */
    public record GradeEligibility(boolean eligible, Instant availableAfter) {

        static GradeEligibility ofEligible() {
            return new GradeEligibility(true, null);
        }

        static GradeEligibility ofRefused(Instant availableAfter) {
            return new GradeEligibility(false, availableAfter);
        }
    }
}
