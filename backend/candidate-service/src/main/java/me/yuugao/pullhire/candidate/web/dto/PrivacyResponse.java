package me.yuugao.pullhire.candidate.web.dto;

import me.yuugao.pullhire.candidate.domain.PrivacySettings;

/** {@code GET|PUT /candidate/privacy} response ({@code docs/api.md §3.2}). */
public record PrivacyResponse(
        boolean visibleToEmployers,
        boolean showFsp,
        boolean showContacts) {

    public static PrivacyResponse from(PrivacySettings settings) {
        return new PrivacyResponse(settings.visibleToEmployers(), settings.showFsp(), settings.showContacts());
    }
}
