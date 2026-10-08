package me.yuugao.pullhire.candidate.domain;

import java.time.Instant;

/**
 * Row of {@code candidate.privacy_settings} ({@code docs/data_model.md §4.3}).
 * 1:1 with {@link Profile}; the owner controls employer visibility.
 */
public record PrivacySettings(
        long profileId,
        boolean visibleToEmployers,
        boolean showFsp,
        boolean showContacts,
        Instant updatedAt) {

    /** Default visibility before the candidate stores anything ({@code docs/data_model.md §4.3}). */
    public static PrivacySettings defaults(long profileId) {
        return new PrivacySettings(profileId, true, true, false, null);
    }
}
