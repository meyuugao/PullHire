package com.pullhire.candidate.application;

import com.pullhire.candidate.domain.Profile;
import com.pullhire.candidate.domain.ProfileCommand;
import com.pullhire.candidate.error.ApiException;
import com.pullhire.candidate.repo.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Candidate profile lifecycle ({@code docs/api.md §3.2},
 * {@code docs/data_model.md §4.1}). The owner is resolved from the JWT
 * {@code sub} (logical {@code user_id}); every mutation re-projects the public
 * card. Per OQ-21 the first successful upsert flips {@code status} to
 * {@code published}.
 */
@Service
public class ProfileService {

    private static final String PUBLISHED = "published";

    private final ProfileRepository profiles;
    private final CardProjectorService cards;

    public ProfileService(ProfileRepository profiles, CardProjectorService cards) {
        this.profiles = profiles;
        this.cards = cards;
    }

    public Optional<Profile> get(long userId) {
        return profiles.findByUserId(userId);
    }

    public Profile getRequired(long userId) {
        return profiles.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Профиль не найден"));
    }

    /** Creates or updates the caller's profile; publishes it on success (OQ-21). */
    @Transactional
    public Profile upsert(long userId, ProfileCommand command) {
        validate(command);
        Optional<Profile> existing = profiles.findByUserId(userId);
        Instant createdAt = existing.map(Profile::createdAt).orElse(null);
        long id = existing.map(Profile::id).orElse(0L);

        Profile toSave = new Profile(id, userId, command.fullName().trim(),
                trimmed(command.contactEmail()), trimmed(command.contactPhone()),
                trimmed(command.positionTitle()), trimmed(command.gradeDeclared()),
                command.experienceYears(), toArray(command.stack()), toArray(command.skills()),
                toArray(command.softSkills()), trimmed(command.summary()),
                trimmed(command.specializationCode()), trimmed(command.industryCode()),
                PUBLISHED, createdAt, Instant.now());

        long profileId = profiles.upsertByUserId(toSave);
        cards.rebuild(profileId);
        return profiles.findById(profileId).orElse(toSave);
    }

    private static void validate(ProfileCommand command) {
        if (command == null || command.fullName() == null || command.fullName().isBlank()) {
            throw ApiException.validation("full_name обязателен");
        }
        BigDecimal years = command.experienceYears();
        if (years != null && years.signum() < 0) {
            throw ApiException.validation("experience_years не может быть отрицательным");
        }
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static String[] toArray(List<String> values) {
        return values == null ? null : values.stream().filter(Objects::nonNull).toArray(String[]::new);
    }
}
