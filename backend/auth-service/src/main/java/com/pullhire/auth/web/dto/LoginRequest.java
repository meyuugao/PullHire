package com.pullhire.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** {@code POST /auth/login} body ({@code docs/api.md §3.1}). */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
