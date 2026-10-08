package me.yuugao.pullhire.assessment.repo;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import me.yuugao.pullhire.assessment.domain.GradeDirection;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository.GradeHistoryEntry;
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
 * DB-backed checks for {@link GradeHistoryRepository} against the
 * {@code assessment} schema ({@code docs/data_model.md §6.1},
 * {@code docs/testing_model.md §7.1}): an initial/up/down/recheck append
 * round-trips and lists oldest-first via the btree
 * {@code (candidate_id, created_at)}; the table is append-only (no UNIQUE, no
 * update/delete API).
 *
 * <p>Runs only when a PostgreSQL URL is provided via
 * {@code -Dpullhire.test.db.url} or {@code ASSESSMENT_TEST_DB_URL} (compose/pg
 * instance); otherwise the class is skipped, so CI without a live database stays
 * green. No extra dependency is introduced.
 */
class GradeHistoryPostgresDbTest {

    private static String url;
    private JdbcTemplate jdbc;
    private GradeHistoryRepository history;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("ASSESSMENT_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "ASSESSMENT_TEST_DB_URL not set: DB-backed grade-history checks skipped");
        Flyway.configure()
                .dataSource(url, System.getProperty("pullhire.test.db.user"),
                        System.getProperty("pullhire.test.db.password"))
                .schemas("assessment")
                .defaultSchema("assessment")
                .locations("classpath:db/migration/assessment")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void reset() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE grade_history, test_sessions RESTART IDENTITY CASCADE");
        history = new GradeHistoryRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void appendsInitialUpDownRecheckAndListsThemOldestFirst() {
        long candidate = 42L;
        history.append(candidate, "junior", "spec-1", GradeDirection.INITIAL, null, null, "onboarding",
                Instant.parse("2026-01-01T00:00:00Z"));
        history.append(candidate, "middle", "spec-1", GradeDirection.UP, null, "cat-1", "promotion",
                Instant.parse("2026-02-01T00:00:00Z"));
        history.append(candidate, "junior", "spec-1", GradeDirection.DOWN, null, "cat-1", "candidate request",
                Instant.parse("2026-03-01T00:00:00Z"));
        history.append(candidate, "junior", "spec-1", GradeDirection.RECHECK, null, "cat-2", "re-evaluation",
                Instant.parse("2026-04-01T00:00:00Z"));

        history.append(43L, "senior", "spec-1", GradeDirection.INITIAL, null, null, "other candidate",
                Instant.parse("2026-01-15T00:00:00Z"));

        List<GradeHistoryEntry> entries = history.listByCandidate(candidate);
        assertThat(entries).hasSize(4);
        assertThat(entries).extracting(GradeHistoryEntry::direction)
                .containsExactly(GradeDirection.INITIAL, GradeDirection.UP,
                        GradeDirection.DOWN, GradeDirection.RECHECK);
        assertThat(entries).extracting(GradeHistoryEntry::createdAt)
                .containsExactly(Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-02-01T00:00:00Z"),
                        Instant.parse("2026-03-01T00:00:00Z"),
                        Instant.parse("2026-04-01T00:00:00Z"));
        assertThat(entries).extracting(GradeHistoryEntry::grade)
                .containsExactly("junior", "middle", "junior", "junior");
        assertThat(entries).extracting(GradeHistoryEntry::candidateId).containsOnly(candidate);
        assertThat(entries.get(1).specializationCode()).isEqualTo("spec-1");
        assertThat(entries.get(1).categoryCode()).isEqualTo("cat-1");
        assertThat(entries.get(1).reason()).isEqualTo("promotion");
        assertThat(entries).extracting(GradeHistoryEntry::id).allMatch(id -> id > 0);
    }

    @Test
    void appendOnlyAllowsRepeatedRowsAndExposesNoMutationApi() {
        long candidate = 42L;
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        history.append(candidate, "junior", "spec-1", GradeDirection.INITIAL, null, null, "first", at);
        history.append(candidate, "junior", "spec-1", GradeDirection.INITIAL, null, null, "first", at);

        // No UNIQUE constraint: identical rows are both retained (append-only).
        assertThat(history.listByCandidate(candidate)).hasSize(2);

        assertThat(GradeHistoryRepository.class.getDeclaredMethods())
                .extracting(Method::getName)
                .noneMatch(name -> name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("remove")
                        || name.startsWith("save"));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
