package me.yuugao.pullhire.auth.domain;

/** Access + refresh pair issued on login and on every successful refresh rotation. */
public record TokenPair(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds) {
}
