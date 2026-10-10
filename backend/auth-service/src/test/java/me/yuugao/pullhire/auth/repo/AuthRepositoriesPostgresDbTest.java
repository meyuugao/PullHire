package me.yuugao.pullhire.auth.repo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import me.yuugao.pullhire.auth.domain.Consent;
import me.yuugao.pullhire.auth.domain.ConsentType;
import me.yuugao.pullhire.auth.domain.EmailToken;
import me.yuugao.pullhire.auth.domain.RefreshToken;
import me.yuugao.pullhire.auth.domain.Role;
import me.yuugao.pullhire.auth.domain.User;
import me.yuugao.pullhire.auth.domain.UserStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aggregate DB-backed checks for the auth repositories
 * ({@code docs/data_model.md §3}: {@code auth.users} 9 columns, email/refresh
 * tokens, append-only consents with {@code DISTINCT ON} latest state).
 *
 * <p>Runs only when a PostgreSQL URL is provided via
 * {@code -Dpullhire.test.db.url} or {@code AUTH_TEST_DB_URL}; otherwise
 * the class is skipped. Repositories are constructed directly (no Spring
 * context, no Testcontainers).
 */
class AuthRepositoriesPostgresDbTest {

    private static String url;

    private UserRepository users;
    private RefreshTokenRepository refreshTokens;
    private EmailTokenRepository emailTokens;
    private ConsentRepository consents;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("AUTH_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "AUTH_TEST_DB_URL not set: DB-backed auth checks skipped");
        Flyway.configure()
                .dataSource(url, System.getProperty("pullhire.test.db.user"),
                        System.getProperty("pullhire.test.db.password"))
                .schemas("auth")
                .defaultSchema("auth")
                .locations("classpath:db/migration/auth")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void reset() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE users, refresh_tokens, email_tokens, consents, idempotency_keys "
                + "RESTART IDENTITY CASCADE");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        users = new UserRepository(named);
        refreshTokens = new RefreshTokenRepository(named);
        emailTokens = new EmailTokenRepository(named);
        consents = new ConsentRepository(named);
    }

    @Test
    void userInsertFindAndVerify() {
        long id = users.insert("ivan@example.com", "hash-1", Role.CANDIDATE);
        assertThat(id).isPositive();

        User byEmail = users.findByEmail("ivan@example.com").orElseThrow();
        assertThat(byEmail.id()).isEqualTo(id);
        assertThat(byEmail.email()).isEqualTo("ivan@example.com");
        assertThat(byEmail.passwordHash()).isEqualTo("hash-1");
        assertThat(byEmail.role()).isEqualTo(Role.CANDIDATE);
        assertThat(byEmail.status()).isEqualTo(UserStatus.PENDING);
        assertThat(byEmail.emailVerifiedAt()).isNull();
        assertThat(byEmail.deletedAt()).isNull();
        assertThat(byEmail.createdAt()).isNotNull();
        assertThat(byEmail.updatedAt()).isNotNull();

        User byId = users.findById(id).orElseThrow();
        assertThat(byId.email()).isEqualTo("ivan@example.com");
        assertThat(users.findByEmail("missing@example.com")).isEmpty();

        users.markEmailVerified(id);
        User verified = users.findById(id).orElseThrow();
        assertThat(verified.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(verified.emailVerifiedAt()).isNotNull();
    }

    @Test
    void refreshTokenInsertFindAndRevoke() {
        long userId = users.insert("refresh@example.com", "hash-1", Role.CANDIDATE);
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        long tokenId = refreshTokens.insert(userId, "rt-hash-1", expiresAt);
        assertThat(tokenId).isPositive();

        RefreshToken found = refreshTokens.findByHash("rt-hash-1").orElseThrow();
        assertThat(found.id()).isEqualTo(tokenId);
        assertThat(found.userId()).isEqualTo(userId);
        assertThat(found.expiresAt()).isEqualTo(expiresAt.truncatedTo(ChronoUnit.MILLIS));
        assertThat(found.revokedAt()).isNull();
        assertThat(refreshTokens.findByHash("rt-missing")).isEmpty();

        refreshTokens.revoke(tokenId);
        RefreshToken revoked = refreshTokens.findByHash("rt-hash-1").orElseThrow();
        assertThat(revoked.revokedAt()).isNotNull();
    }

    @Test
    void emailTokenInsertFindAndMarkUsed() {
        long userId = users.insert("mail@example.com", "hash-1", Role.CANDIDATE);
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        long tokenId = emailTokens.insert(userId, "et-hash-1", expiresAt);
        assertThat(tokenId).isPositive();

        EmailToken found = emailTokens.findByHash("et-hash-1").orElseThrow();
        assertThat(found.id()).isEqualTo(tokenId);
        assertThat(found.userId()).isEqualTo(userId);
        assertThat(found.purpose()).isEqualTo("verify_email");
        assertThat(found.expiresAt()).isEqualTo(expiresAt.truncatedTo(ChronoUnit.MILLIS));
        assertThat(found.usedAt()).isNull();
        assertThat(emailTokens.findByHash("et-missing")).isEmpty();

        emailTokens.markUsed(tokenId);
        EmailToken used = emailTokens.findByHash("et-hash-1").orElseThrow();
        assertThat(used.usedAt()).isNotNull();
    }

    @Test
    void consentGrantedAndRevokedBranches() {
        long userId = users.insert("consent@example.com", "hash-1", Role.CANDIDATE);

        consents.insert(userId, ConsentType.DATA_PROCESSING, true, "v1");
        List<Consent> granted = consents.findLatestByUser(userId);
        assertThat(granted).hasSize(1);
        Consent grant = granted.get(0);
        assertThat(grant.consentType()).isEqualTo(ConsentType.DATA_PROCESSING);
        assertThat(grant.granted()).isTrue();
        assertThat(grant.policyVersion()).isEqualTo("v1");
        assertThat(grant.grantedAt()).isNotNull();
        assertThat(grant.revokedAt()).isNull();

        consents.insert(userId, ConsentType.PROFILE_PUBLICATION, false, "v1");
        List<Consent> both = consents.findLatestByUser(userId);
        assertThat(both).hasSize(2);
        Consent denied = both.stream()
                .filter(c -> c.consentType() == ConsentType.PROFILE_PUBLICATION)
                .findFirst().orElseThrow();
        assertThat(denied.granted()).isFalse();
        assertThat(denied.grantedAt()).isNull();
        assertThat(denied.revokedAt()).isNotNull();
    }

    @Test
    void consentLatestPerTypeDistinctOn() {
        long userId = users.insert("latest@example.com", "hash-1", Role.CANDIDATE);

        consents.insert(userId, ConsentType.DATA_PROCESSING, true, "v1");
        consents.insert(userId, ConsentType.DATA_PROCESSING, false, "v2");
        List<Consent> latest = consents.findLatestByUser(userId);
        assertThat(latest).hasSize(1);
        assertThat(latest.get(0).granted()).isFalse();
        assertThat(latest.get(0).policyVersion()).isEqualTo("v2");

        consents.insert(userId, ConsentType.FSP_LINK, true, "v1");
        List<Consent> twoTypes = consents.findLatestByUser(userId);
        assertThat(twoTypes).hasSize(2);
        assertThat(twoTypes.stream().map(Consent::consentType))
                .containsExactlyInAnyOrder(ConsentType.DATA_PROCESSING, ConsentType.FSP_LINK);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
