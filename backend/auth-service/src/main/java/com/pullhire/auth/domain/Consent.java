package com.pullhire.auth.domain;

import java.time.Instant;

/** Row of {@code auth.consents} ({@code docs/data_model.md §3.4}). */
public record Consent(
        long id,
        long userId,
        ConsentType consentType,
        boolean granted,
        String policyVersion,
        Instant grantedAt,
        Instant revokedAt,
        Instant createdAt) {
}
