package me.yuugao.pullhire.assessment.domain.form;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.ItemKind;
import me.yuugao.pullhire.assessment.domain.ItemStatus;
import me.yuugao.pullhire.assessment.domain.form.FormAssembler.Form;
import me.yuugao.pullhire.assessment.domain.form.FormAssembler.FormItem;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure behaviour checks of deterministic attempt-form assembly
 * ({@code docs/testing_model.md §3.2–§3.4, §4, §9.1}). No Spring context.
 */
class FormAssemblerTest {

    private static final List<String> DIMENSIONS = List.of("dimA", "dimB", "dimC");

    @Test
    void sameSeedProducesByteIdenticalOrderedForm() {
        Form first = FormAssembler.assemble(bank(), DIMENSIONS, 12345L, Map.of(), 10, 4);
        Form second = FormAssembler.assemble(bank(), DIMENSIONS, 12345L, Map.of(), 10, 4);

        assertThat(first.items()).isEqualTo(second.items());
    }

    @Test
    void differentSeedProducesDifferentForm() {
        Form first = FormAssembler.assemble(bank(), DIMENSIONS, 1L, Map.of(), 10, 4);
        Form second = FormAssembler.assemble(bank(), DIMENSIONS, 2L, Map.of(), 10, 4);

        assertThat(first.items()).isNotEqualTo(second.items());
    }

    @Test
    void atMostOneVariantPerTemplateFamily() {
        List<Item> items = List.of(
                anchor(1, "core", 0.0),
                isomorph(20, "family-x", "dimA", -1.0),
                isomorph(21, "family-x", "dimA", -1.0),
                template(11, "family-y", "dimB", 0.0));

        Form form = FormAssembler.assemble(items, DIMENSIONS, 9L, Map.of(), 10, 4);

        Map<String, Long> perFamily = form.items().stream()
                .filter(item -> item.itemKind() == ItemKind.TEMPLATE)
                .collect(Collectors.groupingBy(FormItem::templateFamily, Collectors.counting()));

        assertThat(perFamily.values()).allMatch(count -> count <= 1L);
        assertThat(perFamily).containsEntry("family-x", 1L);
    }

    @Test
    void anchorsAreIncludedInEveryForm() {
        for (long seed : new long[] {1L, 2L, 7L, 99L, 12345L}) {
            Form form = FormAssembler.assemble(bank(), DIMENSIONS, seed, Map.of(), 10, 4);

            assertThat(form.items()).extracting(FormItem::itemId).contains(1L, 2L);
        }
    }

    @Test
    void exhaustedFamilyIsExcluded() {
        List<Item> items = List.of(
                anchor(1, "core", 0.0),
                template(10, "family-ok", "dimA", -1.0),
                exhaustedTemplate(30, "family-dead", "dimB", 3));

        Form form = FormAssembler.assemble(items, DIMENSIONS, 5L, Map.of(), 20, 4);

        assertThat(form.items()).extracting(FormItem::itemId).doesNotContain(30L);
        assertThat(form.items()).extracting(FormItem::templateFamily).doesNotContain("family-dead");
        assertThat(form.items()).extracting(FormItem::itemId).contains(1L, 10L);
    }

    @Test
    void priorServedVariantIsNotReselected() {
        long formSeed = 777L;
        Form first = FormAssembler.assemble(bank(), DIMENSIONS, formSeed, Map.of(), 20, 4);
        FormItem served = first.items().stream()
                .filter(item -> item.itemKind() == ItemKind.TEMPLATE)
                .findFirst()
                .orElseThrow();

        Form second = FormAssembler.assemble(
                bank(), DIMENSIONS, formSeed, Map.of(served.templateFamily(), List.of(served.variantSeed())), 20, 4);
        FormItem reselected = second.items().stream()
                .filter(item -> served.templateFamily().equals(item.templateFamily()))
                .findFirst()
                .orElseThrow();

        assertThat(reselected.variantSeed()).isNotEqualTo(served.variantSeed());
        assertThat(reselected.variantSeed()).isNotIn(List.of(served.variantSeed()));
    }

    @Test
    void declaredDimensionsAreCovered() {
        Form form = FormAssembler.assemble(bank(), DIMENSIONS, 42L, Map.of(), 20, 4);

        Set<String> covered = form.items().stream()
                .map(FormItem::conceptCode)
                .collect(Collectors.toSet());

        assertThat(covered).containsAll(DIMENSIONS);
    }

    @Test
    void declaredSpecializationIsCoveredViaSpecializationCode() {
        List<Item> items = List.of(
                templateWithSpec(10, "family-be", "concept-shared", "backend", -1.0),
                templateWithSpec(11, "family-fe", "concept-shared", "frontend", 1.0));

        Form form = FormAssembler.assemble(items, List.of("frontend"), 42L, Map.of(), 1, 4);

        assertThat(form.items()).hasSize(1);
        assertThat(form.items().get(0).templateFamily()).isEqualTo("family-fe");
    }

    @Test
    void nullGradeMatchesGradeAgnosticAssembly() {
        Form legacy = FormAssembler.assemble(bank(), DIMENSIONS, 42L, Map.of(), 10, 4);
        Form explicitNull = FormAssembler.assemble(bank(), DIMENSIONS, null, 42L, Map.of(), 10, 4);
        Form unknownGrade = FormAssembler.assemble(bank(), DIMENSIONS, "staff++", 42L, Map.of(), 10, 4);

        assertThat(explicitNull.items()).isEqualTo(legacy.items());
        assertThat(unknownGrade.items()).isEqualTo(legacy.items());
    }

    @Test
    void declaredGradeBiasesFamilySelection() {
        List<Item> items = List.of(
                templateWithSpec(10, "family-easy", "concept-shared", "spec", -1.0),
                templateWithSpec(11, "family-hard", "concept-shared", "spec", 1.0));

        Form intern = FormAssembler.assemble(items, List.of("concept-shared"), "intern", 7L, Map.of(), 1, 4);
        Form lead = FormAssembler.assemble(items, List.of("concept-shared"), "lead", 7L, Map.of(), 1, 4);

        assertThat(intern.items()).hasSize(1);
        assertThat(lead.items()).hasSize(1);
        assertThat(intern.items().get(0).templateFamily()).isNotEqualTo(
                lead.items().get(0).templateFamily());
    }

    @Test
    void optionOrderIsADeterministicPermutation() {
        Form form = FormAssembler.assemble(bank(), DIMENSIONS, 42L, Map.of(), 20, 4);

        assertThat(form.items()).isNotEmpty();
        for (FormItem item : form.items()) {
            assertThat(item.optionOrder()).containsExactlyInAnyOrder(0, 1, 2, 3);
        }
        assertThat(form.items()).isEqualTo(
                FormAssembler.assemble(bank(), DIMENSIONS, 42L, Map.of(), 20, 4).items());
    }

    @Test
    void itemOrderIsOneBasedAndContiguous() {
        Form form = FormAssembler.assemble(bank(), DIMENSIONS, 3L, Map.of(), 10, 4);

        List<Integer> orders = form.items().stream().map(FormItem::itemOrder).toList();
        assertThat(orders).containsExactly(1, 2, 3, 4, 5, 6);
    }

    private static List<Item> bank() {
        return List.of(
                anchor(1, "core", 0.0),
                anchor(2, "core", 0.5),
                template(10, "family-1", "dimA", -1.0),
                template(11, "family-2", "dimB", 0.0),
                template(12, "family-3", "dimC", 1.0),
                template(13, "family-4", "dimA", -0.5));
    }

    private static Item anchor(long id, String concept, double difficulty) {
        return item(id, ItemKind.ANCHOR, null, concept, difficulty, null, 0);
    }

    private static Item template(long id, String family, String concept, double difficulty) {
        return item(id, ItemKind.TEMPLATE, family, concept, difficulty, null, 0);
    }

    private static Item templateWithSpec(long id, String family, String concept, String spec, double difficulty) {
        return new Item(
                id,
                ItemKind.TEMPLATE,
                family,
                concept,
                spec,
                BigDecimal.valueOf(difficulty),
                true,
                null,
                0,
                "{}",
                ItemStatus.ACTIVE,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static Item isomorph(long id, String family, String concept, double difficulty) {
        return item(id, ItemKind.TEMPLATE, family, concept, difficulty, null, 0);
    }

    private static Item exhaustedTemplate(long id, String family, String concept, int count) {
        return item(id, ItemKind.TEMPLATE, family, concept, 0.0, count, count);
    }

    private static Item item(long id, ItemKind kind, String family, String concept,
                             double difficulty, Integer exposureLimit, int exposureCount) {
        return new Item(
                id,
                kind,
                family,
                concept,
                "spec",
                BigDecimal.valueOf(difficulty),
                true,
                exposureLimit,
                exposureCount,
                "{}",
                ItemStatus.ACTIVE,
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
