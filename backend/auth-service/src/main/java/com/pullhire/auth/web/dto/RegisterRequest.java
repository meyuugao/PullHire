package com.pullhire.auth.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code POST /auth/register} body ({@code docs/api.md §3.1}). */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Pattern(regexp = "candidate|employer") String role,
        @Valid List<ConsentEntry> consents) {
}
