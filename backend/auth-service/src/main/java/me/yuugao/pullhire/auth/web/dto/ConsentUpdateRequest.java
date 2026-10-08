package me.yuugao.pullhire.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** {@code PUT /auth/consents} body: a set of consent changes to append. */
public record ConsentUpdateRequest(
        @NotNull @Valid @JsonProperty("consents") List<ConsentEntry> consents) {
}
