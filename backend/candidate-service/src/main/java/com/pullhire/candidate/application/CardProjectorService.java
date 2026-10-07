package com.pullhire.candidate.application;

import com.pullhire.candidate.domain.PrivacySettings;
import com.pullhire.candidate.domain.Profile;
import com.pullhire.candidate.domain.PublicCard;
import com.pullhire.candidate.repo.PrivacySettingsRepository;
import com.pullhire.candidate.repo.ProfileRepository;
import com.pullhire.candidate.repo.PublicCardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Builds the privacy-filtered {@code candidate.public_cards} projection and emits
 * {@code candidate.card_updated} ({@code docs/data_model.md §4.4},
 * {@code docs/architecture.md §6.2}). The card is the only employer-visible view;
 * the event carries the agreed fields and never PII.
 *
 * <p>Visibility semantics (OQ-16): {@code visible_to_employers=false} deletes the
 * public card row and publishes the event with {@code visible_to_employers=false}
 * and null content fields; {@code true} upserts the card (preserving any existing
 * {@code contact_released} flag).
 *
 * <p>F7: the headline derived from {@code position_title} is scrubbed of email/phone
 * patterns before it enters the card or the event.
 */
@Service
public class CardProjectorService {

    /** Placeholder shown when a profile has no name yet (public_cards.display_name is NOT NULL). */
    static final String UNNAMED = "Кандидат";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\S+@\\S+");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d[\\d\\s().-]{5,}\\d");

    private final ProfileRepository profiles;
    private final PrivacySettingsRepository privacy;
    private final PublicCardRepository cards;
    private final EventPublisher events;

    public CardProjectorService(ProfileRepository profiles, PrivacySettingsRepository privacy,
                                PublicCardRepository cards, EventPublisher events) {
        this.profiles = profiles;
        this.privacy = privacy;
        this.cards = cards;
        this.events = events;
    }

    /** Re-projects the public card for a profile after any profile/privacy mutation. */
    @Transactional
    public void rebuild(long profileId) {
        Optional<Profile> maybeProfile = profiles.findById(profileId);
        if (maybeProfile.isEmpty()) {
            return;
        }
        Profile profile = maybeProfile.get();
        PrivacySettings settings = privacy.findByProfileId(profileId)
                .orElse(PrivacySettings.defaults(profileId));
        if (settings.visibleToEmployers()) {
            project(profile);
        } else {
            hide(profile);
        }
    }

    /** Current stored card for the internal endpoint; empty when hidden/not projected. */
    public Optional<PublicCard> current(long profileId) {
        return cards.findByProfileId(profileId);
    }

    private void project(Profile profile) {
        boolean contactReleased = cards.findByProfileId(profile.id())
                .map(PublicCard::contactReleased)
                .orElse(false);
        String displayName = mask(profile.fullName());
        String headline = scrubHeadline(profile.positionTitle());
        cards.upsert(new PublicCard(0L, profile.id(), displayName, headline,
                profile.industryCode(), profile.specializationCode(), profile.gradeDeclared(),
                profile.stack(), false, contactReleased, null));
        events.publishCardUpdated(Long.toString(profile.id()), true, displayName,
                headline, profile.industryCode(), profile.specializationCode(),
                profile.gradeDeclared(), toList(profile.stack()), false, contactReleased);
    }

    private void hide(Profile profile) {
        boolean contactReleased = cards.findByProfileId(profile.id())
                .map(PublicCard::contactReleased)
                .orElse(false);
        cards.deleteByProfileId(profile.id());
        events.publishCardUpdated(Long.toString(profile.id()), false, null, null, null, null, null,
                List.of(), false, contactReleased);
    }

    /** Deterministic mask: first name + last-name initial, e.g. «Иван Петров» → «Иван П.». */
    static String mask(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return UNNAMED;
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[1].charAt(0) + ".";
    }

    /** F7: strips email/phone patterns so user-entered contacts cannot leak into the projection. */
    static String scrubHeadline(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = EMAIL_PATTERN.matcher(value).replaceAll(" ");
        cleaned = PHONE_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    /** F6: drop null elements so {@code PUT {"stack":[null]}} cannot NPE. */
    private static List<String> toList(String[] values) {
        return values == null ? List.of() : Arrays.stream(values).filter(Objects::nonNull).toList();
    }
}
