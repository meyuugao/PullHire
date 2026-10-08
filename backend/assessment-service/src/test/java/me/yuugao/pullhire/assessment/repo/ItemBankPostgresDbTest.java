package me.yuugao.pullhire.assessment.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.ItemKind;
import me.yuugao.pullhire.assessment.domain.ItemStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB-backed checks for {@link ItemBankRepository} against the
 * {@code assessment} schema (docs/data_model.md §6.1): seed-load, the
 * {@code template implies template_family} and non-negative {@code exposure_limit}
 * CHECKs, atomic exposure increments and exclusion of exhausted items from
 * rotation.
 *
 * <p>Runs only when a PostgreSQL URL is provided via {@code -Dpullhire.test.db.url}
 * or {@code ASSESSMENT_TEST_DB_URL} (compose/pg instance); otherwise the class is
 * skipped, so CI without a live database stays green. No extra dependency is
 * introduced.
 */
class ItemBankPostgresDbTest {

    private static String url;
    private JdbcTemplate jdbc;
    private ItemBankRepository items;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("ASSESSMENT_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "ASSESSMENT_TEST_DB_URL not set: DB-backed item_bank checks skipped");
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
        jdbc.execute("TRUNCATE item_bank RESTART IDENTITY CASCADE");
        items = new ItemBankRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void seedLoadsAnchorAndTemplateRows() {
        items.seedAll(List.of(anchor("spec-1", 100), template("spec-1")));

        assertThat(count("item_bank")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM item_bank WHERE item_kind = 'anchor' AND template_family IS NULL",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM item_bank WHERE item_kind = 'template' AND template_family = 'fam-1'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void templateWithoutFamilyViolatesCheck() {
        Item orphan = new Item(0L, ItemKind.TEMPLATE, null, "concept-b", "spec-1", new BigDecimal("0.500"),
                false, null, 0, "{\"q\":2}", ItemStatus.ACTIVE, Instant.now(), Instant.now());

        assertThatThrownBy(() -> items.insert(orphan))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("item_bank_template_family_check");
    }

    @Test
    void negativeExposureLimitViolatesCheck() {
        Item negative = anchor("spec-1", -1);

        assertThatThrownBy(() -> items.insert(negative))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("item_bank_exposure_limit_check");
    }

    @Test
    void exposureIncrementsAndExhaustedItemLeavesRotation() {
        long anchorId = items.insert(anchor("spec-1", 1));
        long templateId = items.insert(template("spec-1"));
        long otherSpec = items.insert(anchor("spec-2", 5));

        assertThat(items.findActiveBySpecialization("spec-1")).extracting(Item::id)
                .containsExactlyInAnyOrder(anchorId, templateId);

        assertThat(items.isExposureExhausted(anchorId)).isFalse();
        assertThat(items.incrementExposure(anchorId)).isEqualTo(1);
        assertThat(items.isExposureExhausted(anchorId)).isTrue();

        List<Item> rotation = items.findActiveBySpecialization("spec-1").stream()
                .filter(item -> !item.exposureExhausted())
                .toList();
        assertThat(rotation).extracting(Item::id).containsExactly(templateId);
        assertThat(rotation).extracting(Item::id).doesNotContain(anchorId);

        assertThat(items.findActiveBySpecialization("spec-2")).extracting(Item::id).containsExactly(otherSpec);
    }

    @Test
    void retireRemovesItemFromActiveLookupAndIsIdempotent() {
        long id = items.insert(anchor("spec-3", 10));

        assertThat(items.retire(id)).isEqualTo(1);
        assertThat(items.retire(id)).isZero();
        assertThat(items.findActiveBySpecialization("spec-3")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM item_bank WHERE id = ?", String.class, id))
                .isEqualTo("retired");
    }

    private static Item anchor(String specialization, int exposureLimit) {
        return new Item(0L, ItemKind.ANCHOR, null, "concept-a", specialization, new BigDecimal("1.250"),
                true, exposureLimit, 0, "{\"q\":1}", ItemStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private static Item template(String specialization) {
        return new Item(0L, ItemKind.TEMPLATE, "fam-1", "concept-c", specialization, new BigDecimal("0.750"),
                false, null, 0, "{\"q\":3}", ItemStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
