package com.pullhire.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pullhire.auth.domain.User;

/** {@code GET /auth/me} response ({@code docs/api.md §3.1}): id, email, role, status. */
public record MeResponse(
        String id,
        String email,
        String role,
        String status) {

    public static MeResponse from(User user) {
        return new MeResponse(Long.toString(user.id()), user.email(), user.role().wire(), user.status().wire());
    }
}
