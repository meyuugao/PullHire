package me.yuugao.pullhire.assessment.domain.form;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.ItemKind;

/**
 * Deterministic attempt-form assembly from a form seed
 * ({@code docs/testing_model.md §3.2–§3.4, §4, §9.1}).
 *
 * <p>A form is assembled purely from repo-provided domain data: the candidate's
 * active {@link Item}s (anchors + template families) and the
 * {@code priorServedVariants} journal ({@code §3.4}). The same {@code formSeed}
 * always yields a byte-identical ordered form (item order, per-item
 * {@code variant_seed}, answer-option order); a different seed yields a
 * different selection where possible.
 *
 * <p>Assembly rules ({@code §4}):
 * <ul>
 *   <li><b>Anchors</b> ({@code item_kind = anchor}) are always included
 *       ({@code §3.3}); exposure caps still apply to them.</li>
 *   <li><b>At most one variant per template family</b>: isomorphs of a family
 *       share {@code b} and are interchangeable, so one is chosen
 *       deterministically ({@code §3.2, §3.4}).</li>
  *   <li><b>Declared-dimension coverage without bias</b>: template families are
  *       selected round-robin across the survey's {@code declared_dimensions} so
  *       every dimension is represented before any one is repeated
  *       ({@code §2.1, §4}). A dimension matches a family by
  *       {@code concept_code} or {@code specialization_code} (the only
  *       dimension-kind columns on the item row); a declared grade additionally
  *       biases the pick toward difficulty-appropriate families (see
  *       {@link #assemble(List, List, String, long, Map, int, int)}), while pure
  *       industry codes without a matching column fall through to the
  *       deterministic fill.</li>
 *   <li><b>Exposure caps</b>: items whose {@link Item#exposureExhausted()} is
 *       true are excluded; retiring them is the caller's job
 *       ({@code ItemBankRepository.retire}) — this class never mutates the
 *       bank ({@code §4, §9.1}).</li>
 *   <li><b>No re-serving</b>: a variant seed already served to this candidate
 *       for a family is skipped and the next deterministic seed is derived, all
 *       within the form seed ({@code §3.4, §9.1}).</li>
 *   <li><b>Shuffling</b>: item order and answer-option order are permuted
 *       deterministically from the form seed ({@code §4, §9.1}).</li>
 * </ul>
 *
 * <p>Pure domain: no Spring, JDBC or Jackson. Numeric parameters (target item
 * count, option count) are supplied by the caller and never hardcoded here. The
 * answer-option count is passed in because the item {@code payload} model is not
 * parsed in the domain layer.
 */
public final class FormAssembler {

    private static final long ORDER_SALT = 0x51L;
    private static final long OPTION_SALT = 0x52L;
    private static final long FAMILY_SALT = 0x53L;
    private static final long ANCHOR_SALT = 0x54L;
    private static final long VARIANT_STEP_SALT = 0x55L;
    private static final long DIMENSION_SALT = 0x56L;
    private static final long FILL_SALT = 0x57L;

    /**
     * Documented grade order mirroring the default
     * {@code AssessmentProperties} grades list; used to derive a target
     * difficulty for grade-aware family selection. Unknown grade tokens are
     * ignored (treated as {@code null}).
     */
    private static final List<String> GRADE_ORDER =
            List.of("intern", "junior", "middle", "senior", "lead");

    private FormAssembler() {
    }

    /**
     * One served position of an assembled form: the item identity, its derived
     * per-item {@code variant_seed} ({@code attempts.variant_seed}) and the
     * deterministic answer-option permutation.
     *
     * @param itemOrder   1-based serving order within the attempt
     * @param optionOrder a permutation of {@code [0, optionCount)}; the answer
     *                    option originally at index {@code i} is presented at
     *                    position {@code optionOrder.indexOf(i)}
     */
    public record FormItem(
            long itemId,
            ItemKind itemKind,
            String templateFamily,
            String conceptCode,
            long variantSeed,
            int itemOrder,
            List<Integer> optionOrder) {

        public FormItem {
            optionOrder = List.copyOf(optionOrder);
        }
    }

    /** An assembled attempt form in serving order. */
    public record Form(List<FormItem> items) {

        public Form {
            items = List.copyOf(items);
        }
    }

    /**
     * Assembles the deterministic attempt form.
     *
     * @param items              active items from the bank (anchors and template
     *                           families); order-independent — the list is sorted
     *                           by id internally
     * @param declaredDimensions dimensions declared in the survey stage; may be
     *                           null or empty
     * @param formSeed           {@code test_sessions.form_seed}
     * @param priorServedVariants variant seeds already served to the candidate,
     *                           keyed by {@code template_family}
     *                           ({@code AttemptRepository.priorServedVariants});
     *                           may be null
     * @param targetItemCount    upper bound on form size (non-negative); all
     *                           eligible anchors are always kept even if they
     *                           exceed this bound
     * @param optionCount        number of answer options per item (non-negative)
     * @return the ordered form; the same arguments always produce an identical
     *         form
     */
    public static Form assemble(List<Item> items,
                                List<String> declaredDimensions,
                                long formSeed,
                                Map<String, List<Long>> priorServedVariants,
                                int targetItemCount,
                                int optionCount) {
        return assemble(items, declaredDimensions, null, formSeed,
                priorServedVariants, targetItemCount, optionCount);
    }

    /**
     * Grade-aware variant of {@link #assemble(List, List, long, Map, int, int)}.
     *
     * <p>When {@code declaredGrade} names a known grade, family selection prefers
     * difficulty-appropriate families: per-dimension candidates are ordered by
     * distance of the family representative's difficulty ({@code b}) to the
     * grade-implied target before the deterministic hash pick, so the declared
     * grade actively shapes the form instead of staying inert. A {@code null},
     * blank or unknown grade behaves exactly like the grade-agnostic overload.
     *
     * @param declaredGrade the survey's {@code target_grade}; may be null
     */
    public static Form assemble(List<Item> items,
                                List<String> declaredDimensions,
                                String declaredGrade,
                                long formSeed,
                                Map<String, List<Long>> priorServedVariants,
                                int targetItemCount,
                                int optionCount) {
        if (targetItemCount < 0) {
            throw new IllegalArgumentException("targetItemCount must be non-negative");
        }
        if (optionCount < 0) {
            throw new IllegalArgumentException("optionCount must be non-negative");
        }

        List<Item> eligible = (items == null ? List.<Item>of() : items).stream()
                .filter(Objects::nonNull)
                .filter(item -> !item.exposureExhausted())
                .sorted(Comparator.comparingLong(Item::id))
                .toList();

        List<Item> anchors = eligible.stream()
                .filter(item -> item.itemKind() == ItemKind.ANCHOR)
                .toList();

        Map<String, List<Item>> families = groupTemplates(eligible);
        Map<String, Item> representative = chooseRepresentatives(families, formSeed);
        Map<String, Set<Long>> servedByFamily = indexServed(priorServedVariants);
        List<String> dimensions = distinctDimensions(declaredDimensions);

        int templateSlots = Math.max(0, targetItemCount - anchors.size());
        Double gradeTarget = gradeTarget(declaredGrade);
        List<String> chosenFamilies = chooseFamilies(representative, dimensions, gradeTarget, formSeed, templateSlots);

        List<Item> chosen = new ArrayList<>(anchors);
        for (String family : chosenFamilies) {
            chosen.add(representative.get(family));
        }

        fisherYates(chosen, new Rng(mix(formSeed ^ ORDER_SALT)));

        List<FormItem> formItems = new ArrayList<>(chosen.size());
        int itemOrder = 1;
        for (Item item : chosen) {
            formItems.add(new FormItem(
                    item.id(),
                    item.itemKind(),
                    item.templateFamily(),
                    item.conceptCode(),
                    variantSeed(item, formSeed, servedByFamily),
                    itemOrder++,
                    optionPermutation(formSeed, item.id(), optionCount)));
        }
        return new Form(formItems);
    }

    private static Map<String, List<Item>> groupTemplates(List<Item> eligible) {
        Map<String, List<Item>> families = new LinkedHashMap<>();
        for (Item item : eligible) {
            if (item.itemKind() != ItemKind.TEMPLATE) {
                continue;
            }
            families.computeIfAbsent(familyKey(item), key -> new ArrayList<>()).add(item);
        }
        return families;
    }

    private static Map<String, Item> chooseRepresentatives(Map<String, List<Item>> families, long formSeed) {
        Map<String, Item> representative = new LinkedHashMap<>();
        for (Map.Entry<String, List<Item>> entry : families.entrySet()) {
            List<Item> isomorphs = entry.getValue();
            long hash = mix(formSeed ^ hashKey(entry.getKey()) ^ FAMILY_SALT);
            int index = (int) Math.floorMod(hash, (long) isomorphs.size());
            representative.put(entry.getKey(), isomorphs.get(index));
        }
        return representative;
    }

    private static List<String> chooseFamilies(Map<String, Item> representative,
                                               List<String> dimensions,
                                               Double gradeTarget,
                                               long formSeed,
                                               int slots) {
        List<String> chosen = new ArrayList<>();
        if (slots <= 0 || representative.isEmpty()) {
            return chosen;
        }
        Set<String> taken = new LinkedHashSet<>();
        long round = 0L;
        boolean progress = true;
        while (chosen.size() < slots && progress) {
            progress = false;
            for (String dimension : dimensions) {
                if (chosen.size() >= slots) {
                    break;
                }
                List<String> candidates = new ArrayList<>();
                for (Map.Entry<String, Item> entry : representative.entrySet()) {
                    if (!taken.contains(entry.getKey()) && covers(entry.getValue(), dimension)) {
                        candidates.add(entry.getKey());
                    }
                }
                if (candidates.isEmpty()) {
                    continue;
                }
                if (gradeTarget != null) {
                    candidates.sort(Comparator
                            .comparingDouble((String family) -> difficultyDistance(
                                    representative.get(family), gradeTarget))
                            .thenComparing(Comparator.naturalOrder()));
                }
                long hash = mix(formSeed ^ hashKey(dimension) ^ mix(round) ^ DIMENSION_SALT);
                int index = (int) Math.floorMod(hash, (long) candidates.size());
                String pick = candidates.get(index);
                taken.add(pick);
                chosen.add(pick);
                progress = true;
            }
            round++;
        }
        if (chosen.size() < slots) {
            List<String> remaining = new ArrayList<>();
            for (String family : representative.keySet()) {
                if (!taken.contains(family)) {
                    remaining.add(family);
                }
            }
            fisherYates(remaining, new Rng(mix(formSeed ^ FILL_SALT)));
            for (String family : remaining) {
                if (chosen.size() >= slots) {
                    break;
                }
                chosen.add(family);
            }
        }
        return chosen;
    }

    private static long variantSeed(Item item, long formSeed, Map<String, Set<Long>> servedByFamily) {
        if (item.itemKind() == ItemKind.ANCHOR) {
            return mix(formSeed ^ mix(item.id()) ^ ANCHOR_SALT);
        }
        long base = mix(formSeed ^ mix(item.id()) ^ FAMILY_SALT);
        Set<Long> served = servedByFamily.getOrDefault(familyKey(item), Set.of());
        long candidate = base;
        long attempt = 0L;
        while (served.contains(candidate)) {
            attempt++;
            candidate = mix(base ^ mix(attempt) ^ VARIANT_STEP_SALT);
        }
        return candidate;
    }

    private static List<Integer> optionPermutation(long formSeed, long itemId, int optionCount) {
        List<Integer> order = new ArrayList<>(optionCount);
        for (int index = 0; index < optionCount; index++) {
            order.add(index);
        }
        fisherYates(order, new Rng(mix(formSeed ^ mix(itemId) ^ OPTION_SALT)));
        return order;
    }

    /**
     * True when the family's representative item covers the declared dimension:
     * either by {@code concept_code} (concept coverage) or by
     * {@code specialization_code} (declared-specialization coverage,
     * {@code docs/data_model.md §6.1}). Grade and industry tokens have no
     * dedicated item column, so they never match here — grades shape selection
     * via the difficulty bias in {@link #chooseFamilies}, and unmatched industry
     * codes fall through to the deterministic fill.
     */
    private static boolean covers(Item item, String dimension) {
        if (dimension == null || item == null) {
            return false;
        }
        return dimension.equals(item.conceptCode()) || dimension.equals(item.specializationCode());
    }

    /**
     * Grade-implied target difficulty ({@code b}): the grade rank linearly
     * mapped onto {@code [-1.0, 1.0]}. Returns {@code null} for a null, blank
     * or unknown grade (grade-agnostic selection).
     */
    private static Double gradeTarget(String declaredGrade) {
        if (declaredGrade == null || declaredGrade.isBlank()) {
            return null;
        }
        int rank = GRADE_ORDER.indexOf(declaredGrade.trim());
        if (rank < 0) {
            return null;
        }
        return -1.0 + 2.0 * rank / (GRADE_ORDER.size() - 1);
    }

    private static double difficultyDistance(Item item, double target) {
        if (item == null || item.difficulty() == null) {
            return Double.MAX_VALUE;
        }
        return Math.abs(item.difficulty().doubleValue() - target);
    }

    private static List<String> distinctDimensions(List<String> declaredDimensions) {
        Set<String> distinct = new LinkedHashSet<>();
        if (declaredDimensions != null) {
            for (String dimension : declaredDimensions) {
                if (dimension != null && !dimension.isBlank()) {
                    distinct.add(dimension.trim());
                }
            }
        }
        return new ArrayList<>(distinct);
    }

    private static Map<String, Set<Long>> indexServed(Map<String, List<Long>> priorServedVariants) {
        Map<String, Set<Long>> served = new HashMap<>();
        if (priorServedVariants != null) {
            for (Map.Entry<String, List<Long>> entry : priorServedVariants.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                Set<Long> seeds = new HashSet<>();
                for (Long seed : entry.getValue()) {
                    if (seed != null) {
                        seeds.add(seed);
                    }
                }
                served.put(entry.getKey(), seeds);
            }
        }
        return served;
    }

    private static String familyKey(Item item) {
        return item.templateFamily() != null ? item.templateFamily() : "item:" + item.id();
    }

    private static <T> void fisherYates(List<T> list, Rng rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }

    private static long hashKey(String value) {
        long hash = 0xCBF29CE484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** SplitMix64 stream: a deterministic, platform-independent PRNG. */
    private static final class Rng {

        private long state;

        Rng(long seed) {
            this.state = seed;
        }

        long nextLong() {
            long z = (state += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        int nextInt(int bound) {
            return (int) Math.floorMod(nextLong(), (long) bound);
        }
    }
}
