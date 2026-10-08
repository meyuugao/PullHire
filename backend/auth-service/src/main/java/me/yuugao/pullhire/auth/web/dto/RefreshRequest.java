package me.yuugao.pullhire.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** {@code POST /auth/refresh} body ({@code docs/api.md §3.1}). */
public record RefreshRequest(
        @NotBlank @JsonProperty("refresh_token") String refreshToken) {
}
