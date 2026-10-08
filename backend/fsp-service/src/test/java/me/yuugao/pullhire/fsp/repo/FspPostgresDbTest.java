package me.yuugao.pullhire.fsp.repo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import me.yuugao.pullhire.fsp.adapter.FspRegistryAdapter;
import me.yuugao.pullhire.fsp.adapter.RegistryAchievement;
import me.yuugao.pullhire.fsp.adapter.RegistryHistory;
import me.yuugao.pullhire.fsp.application.EventPublisher;
import me.yuugao.pullhire.fsp.application.FspLinkService;
import me.yuugao.pullhire.fsp.domain.FspLink;
import me.yuugao.pullhire.fsp.domain.HistoryStatus;
import me.yuugao.pullhire.fsp.domain.LinkStatus;
import me.yuugao.pullhire.fsp.error.ApiException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DB-backed repository/integration checks for the fsp slice: schema migrations,
 * repeated-link idempotency, the {@code UNIQUE (fsp_id)} violation feeding the 409
 * path, unlink no-op and the synthetic seed ({@code docs/fsp_integration.md §4/§6}).
 *
 * <p>Runs only when a PostgreSQL URL is provided via {@code -Dpullhire.test.db.url}
 * or {@code FSP_TEST_DB_URL} (compose/pg instance); otherwise the class is skipped,
 * so CI without a live database stays green. No extra dependency is introduced.
 */
class FspPostgresDbTest {

    private static String url;
    private JdbcTemplate jdbc;
    private FspLinkRepository links;
    private AchievementCacheRepository achievements;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("FSP_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "FSP_TEST_DB_URL not set: DB-backed fsp checks skipped");
        Flyway.configure()
                .dataSource(url, System.getProperty("pullhire.test.db.user"),
                        System.getProperty("pullhire.test.db.password"))
                .schemas("fsp")
                .defaultSchema("fsp")
                .locations("classpath:db/migration/fsp")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void reset() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE fsp_links RESTART IDENTITY CASCADE");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        links = new FspLinkRepository(named);
        achievements = new AchievementCacheRepository(named);
    }

    @Test
    void seedInsertsPresentAndNoHistorySets() throws Exception {
        runSeed();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM fsp_links WHERE candidate_id IN (3, 4) AND history_status = 'no_history'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM achievements_cache a JOIN fsp_links l ON l.id = a.fsp_link_id "
                        + "WHERE l.candidate_id IN (3, 4)",
                Integer.class)).isZero();
        assertThat(countAchievementsForCandidate(1L)).isEqualTo(3);
        assertThat(countAchievementsForCandidate(2L)).isEqualTo(2);
    }

    @Test
    void repeatedLinkIsIdempotentConflictIs409AndUnlinkIsNoOp() {
        FspRegistryAdapter registry = mock(FspRegistryAdapter.class);
        EventPublisher events = mock(EventPublisher.class);
        when(registry.resolve("fsp-a")).thenReturn(RegistryHistory.of(HistoryStatus.PRESENT, List.of(
                new RegistryAchievement("x-1", "Победа", "championship", LocalDate.of(2023, 1, 1), 1, null))));
        FspLinkService service = new FspLinkService(links, achievements, registry, events);

        service.link(1L, "fsp-a");
        FspLink again = service.link(1L, "fsp-a");

        assertThat(again.linkStatus()).isEqualTo(LinkStatus.LINKED);
        assertThat(count("fsp_links")).isEqualTo(1);
        assertThat(countAchievementsForCandidate(1L)).isEqualTo(1);

        assertThatThrownBy(() -> service.link(2L, "fsp-a"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(409));
        assertThat(count("fsp_links")).isEqualTo(1);

        service.unlink(1L);
        assertThat(links.unlink(1L)).isZero();
        assertThat(count("fsp_links")).isEqualTo(1);
        assertThat(count("achievements_cache")).isZero();
    }

    @Test
    void uniqueFspIdViolationIsEnforcedByTheDatabase() {
        Instant now = Instant.now();
        links.upsert(1L, "fsp-dup", LinkStatus.LINKED, HistoryStatus.PRESENT, now, now);

        assertThatThrownBy(() -> links.upsert(2L, "fsp-dup", LinkStatus.LINKED, HistoryStatus.PRESENT, now, now))
                .hasMessageContaining("uq_fsp_links_fsp_id");
    }

    private void runSeed() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("db/seed/fsp_seed.sql"), StandardCharsets.UTF_8));
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private int countAchievementsForCandidate(long candidateId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM achievements_cache a JOIN fsp_links l ON l.id = a.fsp_link_id "
                        + "WHERE l.candidate_id = ?",
                Integer.class, candidateId);
        return count == null ? 0 : count;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
