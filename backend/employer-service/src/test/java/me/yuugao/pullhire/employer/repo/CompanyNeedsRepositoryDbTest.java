package me.yuugao.pullhire.employer.repo;

import java.time.Instant;

import me.yuugao.pullhire.employer.domain.Company;
import me.yuugao.pullhire.employer.domain.CompanyCommand;
import me.yuugao.pullhire.employer.domain.Need;
import me.yuugao.pullhire.employer.domain.NeedCommand;
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
 * DB-backed repository checks for the employer slice: company upsert
 * idempotency and company-scoped need update/delete.
 *
 * <p>Runs only when a PostgreSQL URL is provided via {@code -Dpullhire.test.db.url}
 * or {@code EMPLOYER_TEST_DB_URL} (compose/pg instance); otherwise the class is skipped,
 * so CI without a live database stays green. No extra dependency is introduced.
 */
class CompanyNeedsRepositoryDbTest {

    private static String url;
    private JdbcTemplate jdbc;
    private CompanyRepository companies;
    private NeedRepository needs;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("EMPLOYER_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "EMPLOYER_TEST_DB_URL not set: DB-backed employer checks skipped");
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
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE companies, needs, outbox_events RESTART IDENTITY CASCADE");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        companies = new CompanyRepository(named);
        needs = new NeedRepository(named);
    }

    @Test
    void companyUpsertInsertThenUpdateKeepsSingleRowAndCreatedAt() {
        Company first = companies.upsert(1L, command("Acme"));
        Instant createdAt = first.createdAt();

        Company second = companies.upsert(1L, command("Acme v2"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.createdAt()).isEqualTo(createdAt);
        assertThat(second.name()).isEqualTo("Acme v2");
        assertThat(count("companies")).isEqualTo(1);
    }

    @Test
    void needsUpdateAndDeleteAreCompanyScoped() {
        Company mine = companies.upsert(1L, command("Acme"));
        Company other = companies.upsert(2L, command("Other"));
        Need need = needs.insert(mine.id(), needCommand("Backend", "active"));

        assertThat(needs.update(need.id(), other.id(), needCommand("Hacked", "archived"))).isZero();
        assertThat(needs.delete(need.id(), other.id())).isZero();

        Need stored = needs.findById(need.id()).orElseThrow();
        assertThat(stored.title()).isEqualTo("Backend");
        assertThat(count("needs")).isEqualTo(1);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static CompanyCommand command(String name) {
        return new CompanyCommand(name, "About us", "IT", "hr@acme.example", "+7", "https://acme.example");
    }

    private static NeedCommand needCommand(String title, String status) {
        return new NeedCommand(title, "About", "backend", "middle", new String[]{"java"}, status);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
