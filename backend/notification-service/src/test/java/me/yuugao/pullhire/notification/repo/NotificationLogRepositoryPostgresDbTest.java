package me.yuugao.pullhire.notification.repo;

import java.time.Instant;
import java.util.UUID;

import me.yuugao.pullhire.notification.domain.NotificationLog;
import me.yuugao.pullhire.notification.domain.NotificationStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB contract of {@link NotificationLogRepository} over
 * {@code notification.notification_log} ({@code docs/data_model.md §10.1}:
 * timestamptz journal, {@code UNIQUE source_event_id} idempotency guard,
 * {@code queued → sent | failed} lifecycle).
 *
 * <p>Runs only when a PostgreSQL URL is provided via
 * {@code -Dpullhire.test.db.url} or {@code NOTIFICATION_TEST_DB_URL}; otherwise
 * the class is skipped. No Spring context, no Testcontainers: Flyway migrates
 * only {@code V1__create_notification_log}, each test truncates the table and
 * talks to {@code new NotificationLogRepository(NamedParameterJdbcTemplate)}
 * directly.
 */
class NotificationLogRepositoryPostgresDbTest {

    private static String url;

    private NotificationLogRepository repo;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("NOTIFICATION_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "NOTIFICATION_TEST_DB_URL not set: DB-backed notification-log checks skipped");
        Flyway.configure()
                .dataSource(url, System.getProperty("pullhire.test.db.user"),
                        System.getProperty("pullhire.test.db.password"))
                .schemas("notification")
                .defaultSchema("notification")
                .locations("classpath:db/migration/notification")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void reset() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE notification_log RESTART IDENTITY CASCADE");
        repo = new NotificationLogRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void insertQueuedReturnsIdWithQueuedStatus() {
        long id = repo.insertQueued(NotificationLog.queued(7L, "u7@example.com",
                "welcome", "user.registered", "evt-1"));

        assertThat(id).isPositive();
        NotificationLog row = repo.findBySourceEventId("evt-1").orElseThrow();
        assertThat(row.id()).isEqualTo(id);
        assertThat(row.recipientUserId()).isEqualTo(7L);
        assertThat(row.recipientEmail()).isEqualTo("u7@example.com");
        assertThat(row.templateCode()).isEqualTo("welcome");
        assertThat(row.eventType()).isEqualTo("user.registered");
        assertThat(row.sourceEventId()).isEqualTo("evt-1");
        assertThat(row.status()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(row.providerMessageId()).isNull();
        assertThat(row.error()).isNull();
        assertThat(row.createdAt()).isNotNull();
        assertThat(row.sentAt()).isNull();
    }

    @Test
    void findBySourceEventIdNullReturnsEmpty() {
        assertThat(repo.findBySourceEventId(null)).isEmpty();
        assertThat(repo.findBySourceEventId("missing")).isEmpty();
    }

    @Test
    void duplicateSourceEventIdViolatesUniqueConstraintButNullsDoNotCollide() {
        repo.insertQueued(NotificationLog.queued(1L, "a@example.com", "welcome", "user.registered", "evt-dup"));
        assertThatThrownBy(() -> repo.insertQueued(
                NotificationLog.queued(2L, "b@example.com", "welcome", "user.registered", "evt-dup")))
                .isInstanceOf(DuplicateKeyException.class);

        // NULL source_event_id rows are not equal under UNIQUE: both inserts succeed.
        long first = repo.insertQueued(NotificationLog.queued(3L, "c@example.com", "welcome", "user.registered", null));
        long second = repo.insertQueued(NotificationLog.queued(3L, "d@example.com", "welcome", "user.registered", null));
        assertThat(first).isPositive();
        assertThat(second).isPositive().isNotEqualTo(first);
    }

    @Test
    void markSentSetsProviderMessageIdClearsErrorAndSetsSentAt() {
        long id = repo.insertQueued(NotificationLog.queued(9L, "u9@example.com",
                "invite", "employer.invite", "evt-sent"));
        repo.markFailed(id, "smtp boom");
        Instant sentAt = Instant.parse("2026-10-01T10:00:00Z");
        repo.markSent(id, "provider-123", sentAt);

        NotificationLog row = repo.findBySourceEventId("evt-sent").orElseThrow();
        assertThat(row.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.providerMessageId()).isEqualTo("provider-123");
        assertThat(row.error()).isNull();
        assertThat(row.sentAt()).isEqualTo(sentAt);
    }

    @Test
    void markFailedRecordsErrorAndKeepsSentAtNull() {
        long id = repo.insertQueued(NotificationLog.queued(9L, "u9@example.com",
                "invite", "employer.invite", "evt-failed"));
        repo.markFailed(id, "connection refused");

        NotificationLog row = repo.findBySourceEventId("evt-failed").orElseThrow();
        assertThat(row.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(row.error()).isEqualTo("connection refused");
        assertThat(row.providerMessageId()).isNull();
        assertThat(row.sentAt()).isNull();
    }

    @Test
    void findByRecipientUserIdPagesWithOrderingAndTotal() {
        long user = 42L;
        for (int i = 0; i < 5; i++) {
            repo.insertQueued(NotificationLog.queued(user, "u" + i + "@example.com",
                    "tpl-" + i, "user.registered", "evt-page-" + i + "-" + UUID.randomUUID()));
        }
        repo.insertQueued(NotificationLog.queued(43L, "other@example.com", "tpl-x", "user.registered", "evt-other"));

        PageResult<NotificationLog> asc = repo.findByRecipientUserId(user, 0, 10, "id", true);
        assertThat(asc.total()).isEqualTo(5);
        assertThat(asc.items()).hasSize(5);
        assertThat(asc.items().stream().map(NotificationLog::id).toList()).isSorted();
        assertThat(asc.items()).allMatch(r -> r.recipientUserId() == user);

        PageResult<NotificationLog> desc = repo.findByRecipientUserId(user, 0, 10, "id", false);
        assertThat(desc.total()).isEqualTo(5);
        assertThat(desc.items().stream().map(NotificationLog::id).toList())
                .isSortedAccordingTo((a, b) -> Long.compare(b, a));

        PageResult<NotificationLog> first = repo.findByRecipientUserId(user, 0, 2, "id", true);
        PageResult<NotificationLog> second = repo.findByRecipientUserId(user, 1, 2, "id", true);
        PageResult<NotificationLog> third = repo.findByRecipientUserId(user, 2, 2, "id", true);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(2);
        assertThat(third.items()).hasSize(1);
        assertThat(first.total()).isEqualTo(5);
        assertThat(first.items().get(0).id()).isEqualTo(asc.items().get(0).id());
        assertThat(second.items().get(0).id()).isEqualTo(asc.items().get(2).id());
        assertThat(third.items().get(0).id()).isEqualTo(asc.items().get(4).id());
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
