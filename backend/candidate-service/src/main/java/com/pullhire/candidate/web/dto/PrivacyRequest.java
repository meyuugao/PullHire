package com.pullhire.candidate.web.dto;

import jakarta.validation.constraints.NotNull;

/** {@code PUT /candidate/privacy} body ({@code docs/data_model.md §4.3}). */
public record PrivacyRequest(
        @NotNull Boolean visibleToEmployers,
        @NotNull Boolean showFsp,
        @NotNull Boolean showContacts) {
}
