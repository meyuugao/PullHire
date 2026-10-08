package me.yuugao.pullhire.assessment.domain.form;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.ItemKind;
import me.yuugao.pullhire.assessment.domain.ItemStatus;

/**
 * Deterministic synthetic {@code assessment.item_bank} generator
 * ({@code docs/testing_model.md §3.1–§3.4, §3.6}; {@code docs/assumptions.md §6, §9}).
 *
 * <p>For every MVP specialization ({@code docs/assumptions.md §4}) the generator
 * emits:
 * <ul>
 *   <li>anchor items ({@code item_kind = 'anchor'}, {@code template_family}
 *       {@code NULL}) with a fixed expert model and key ({@code §3.3}); anchors
 *       are never generated from templates;</li>
 *   <li>{@value #TEMPLATE_FAMILIES_PER_SPECIALIZATION} template families
 *       ({@code item_kind = 'template'}, non-null {@code template_family})
 *       covering the auto-checkable formats (single/multiple choice, short
 *       answer, code).</li>
 * </ul>
 *
 * <p>The {@code payload} carries only the immutable item <em>model</em>
 * (radical, {@code §3.2}) and its <em>key</em>. Variant incidentals are
 * deliberately not pre-baked: they are resolved downstream from
 * {@code test_sessions.form_seed} / {@code attempts.variant_seed} ({@code §3.4}).
 * Free-text ("propose an approach") is excluded from the MVP form by Decision D
 * ({@code docs/assumptions.md §9}).
 *
 * <p>Difficulties are expert/provisional ({@code is_provisional = true},
 * {@code §3.5}). Pure domain: no Spring, JDBC or Jackson; ids and timestamps are
 * assigned on persistence, so generated rows carry placeholder values.
 */
public final class ItemBankGenerator {

    public static final String FORMAT_SINGLE_CHOICE = "single_choice";
    public static final String FORMAT_MULTIPLE_CHOICE = "multiple_choice";
    public static final String FORMAT_SHORT_ANSWER = "short_answer";
    public static final String FORMAT_CODE = "code";

    /** Formats permitted in the MVP grade-test form (Decision D, docs/assumptions.md §6, §9). */
    public static final List<String> ALLOWED_FORMATS = List.of(
            FORMAT_SINGLE_CHOICE, FORMAT_MULTIPLE_CHOICE, FORMAT_SHORT_ANSWER, FORMAT_CODE);

    public static final int ANCHORS_PER_SPECIALIZATION = 1;
    public static final int TEMPLATE_FAMILIES_PER_SPECIALIZATION = 3;

    /** Persistence assigns real ids and timestamps; these are placeholders. */
    private static final Instant GENERATED_AT = Instant.EPOCH;

    private static final BigDecimal ANCHOR_DIFFICULTY = new BigDecimal("0.000");
    private static final BigDecimal SINGLE_CHOICE_DIFFICULTY = new BigDecimal("-1.000");
    private static final BigDecimal MULTIPLE_CHOICE_DIFFICULTY = new BigDecimal("0.000");
    private static final BigDecimal CODE_DIFFICULTY = new BigDecimal("1.000");

    private record SpecializationSeed(
            String code, String anchorStatement, String anchorAnswerType, String anchorKeyValueJson) {
    }

    private static final List<SpecializationSeed> SEEDS = List.of(
            new SpecializationSeed("backend",
                    "Which HTTP status code signals an internal server error?", "integer", "500"),
            new SpecializationSeed("frontend",
                    "Which CSS property controls the stacking order of positioned elements?", "string", "\"z-index\""),
            new SpecializationSeed("fullstack",
                    "Which HTTP method is idempotent and replaces the target resource?", "string", "\"PUT\""),
            new SpecializationSeed("mobile",
                    "Which Android lifecycle callback runs when an activity becomes visible?", "string", "\"onStart\""),
            new SpecializationSeed("devops",
                    "Which command builds a container image from a Dockerfile?", "string", "\"docker build\""),
            new SpecializationSeed("qa",
                    "Which artifact describes the conditions and cases to be tested?", "string", "\"test plan\""),
            new SpecializationSeed("data",
                    "Which SQL clause filters rows after grouping?", "string", "\"HAVING\""),
            new SpecializationSeed("ml",
                    "Which metric is the harmonic mean of precision and recall?", "string", "\"F1\""));

    public static final List<String> SPECIALIZATIONS = SEEDS.stream()
            .map(SpecializationSeed::code)
            .toList();

    private ItemBankGenerator() {
    }

    /** All synthetic items across every MVP specialization, in a stable order. */
    public static List<Item> generate() {
        List<Item> items = new ArrayList<>();
        for (SpecializationSeed seed : SEEDS) {
            items.addAll(generate(seed.code()));
        }
        return List.copyOf(items);
    }

    /**
     * One anchor plus {@value #TEMPLATE_FAMILIES_PER_SPECIALIZATION} template
     * families for the given specialization.
     *
     * @throws IllegalArgumentException when the specialization is not an MVP code
     */
    public static List<Item> generate(String specializationCode) {
        SpecializationSeed seed = SEEDS.stream()
                .filter(candidate -> candidate.code().equals(specializationCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown specialization: " + specializationCode));
        List<Item> items = new ArrayList<>();
        items.add(anchor(seed));
        items.add(template(seed.code(), FORMAT_SINGLE_CHOICE, SINGLE_CHOICE_DIFFICULTY));
        items.add(template(seed.code(), FORMAT_MULTIPLE_CHOICE, MULTIPLE_CHOICE_DIFFICULTY));
        items.add(template(seed.code(), FORMAT_CODE, CODE_DIFFICULTY));
        return List.copyOf(items);
    }

    private static Item anchor(SpecializationSeed seed) {
        String conceptCode = seed.code() + ".anchor.baseline";
        String payload = "{\"model\":{\"format\":\"" + FORMAT_SHORT_ANSWER
                + "\",\"statement\":\"" + seed.anchorStatement()
                + "\",\"answer_type\":\"" + seed.anchorAnswerType() + "\"},"
                + "\"key\":{\"value\":" + seed.anchorKeyValueJson() + "}}";
        return new Item(0L, ItemKind.ANCHOR, null, conceptCode, seed.code(), ANCHOR_DIFFICULTY, true,
                null, 0, payload, ItemStatus.ACTIVE, GENERATED_AT, GENERATED_AT);
    }

    private static Item template(String specializationCode, String format, BigDecimal difficulty) {
        String slug = format.replace('_', '-');
        String family = specializationCode + ".family." + slug;
        String concept = specializationCode + ".concept." + slug;
        return new Item(0L, ItemKind.TEMPLATE, family, concept, specializationCode, difficulty, true,
                null, 0, templatePayload(format), ItemStatus.ACTIVE, GENERATED_AT, GENERATED_AT);
    }

    /** Model + key only; incidentals stay parameterised for downstream seeding. */
    private static String templatePayload(String format) {
        return switch (format) {
            case FORMAT_SINGLE_CHOICE -> "{\"model\":{\"format\":\"" + FORMAT_SINGLE_CHOICE
                    + "\",\"statement\":\"Choose the single correct option for {{scenario}}.\","
                    + "\"parameters\":[\"scenario\"],"
                    + "\"options\":[\"option-a\",\"option-b\",\"option-c\",\"option-d\"]},"
                    + "\"key\":{\"correct\":\"option-b\"}}";
            case FORMAT_MULTIPLE_CHOICE -> "{\"model\":{\"format\":\"" + FORMAT_MULTIPLE_CHOICE
                    + "\",\"statement\":\"Select every statement that holds for {{scenario}}.\","
                    + "\"parameters\":[\"scenario\"],"
                    + "\"options\":[\"statement-a\",\"statement-b\",\"statement-c\",\"statement-d\"]},"
                    + "\"key\":{\"correct\":[\"statement-a\",\"statement-c\"]}}";
            case FORMAT_CODE -> "{\"model\":{\"format\":\"" + FORMAT_CODE
                    + "\",\"statement\":\"Implement {{function}} to satisfy the specification.\","
                    + "\"parameters\":[\"function\"],\"language\":\"java\"},"
                    + "\"key\":{\"reference_tests\":\"tests specify the expected behaviour\"}}";
            default -> throw new IllegalArgumentException("unsupported format: " + format);
        };
    }
}
