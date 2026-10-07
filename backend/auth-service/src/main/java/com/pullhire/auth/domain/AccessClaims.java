package com.pullhire.auth.domain;

/** Validated access-token claims extracted locally from an RS256 JWT. */
public record AccessClaims(
        long userId,
        Role role,
        String jti,
        java.time.Instant expiresAt) {
}
