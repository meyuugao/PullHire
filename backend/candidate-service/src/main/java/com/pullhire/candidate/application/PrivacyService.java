package com.pullhire.candidate.application;

import com.pullhire.candidate.domain.PrivacySettings;
import com.pullhire.candidate.domain.Profile;
import com.pullhire.candidate.error.ApiException;
import com.pullhire.candidate.repo.PrivacySettingsRepository;
import com.pullhire.candidate.repo.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visibility settings ({@code docs/api.md §3.2}, {@code docs/data_model.md §4.3}).
 * A first GET returns defaults without persisting; PUT upserts and re-projects the
 * public card so the employer-visible view follows the flag immediately.
 */
@Service
public class PrivacyService {

    private final ProfileRepository profiles;
    private final PrivacySettingsRepository privacy;
    private final CardProjectorService cards;

    public PrivacyService(ProfileRepository profiles, PrivacySettingsRepository privacy,
                          CardProjectorService cards) {
        this.profiles = profiles;
        this.privacy = privacy;
        this.cards = cards;
    }

    /** Current settings, or the documented defaults when nothing is stored yet. */
    public PrivacySettings get(long userId) {
        long profileId = requireProfileId(userId);
        return privacy.findByProfileId(profileId).orElse(PrivacySettings.defaults(profileId));
    }

    @Transactional
    public PrivacySettings update(long userId, boolean visibleToEmployers, boolean showFsp, boolean showContacts) {
        long profileId = requireProfileId(userId);
        PrivacySettings settings =
                new PrivacySettings(profileId, visibleToEmployers, showFsp, showContacts, null);
        privacy.upsert(settings);
        cards.rebuild(profileId);
        return privacy.findByProfileId(profileId).orElse(settings);
    }

    private long requireProfileId(long userId) {
        return profiles.findByUserId(userId)
                .map(Profile::id)
                .orElseThrow(() -> ApiException.notFound("Профиль не найден"));
    }
}
