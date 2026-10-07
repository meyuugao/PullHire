package com.pullhire.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /auth/verify-email} body ({@code docs/api.md §3.1}). */
public record VerifyEmailRequest(
        @NotBlank String token) {
}
