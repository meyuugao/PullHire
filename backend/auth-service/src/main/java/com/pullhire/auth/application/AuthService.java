package com.pullhire.auth.application;

import com.pullhire.auth.domain.ConsentCommand;
import com.pullhire.auth.domain.EmailToken;
import com.pullhire.auth.domain.Role;
import com.pullhire.auth.domain.TokenPair;
import com.pullhire.auth.domain.User;
import com.pullhire.auth.domain.UserStatus;
import com.pullhire.auth.error.ApiException;
import com.pullhire.auth.repo.EmailTokenRepository;
import com.pullhire.auth.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Identity lifecycle for S1 ({@code docs/api.md §3.1}): registration, email
 * verification, login and refresh. Role is fixed at registration and never changes.
 */
@Service
public class AuthService {

    /** Email verification link lifetime (§4 roles.md): 24 hours. */
    private static final Duration EMAIL_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository users;
    private final EmailTokenRepository emailTokens;
    private final ConsentService consentService;
    private final TokenService tokens;
    private final EventPublisher events;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, EmailTokenRepository emailTokens, ConsentService consentService,
                       TokenService tokens, EventPublisher events, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.emailTokens = emailTokens;
        this.consentService = consentService;
        this.tokens = tokens;
        this.events = events;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a {@code pending} user. Duplicate email → 409. Issues a one-time email
     * token (hash only) valid for 24h and publishes {@code user.registered}.
     */
    @Transactional
    public User register(String email, String password, Role role, List<ConsentCommand> consents, String requestId) {
        String normalizedEmail = normalizeEmail(email);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw ApiException.conflict("Email уже зарегистрирован");
        }

        long userId = users.insert(normalizedEmail, passwordEncoder.encode(password), role);

        if (consents != null && !consents.isEmpty()) {
            consentService.append(userId, consents);
        }

        String rawToken = TokenService.generateRawToken();
        emailTokens.insert(userId, TokenService.hashToken(rawToken), Instant.now().plus(EMAIL_TOKEN_TTL));
        events.publishUserRegistered(userId, normalizedEmail, role, rawToken, requestId);

        return users.findById(userId).orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }

    /** Consumes a verification token: valid + unused + unexpired → activate the account. */
    @Transactional
    public User verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.validation("token обязателен");
        }
        EmailToken token = emailTokens.findByHash(TokenService.hashToken(rawToken))
                .orElseThrow(() -> ApiException.validation("Токен подтверждения не найден"));
        if (token.usedAt() != null) {
            throw ApiException.validation("Токен уже использован");
        }
        if (token.expiresAt().isBefore(Instant.now())) {
            throw ApiException.validation("Срок действия токена истёк");
        }

        emailTokens.markUsed(token.id());
        users.markEmailVerified(token.userId());
        return users.findById(token.userId()).orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }

    /** Password login; only active, non-deleted accounts succeed, otherwise 401. */
    public TokenPair login(String email, String password) {
        User user = users.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> ApiException.unauthenticated("Неверный email или пароль"));
        if (user.deletedAt() != null || user.status() != UserStatus.ACTIVE) {
            throw ApiException.unauthenticated("Неверный email или пароль");
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw ApiException.unauthenticated("Неверный email или пароль");
        }
        return tokens.issueTokenPair(user);
    }

    /** Rotates the refresh token and issues a new access + refresh pair. */
    public TokenPair refresh(String rawRefreshToken) {
        return tokens.rotateRefreshToken(rawRefreshToken);
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            throw ApiException.validation("email обязателен");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
