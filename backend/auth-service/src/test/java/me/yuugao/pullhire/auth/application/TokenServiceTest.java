package me.yuugao.pullhire.auth.application;

import me.yuugao.pullhire.auth.config.JwtProperties;
import me.yuugao.pullhire.auth.config.RsaKeyProvider;
import me.yuugao.pullhire.auth.domain.AccessClaims;
import me.yuugao.pullhire.auth.domain.Role;
import me.yuugao.pullhire.auth.domain.User;
import me.yuugao.pullhire.auth.domain.UserStatus;
import me.yuugao.pullhire.auth.error.ApiException;
import me.yuugao.pullhire.auth.repo.RefreshTokenRepository;
import me.yuugao.pullhire.auth.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** TokenService semantics without any external infrastructure (generated RSA keys). */
class TokenServiceTest {

    private JwtProperties properties;
    private TokenService service;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setAccessTtl(Duration.ofMinutes(15));
        properties.setRefreshTtl(Duration.ofDays(30));
        service = new TokenService(
                new RsaKeyProvider(null, null),
                properties,
                mock(RefreshTokenRepository.class),
                mock(UserRepository.class));
    }

    private static User user() {
        Instant now = Instant.now();
        return new User(1L, "user@example.com", "hash", Role.CANDIDATE, UserStatus.ACTIVE, now, null, now, now);
    }

    @Test
    void issuesAndVerifiesAccessToken() {
        String token = service.issueAccessToken(user());

        AccessClaims claims = service.verifyAccessToken(token);

        assertThat(claims.userId()).isEqualTo(1L);
        assertThat(claims.role()).isEqualTo(Role.CANDIDATE);
        assertThat(claims.jti()).isNotBlank();
        assertThat(claims.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void rejectsExpiredAccessToken() {
        properties.setAccessTtl(Duration.ofMinutes(-1));
        String expired = service.issueAccessToken(user());

        assertThatThrownBy(() -> service.verifyAccessToken(expired))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("unauthenticated"));
    }

    @Test
    void rejectsTokenSignedByForeignKey() {
        TokenService foreign = new TokenService(
                new RsaKeyProvider(null, null),
                properties,
                mock(RefreshTokenRepository.class),
                mock(UserRepository.class));
        String foreignToken = foreign.issueAccessToken(user());

        assertThatThrownBy(() -> service.verifyAccessToken(foreignToken))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> service.verifyAccessToken("not.a.jwt"))
                .isInstanceOf(ApiException.class);
    }
}
