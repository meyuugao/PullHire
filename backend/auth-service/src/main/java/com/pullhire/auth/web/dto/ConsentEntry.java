package com.pullhire.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pullhire.auth.domain.ConsentCommand;
import com.pullhire.auth.domain.ConsentType;
import jakarta.validation.constraints.Pattern;

/**
 * One consent entry in {@code GET|PUT /auth/consents} ({@code docs/data_model.md §3.4}).
 * {@code policy_version} is optional on input and defaults to {@code 1}.
 */
public record ConsentEntry(
        @Pattern(regexp = "data_processing|profile_publication|fsp_link|task_participation")
        @JsonProperty("consent_type") String consentType,
        boolean granted,
        @JsonProperty("policy_version") String policyVersion) {

    public ConsentCommand toCommand() {
        String version = (policyVersion == null || policyVersion.isBlank()) ? "1" : policyVersion;
        return new ConsentCommand(ConsentType.fromWire(consentType), granted, version);
    }
}
