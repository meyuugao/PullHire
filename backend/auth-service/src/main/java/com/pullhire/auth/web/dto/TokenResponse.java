package com.pullhire.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pullhire.auth.domain.TokenPair;

/** Token pair response ({@code docs/api.md §3.1}); ids/timestamps follow §1 conventions. */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn) {

    public static TokenResponse from(TokenPair pair) {
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), pair.tokenType(), pair.expiresInSeconds());
    }
}
