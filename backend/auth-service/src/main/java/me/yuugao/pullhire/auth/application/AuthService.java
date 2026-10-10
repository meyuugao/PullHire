package me.yuugao.pullhire.auth.application;

import me.yuugao.pullhire.auth.domain.ConsentCommand;
import me.yuugao.pullhire.auth.domain.EmailToken;
import me.yuugao.pullhire.auth.domain.Role;
import me.yuugao.pullhire.auth.domain.TokenPair;
import me.yuugao.pullhire.auth.domain.User;
import me.yuugao.pullhire.auth.domain.UserStatus;
import me.yuugao.pullhire.auth.error.ApiException;
import me.yuugao.pullhire.auth.repo.EmailTokenRepository;
import me.yuugao.pullhire.auth.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Email verification link lifetime (§4 roles.md): 24 hours. */
    private static final Duration EMAIL_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository users;
    private final EmailTokenRepository emailTokens;
    private final ConsentService consentService;
    private final TokenService tokens;
    private final EventPublisher events;
    private final PasswordEncoder passwordEncoder;
    private final String appBaseUrl;

    public AuthService(UserRepository users, EmailTokenRepository emailTokens, ConsentService consentService,
                       TokenService tokens, EventPublisher events, PasswordEncoder passwordEncoder,
                       @Value("${app.base-url}") String appBaseUrl) {
        this.users = users;
        this.emailTokens = emailTokens;
        this.consentService = consentService;
        this.tokens = tokens;
        this.events = events;
        this.passwordEncoder = passwordEncoder;
        this.appBaseUrl = appBaseUrl;
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
        // OQ-13: render the full link here; the raw token never enters the event.
        String verificationUrl = appBaseUrl + "/verify-email?token=" + rawToken;
        events.publishUserRegistered(userId, normalizedEmail, role, verificationUrl, requestId);
        // Success marker for the chain: MDC correlation_id comes from CorrelationMdcFilter.
        // Never log the raw token or password; email is intentionally omitted (PII).
        log.info("Регистрация принята, user.registered помещён в outbox: user_id={}, role={}",
                userId, role.wire());

        return users.findById(userId).orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }

    /** Consumes a verification token: valid + unused + unexpired → activate the account. */
    @Transactional
    public User verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.badRequest("token обязателен");
        }
        EmailToken token = emailTokens.findByHash(TokenService.hashToken(rawToken))
                .orElseThrow(() -> ApiException.badRequest("Токен подтверждения не найден"));
        if (token.usedAt() != null) {
            throw ApiException.badRequest("Токен уже использован");
        }
        if (token.expiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Срок действия токена истёк");
        }

        emailTokens.markUsed(token.id());
        users.markEmailVerified(token.userId());
        return users.findById(token.userId()).orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }

    /** Password login; only active, non-deleted accounts succeed, otherwise 401. */
    public TokenPair login(String email, String password) {
        User user = users.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> ApiException.unauthorized("Неверный email или пароль"));
        if (user.deletedAt() != null || user.status() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("Неверный email или пароль");
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw ApiException.unauthorized("Неверный email или пароль");
        }
        return tokens.issueTokenPair(user);
    }

    /** Rotates the refresh token and issues a new access + refresh pair. */
    public TokenPair refresh(String rawRefreshToken) {
        return tokens.rotateRefreshToken(rawRefreshToken);
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            throw ApiException.badRequest("email обязателен");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
