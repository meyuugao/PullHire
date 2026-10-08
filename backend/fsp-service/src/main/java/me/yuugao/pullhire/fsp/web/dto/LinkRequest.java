package me.yuugao.pullhire.fsp.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /candidate/fsp/link} body ({@code docs/api.md §3.3}).
 *
 * <p>The candidate id is taken from the JWT, not the body; {@code fsp_id} is the
 * external FSP participant id (MVP accepts it directly, no cross-service call,
 * {@code docs/fsp_integration.md §2}).
 */
public record LinkRequest(
        @JsonProperty("fsp_id") @NotBlank String fspId) {
}
