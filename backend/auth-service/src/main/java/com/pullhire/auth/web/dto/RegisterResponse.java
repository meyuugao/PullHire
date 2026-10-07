package com.pullhire.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pullhire.auth.domain.User;

/** {@code POST /auth/register} response: the freshly created pending account. */
public record RegisterResponse(
        @JsonProperty("user_id") String userId,
        String email,
        String role,
        String status) {

    public static RegisterResponse from(User user) {
        return new RegisterResponse(Long.toString(user.id()), user.email(), user.role().wire(), user.status().wire());
    }
}
