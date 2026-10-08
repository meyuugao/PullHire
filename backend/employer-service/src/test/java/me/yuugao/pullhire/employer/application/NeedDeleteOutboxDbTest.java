package me.yuugao.pullhire.employer.application;

import java.util.Map;

import me.yuugao.pullhire.employer.repo.CompanyRepository;
import me.yuugao.pullhire.employer.repo.NeedRepository;
import me.yuugao.pullhire.events.EventNames;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import me.yuugao.pullhire.events.OutboxWriter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB-backed delete → outbox atomicity ({@code docs/architecture.md §4.3}):
 * the real repositories and the real {@link OutboxWriter} run against
 * PostgreSQL — {@code delete} removes the need row <i>and</i> leaves an
 * {@code employer.need_deleted} outbox row with the string-identifier payload;
 * a forced rollback discards both.
 *
 * <p>Runs only when a PostgreSQL URL is provided via
 * {@code -Dpullhire.test.db.url} or {@code EMPLOYER_TEST_DB_URL}; otherwise
 * the class is skipped (same mechanism as {@code AssessmentFlowTest}).
 */
class NeedDeleteOutboxDbTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static String url;

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private NeedService service;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("EMPLOYER_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "EMPLOYER_TEST_DB_URL not set: DB-backed delete checks skipped");
        Flyway.configure()
                .dataSource(url, System.getProperty("pullhire.test.db.user"),
                        System.getProperty("pullhire.test.db.password"))
                .schemas("employer")
                .defaultSchema("employer")
                .locations("classpath:db/migration/employer")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void reset() {
        dataSource = new DriverManagerDataSource(url);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE companies, needs, outbox_events RESTART IDENTITY CASCADE");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        service = new NeedService(new CompanyRepository(named), new NeedRepository(named),
                new EventPublisher(new OutboxEventPublisher(new OutboxWriter(jdbc))));
    }

    @Test
    void deleteRemovesRowAndWritesNeedDeletedOutboxRow() {
        long companyId = insertCompany(1L);
        long needId = insertNeed(companyId);

        service.delete(1L, needId);

        assertThat(needCount(needId)).isZero();
        Map<String, Object> row = outboxRow(needId);
        assertThat(row.get("event_type")).isEqualTo(EventNames.EMPLOYER_NEED_DELETED);
        assertThat(row.get("producer")).isEqualTo("employer-service");
        assertThat(payload(row)).containsExactlyEntriesOf(Map.of(
                "id", Long.toString(needId),
                "company_id", Long.toString(companyId),
                "user_id", "1"));
    }

    @Test
    void forcedRollbackKeepsRowAndWritesNoOutboxRow() {
        long companyId = insertCompany(1L);
        long needId = insertNeed(companyId);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        tx.executeWithoutResult(status -> {
            service.delete(1L, needId);
            status.setRollbackOnly();
        });

        assertThat(needCount(needId)).isEqualTo(1L);
        assertThat(outboxCount()).isZero();
    }

    private long insertCompany(long userId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO companies (user_id, name) VALUES (?, ?) RETURNING id",
                Long.class, userId, "Acme");
        assertThat(id).isNotNull();
        return id;
    }

    private long insertNeed(long companyId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO needs (company_id, title, specialization_code, grade, status)"
                        + " VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, companyId, "Backend", "backend", "middle", "active");
        assertThat(id).isNotNull();
        return id;
    }

    private long needCount(long needId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM needs WHERE id = ?", Long.class, needId);
        return count == null ? 0L : count;
    }

    private long outboxCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class);
        return count == null ? 0L : count;
    }

    private Map<String, Object> outboxRow(long needId) {
        return jdbc.queryForMap(
                "SELECT event_type, producer, payload::text AS payload FROM outbox_events"
                        + " WHERE event_type = ? AND payload->>'id' = ?",
                EventNames.EMPLOYER_NEED_DELETED, Long.toString(needId));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> row) {
        return JSON.readValue(String.valueOf(row.get("payload")), Map.class);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
