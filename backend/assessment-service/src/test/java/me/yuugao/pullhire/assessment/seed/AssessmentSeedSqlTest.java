package me.yuugao.pullhire.assessment.seed;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.ItemKind;
import me.yuugao.pullhire.assessment.domain.ItemStatus;
import me.yuugao.pullhire.assessment.domain.form.ItemBankGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the synthetic assessment item bank seed
 * ({@code docs/testing_model.md §3.1–§3.4, §3.6}; {@code docs/assumptions.md §6, §9}):
 * anchors present with {@code template_family NULL}, at least N template families
 * per MVP specialization, {@code payload} carrying only model + key for
 * auto-checkable formats (no free text), an idempotent natural-key guard and no
 * event/other-table writes. Also checks that
 * {@link ItemBankGenerator} emits the same shape as the seed.
 */
class AssessmentSeedSqlTest {

    private static final String ITEM_BANK = "assessment.item_bank";

    private static final Pattern ROW = Pattern.compile(
            "^\\s*\\(\\s*'(anchor|template)'\\s*,\\s*(NULL|'([^']*)')\\s*,\\s*'([^']+)'\\s*,"
                    + "\\s*'([^']+)'\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(true|false)\\s*,"
                    + "\\s*(NULL|\\d+)\\s*,\\s*'(\\{.*\\})'\\s*,\\s*'([^']+)'\\s*\\)\\s*,?\\s*$",
            Pattern.MULTILINE);

    private static final Pattern FORMAT = Pattern.compile("\"format\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern INSERT_TARGET = Pattern.compile(
            "(?i)INSERT\\s+INTO\\s+([A-Za-z_][A-Za-z0-9_.]*)");

    @Test
    void anchorsArePresentWithNullTemplateFamily() throws Exception {
        List<Row> rows = rows(seed());

        List<Row> anchors = rows.stream().filter(row -> "anchor".equals(row.kind())).toList();
        List<Row> templates = rows.stream().filter(row -> "template".equals(row.kind())).toList();

        assertThat(anchors).isNotEmpty();
        assertThat(anchors).allSatisfy(row -> assertThat(row.family()).isNull());
        assertThat(templates).isNotEmpty();
        assertThat(templates).allSatisfy(row -> assertThat(row.family()).isNotBlank());
    }

    @Test
    void seedsAtLeastTemplateFamiliesPerSpecialization() throws Exception {
        Map<String, Long> familiesBySpecialization = rows(seed()).stream()
                .filter(row -> "template".equals(row.kind()))
                .collect(Collectors.groupingBy(Row::specialization, Collectors.counting()));

        assertThat(familiesBySpecialization.keySet())
                .containsExactlyInAnyOrderElementsOf(ItemBankGenerator.SPECIALIZATIONS);
        assertThat(familiesBySpecialization.values()).allSatisfy(count ->
                assertThat(count).isGreaterThanOrEqualTo(ItemBankGenerator.TEMPLATE_FAMILIES_PER_SPECIALIZATION));
    }

    @Test
    void payloadCarriesModelAndKeyForAutoCheckableFormats() throws Exception {
        String sql = seed();
        List<Row> rows = rows(sql);

        assertThat(rows).allSatisfy(row ->
                assertThat(row.payload()).contains("\"model\"").contains("\"key\""));

        List<String> formats = new ArrayList<>();
        for (Row row : rows) {
            Matcher matcher = FORMAT.matcher(row.payload());
            assertThat(matcher.find()).as("format present in payload of %s", row.concept()).isTrue();
            formats.add(matcher.group(1));
        }
        assertThat(formats).isNotEmpty();
        assertThat(formats).allSatisfy(format ->
                assertThat(ItemBankGenerator.ALLOWED_FORMATS).contains(format));

        // Decision D: no free-text / open-answer format anywhere in the seed.
        assertThat(sql.toLowerCase()).doesNotContain("free_text", "free-text", "open_text", "free-answer");
    }

    @Test
    void allSeedItemsAreExpertProvisional() throws Exception {
        assertThat(rows(seed())).allSatisfy(row -> assertThat(row.provisional()).isTrue());
    }

    @Test
    void templateItemsCarryExposureLimitAndAnchorsHaveNone() throws Exception {
        List<Row> rows = rows(seed());

        List<Row> templates = rows.stream().filter(row -> "template".equals(row.kind())).toList();
        List<Row> anchors = rows.stream().filter(row -> "anchor".equals(row.kind())).toList();

        // app.assessment.exposure-default default (application.yml ASSESSMENT_EXPOSURE_DEFAULT:100).
        assertThat(templates).isNotEmpty();
        assertThat(templates).allSatisfy(row -> assertThat(row.exposureLimit()).isEqualTo("100"));
        assertThat(anchors).isNotEmpty();
        assertThat(anchors).allSatisfy(row -> assertThat(row.exposureLimit()).isEqualTo("NULL"));

        // TD-5 caps at template_family level: one consistent cap per family.
        Map<String, Set<String>> limitsByFamily = templates.stream()
                .collect(Collectors.groupingBy(Row::family,
                        Collectors.mapping(Row::exposureLimit, Collectors.toSet())));
        assertThat(limitsByFamily.values())
                .allSatisfy(limits -> assertThat(limits).containsExactly("100"));
    }

    @Test
    void seedProjectsPerRowStatus() throws Exception {
        String sql = seed();

        assertThat(sql).contains("v.status");
        assertThat(sql).doesNotContain("v.payload::jsonb, 'active'");
        assertThat(rows(sql)).allSatisfy(row -> assertThat(row.status()).isEqualTo("active"));
    }

    @Test
    void seedIsIdempotentAndNaturalKeysAreUnique() throws Exception {
        String sql = seed();

        Set<NaturalKey> seen = new HashSet<>();
        for (Row row : rows(sql)) {
            NaturalKey key = new NaturalKey(row.kind(), row.specialization(), row.concept(), row.family());
            assertThat(seen.add(key)).as("duplicate seed natural key: %s", key).isTrue();
        }

        // Guard makes a re-run skip already-present rows instead of duplicating them.
        assertThat(sql).contains("WHERE NOT EXISTS");
        assertThat(sql).contains(ITEM_BANK + " ib");
        assertThat(sql).contains("ib.template_family IS NOT DISTINCT FROM v.template_family");
    }

    @Test
    void seedWritesOnlyItemBankAndNoEvents() throws Exception {
        String sql = seed();

        List<String> targets = new ArrayList<>();
        Matcher matcher = INSERT_TARGET.matcher(sql);
        while (matcher.find()) {
            targets.add(matcher.group(1));
        }
        assertThat(targets).isNotEmpty();
        assertThat(targets).allSatisfy(target -> assertThat(target).isEqualTo(ITEM_BANK));
        assertThat(sql).doesNotContain("outbox_events");
    }

    @Test
    void generatorEmitsAnchorsAndTemplateFamiliesPerSpecialization() {
        List<Item> items = ItemBankGenerator.generate();

        assertThat(items).hasSize(ItemBankGenerator.SPECIALIZATIONS.size()
                * (ItemBankGenerator.ANCHORS_PER_SPECIALIZATION
                   + ItemBankGenerator.TEMPLATE_FAMILIES_PER_SPECIALIZATION));

        List<Item> anchors = items.stream().filter(item -> item.itemKind() == ItemKind.ANCHOR).toList();
        List<Item> templates = items.stream().filter(item -> item.itemKind() == ItemKind.TEMPLATE).toList();

        assertThat(anchors).hasSize(ItemBankGenerator.SPECIALIZATIONS.size());
        assertThat(templates).hasSize(ItemBankGenerator.SPECIALIZATIONS.size()
                * ItemBankGenerator.TEMPLATE_FAMILIES_PER_SPECIALIZATION);
        assertThat(anchors).allSatisfy(anchor -> {
            assertThat(anchor.templateFamily()).isNull();
            assertThat(anchor.provisional()).isTrue();
            assertThat(anchor.status()).isEqualTo(ItemStatus.ACTIVE);
        });
        assertThat(templates).allSatisfy(template -> {
            assertThat(template.templateFamily()).isNotBlank();
            assertThat(template.provisional()).isTrue();
            assertThat(template.status()).isEqualTo(ItemStatus.ACTIVE);
        });

        for (Item item : items) {
            assertThat(item.payload()).contains("\"model\"").contains("\"key\"");
            Matcher matcher = FORMAT.matcher(item.payload());
            assertThat(matcher.find()).isTrue();
            assertThat(ItemBankGenerator.ALLOWED_FORMATS).contains(matcher.group(1));
        }
    }

    @Test
    void generatorShapeMatchesSeed() throws Exception {
        List<Item> items = ItemBankGenerator.generate();
        List<Row> rows = rows(seed());

        Map<String, Long> generatedFamilies = items.stream()
                .filter(item -> item.itemKind() == ItemKind.TEMPLATE)
                .collect(Collectors.groupingBy(Item::specializationCode, Collectors.counting()));
        Map<String, Long> seededFamilies = rows.stream()
                .filter(row -> "template".equals(row.kind()))
                .collect(Collectors.groupingBy(Row::specialization, Collectors.counting()));

        Map<String, Long> generatedAnchors = items.stream()
                .filter(item -> item.itemKind() == ItemKind.ANCHOR)
                .collect(Collectors.groupingBy(Item::specializationCode, Collectors.counting()));
        Map<String, Long> seededAnchors = rows.stream()
                .filter(row -> "anchor".equals(row.kind()))
                .collect(Collectors.groupingBy(Row::specialization, Collectors.counting()));

        assertThat(generatedFamilies).isEqualTo(seededFamilies);
        assertThat(generatedAnchors).isEqualTo(seededAnchors);

        Set<String> generatedSpecializations = items.stream()
                .map(Item::specializationCode)
                .collect(Collectors.toSet());
        assertThat(generatedSpecializations).isEqualTo(new HashSet<>(ItemBankGenerator.SPECIALIZATIONS));
        assertThat(new HashSet<>(seededFamilies.keySet())).isEqualTo(generatedSpecializations);
    }

    private static List<Row> rows(String sql) {
        Matcher matcher = ROW.matcher(sql);
        List<Row> rows = new ArrayList<>();
        while (matcher.find()) {
            rows.add(new Row(matcher.group(1), matcher.group(3), matcher.group(4), matcher.group(5),
                    matcher.group(6), Boolean.parseBoolean(matcher.group(7)), matcher.group(8),
                    matcher.group(9), matcher.group(10)));
        }
        assertThat(rows).as("parsed seed rows").isNotEmpty();
        return rows;
    }

    private static String seed() throws Exception {
        try (InputStream stream = AssessmentSeedSqlTest.class.getClassLoader()
                .getResourceAsStream("db/seed/assessment_seed.sql")) {
            assertThat(stream).as("db/seed/assessment_seed.sql on the classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record Row(String kind, String family, String concept, String specialization,
                       String difficulty, boolean provisional, String exposureLimit, String payload,
                       String status) {
    }

    private record NaturalKey(String kind, String specialization, String concept, String family) {
    }
}
