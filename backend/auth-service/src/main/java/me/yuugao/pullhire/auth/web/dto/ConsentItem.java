package me.yuugao.pullhire.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** One current consent row in {@code GET|PUT /auth/consents} responses. */
public record ConsentItem(
        @JsonProperty("consent_type") String consentType,
        boolean granted,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("granted_at") Instant grantedAt,
        @JsonProperty("revoked_at") Instant revokedAt) {
}
