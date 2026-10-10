package me.yuugao.pullhire.auth.application;

import me.yuugao.pullhire.auth.config.JwtProperties;
import me.yuugao.pullhire.auth.config.RsaKeyProvider;
import me.yuugao.pullhire.auth.domain.AccessClaims;
import me.yuugao.pullhire.auth.domain.RefreshToken;
import me.yuugao.pullhire.auth.domain.Role;
import me.yuugao.pullhire.auth.domain.TokenPair;
import me.yuugao.pullhire.auth.domain.User;
import me.yuugao.pullhire.auth.domain.UserStatus;
import me.yuugao.pullhire.auth.error.ApiException;
import me.yuugao.pullhire.auth.repo.RefreshTokenRepository;
import me.yuugao.pullhire.auth.repo.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RS256 token mechanics ({@code docs/architecture.md §6.1}). Access tokens are
 * self-contained and verified locally; refresh tokens are opaque, stored only as
 * SHA-256 hashes, and rotated on every use.
 */
@Service
public class TokenService {

    public static final String ISSUER = "pullhire-auth";
    private static final String ROLES_CLAIM = "realm_access";
    private static final String ROLES_KEY = "roles";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RsaKeyProvider keys;
    private final JwtProperties properties;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;

    public TokenService(RsaKeyProvider keys, JwtProperties properties,
                        RefreshTokenRepository refreshTokens, UserRepository users) {
        this.keys = keys;
        this.properties = properties;
        this.refreshTokens = refreshTokens;
        this.users = users;
    }

    /** Builds a signed access JWT; {@code sub}=user id, roles in {@code realm_access.roles}. */
    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(user.id()))
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTtl())))
                .id(UUID.randomUUID().toString())
                .claim(ROLES_CLAIM, Map.of(ROLES_KEY, List.of(user.role().wire())))
                .signWith(keys.privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /** Verifies signature/issuer/expiry locally and extracts claims; throws 401 on failure. */
    public AccessClaims verifyAccessToken(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(keys.publicKey())
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();
            return new AccessClaims(
                    Long.parseLong(claims.getSubject()),
                    roleFromClaims(claims),
                    claims.getId(),
                    claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ex) {
            throw ApiException.unauthorized("Невалидный или истёкший токен");
        }
    }

    /** Generates an opaque refresh token, stores its hash, and returns the raw value. */
    public String issueRefreshToken(long userId) {
        String raw = generateRawToken();
        refreshTokens.insert(userId, hashToken(raw), Instant.now().plus(properties.getRefreshTtl()));
        return raw;
    }

    /** Issues a fresh access + refresh pair for an authenticated user. */
    public TokenPair issueTokenPair(User user) {
        String access = issueAccessToken(user);
        String refresh = issueRefreshToken(user.id());
        return new TokenPair(access, refresh, "Bearer", properties.getAccessTtl().toSeconds());
    }

    /** Rotates a refresh token: revokes the consumed one and issues a new pair. */
    public TokenPair rotateRefreshToken(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            throw ApiException.unauthorized("refresh-токен обязателен");
        }
        RefreshToken stored = refreshTokens.findByHash(hashToken(rawRefresh))
                .orElseThrow(() -> ApiException.unauthorized("refresh-токен не найден"));
        if (stored.revokedAt() != null || stored.expiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("refresh-токен отозван или истёк");
        }
        refreshTokens.revoke(stored.id());
        User user = users.findById(stored.userId())
                .orElseThrow(() -> ApiException.unauthorized("Пользователь не найден"));
        if (user.deletedAt() != null || user.status() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("Учётная запись недоступна");
        }
        return issueTokenPair(user);
    }

    private static Role roleFromClaims(Claims claims) {
        Object realm = claims.get(ROLES_CLAIM);
        if (realm instanceof Map<?, ?> map) {
            Object roles = map.get(ROLES_KEY);
            if (roles instanceof List<?> list && !list.isEmpty()) {
                return Role.fromWire(String.valueOf(list.get(0)));
            }
        }
        throw ApiException.unauthorized("В токене отсутствует роль");
    }

    public static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashToken(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 недоступен в JVM", ex);
        }
    }
}
