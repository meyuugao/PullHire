package me.yuugao.pullhire.selection.web;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import me.yuugao.pullhire.idempotency.IdempotencyFilter;
import me.yuugao.pullhire.idempotency.IdempotencyResultStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotency wiring for {@code POST /selections} (docs/api.md §1,
 * assumptions.md §12): same key twice replays the stored 2xx body without
 * re-executing the effect, a missing key fails with a 409 nested
 * problem+json envelope, and {@code GET /selections} needs no key.
 *
 * <p>Runs only when a PostgreSQL URL is provided via
 * {@code -Dpullhire.test.db.url} or {@code SELECTION_TEST_DB_URL}; otherwise
 * the class is skipped. Standalone MockMvc with a real
 * {@link IdempotencyFilter} + {@link IdempotencyResultStore} over a
 * {@link DriverManagerDataSource}; no Spring context, no Testcontainers.
 */
class IdempotencyFilterPostgresDbTest {

    private static String url;

    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("SELECTION_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "SELECTION_TEST_DB_URL not set: DB-backed idempotency checks skipped");
        Flyway.configure()
                .dataSource(url, System.getProperty("pullhire.test.db.user"),
                        System.getProperty("pullhire.test.db.password"))
                .schemas("selection")
                .defaultSchema("selection")
                .locations("classpath:db/migration/selection")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void reset() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        dataSource.setUsername(System.getProperty("pullhire.test.db.user"));
        dataSource.setPassword(System.getProperty("pullhire.test.db.password"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE candidate_cards, candidate_categories, rank_signals, "
                + "selection_results, idempotency_keys, processed_events RESTART IDENTITY CASCADE");
        IdempotencyResultStore store = new IdempotencyResultStore(jdbc, Duration.ofHours(24));
        mvc = MockMvcBuilders.standaloneSetup(new StubSelectionsController(jdbc))
                .addFilters(new IdempotencyFilter(store, List.of("/selections")))
                .build();
    }

    @Test
    void sameKeyTwiceReplaysWithoutReexecuting() throws Exception {
        String body = "{\"title\":\"Backend\",\"specialization_code\":\"backend\",\"grade\":\"middle\"}";

        MvcResult first = mvc.perform(post("/selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(IdempotencyFilter.HEADER, "key-1"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM selection_results", Integer.class)).isOne();

        MvcResult second = mvc.perform(post("/selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(IdempotencyFilter.HEADER, "key-1"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM selection_results", Integer.class)).isOne();
    }

    @Test
    void missingKeyFailsWithNestedProblemJson() throws Exception {
        MvcResult result = mvc.perform(post("/selections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Backend\",\"specialization_code\":\"backend\",\"grade\":\"middle\"}"))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
        assertThat(result.getResponse().getContentAsString()).contains("\"error\"");
    }

    @Test
    void getUnaffectedByFilter() throws Exception {
        mvc.perform(get("/selections"))
                .andExpect(status().isOk());
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    /**
     * Minimal {@code /selections} stand-in: POST inserts one
     * {@code selection_results} row per invocation (proving replay skips
     * re-execution via the row count) and returns 200 JSON; GET returns 200
     * without touching the database. Existing controller status (200, not
     * 201) is preserved; the filter persists any 2xx.
     */
    @RestController
    @RequestMapping("/selections")
    static class StubSelectionsController {

        private final JdbcTemplate jdbc;

        StubSelectionsController(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @PostMapping
        public Map<String, Object> create(@RequestBody Map<String, Object> request) {
            jdbc.update("INSERT INTO selection_results (request, recommended_categories, items) "
                    + "VALUES (CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb))",
                    "{\"title\":\"t\"}", "[]", "[]");
            return Map.of("ok", true, "title", String.valueOf(request.getOrDefault("title", "")));
        }

        @GetMapping
        public Map<String, Object> list() {
            return Map.of("ok", true);
        }
    }
}
