package me.yuugao.pullhire.outreach;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Shared PostgreSQL setup for outreach DB-backed tests (mirrors the selection
 * flow-test convention): no Spring context, no Testcontainers. Runs only when
 * a URL is provided via {@code -Dpullhire.test.db.url} or
 * {@code OUTREACH_TEST_DB_URL}; otherwise callers skip via
 * {@link #requireDb()}.
 *
 * <p>The URL is normalised to {@code currentSchema=outreach} so the
 * unqualified table names of the main code resolve to the migrated schema.
 */
final class OutreachTestDb {

    static final String SCHEMA = "outreach";

    private static String url;

    private OutreachTestDb() {
    }

    /** Returns the DB URL or skips the test when the assumption is absent. */
    static String requireDb() {
        if (url == null) {
            url = withSchema(
                    firstNonBlank(System.getProperty("pullhire.test.db.url"),
                            System.getenv("OUTREACH_TEST_DB_URL")),
                    SCHEMA);
        }
        Assumptions.assumeTrue(url != null, "OUTREACH_TEST_DB_URL not set: DB-backed outreach checks skipped");
        return url;
    }

    /** Migrates the outreach schema (idempotent across test classes). */
    static void migrate(String dbUrl) {
        Flyway.configure()
                .dataSource(dbUrl, System.getProperty("pullhire.test.db.user"),
                        System.getProperty("pullhire.test.db.password"))
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration/outreach")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    static DriverManagerDataSource dataSource(String dbUrl) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(dbUrl);
        dataSource.setUsername(System.getProperty("pullhire.test.db.user"));
        dataSource.setPassword(System.getProperty("pullhire.test.db.password"));
        return dataSource;
    }

    private static String withSchema(String dbUrl, String schema) {
        if (dbUrl == null || dbUrl.isBlank() || dbUrl.contains("currentSchema=")) {
            return dbUrl != null && !dbUrl.isBlank() ? dbUrl : null;
        }
        return dbUrl + (dbUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
