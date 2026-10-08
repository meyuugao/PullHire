package me.yuugao.pullhire.selection.application;

import me.yuugao.pullhire.selection.config.SelectionProperties;
import me.yuugao.pullhire.selection.error.ApiException;
import me.yuugao.pullhire.selection.repo.CandidateSearchRepository;
import me.yuugao.pullhire.selection.repo.RankSignalRepository;
import me.yuugao.pullhire.selection.repo.RankSignalRepository.RankSignalRow;
import me.yuugao.pullhire.selection.repo.SelectionResultRepository;
import me.yuugao.pullhire.selection.repo.SelectionResultRepository.Saved;
import me.yuugao.pullhire.selection.repo.TaxonomyRepository;
import me.yuugao.pullhire.selection.repo.TaxonomyRepository.TaxonomyRow;
import me.yuugao.pullhire.selection.web.dto.CandidateCardResponse;
import me.yuugao.pullhire.selection.web.dto.SelectionExplanationResponse;
import me.yuugao.pullhire.selection.web.dto.SelectionItemResponse;
import me.yuugao.pullhire.selection.web.dto.SelectionRequest;
import me.yuugao.pullhire.selection.web.dto.SelectionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Formation of a подборка ({@code POST /selections},
 * {@code GET /selections/{id}}, {@code docs/api.md §3.5},
 * {@code docs/selection.md §5}): taxonomy validation (OQ-27), candidate pool
 * from own projections, ranking within the requested category, qualitative
 * explainability per item ({@code §4.3}), recommended categories (frozen
 * illustrative rule, {@code §4.2} underdetermined — OQ-style default), and
 * immutable snapshot persistence (V6 {@code selection_results}).
 * Refinement is a new {@code POST} with {@code parent_selection_id} linking
 * to the original row; originals are never overwritten, snapshots immutable.
 */
@Service
public class SelectionService {

    private static final JsonMapper JSON = new JsonMapper();

    /**
     * Internal page size for draining the full category pool from
     * {@code CandidateSearchRepository.search} ({@code docs/selection.md §4.2}:
     * ranking runs over the whole category). The repository returns an
     * id-ordered window, so any fixed cap would silently exclude stronger
     * candidates beyond it; pages are looped until a short page.
     */
    private static final int POOL_PAGE_SIZE = 500;

    private final TaxonomyRepository taxonomy;
    private final CandidateSearchRepository search;
    private final RankSignalRepository signals;
    private final SelectionResultRepository results;
    private final RankingService ranking;
    private final SelectionProperties properties;

    public SelectionService(TaxonomyRepository taxonomy,
                            CandidateSearchRepository search,
                            RankSignalRepository signals,
                            SelectionResultRepository results,
                            RankingService ranking,
                            SelectionProperties properties) {
        this.taxonomy = taxonomy;
        this.search = search;
        this.signals = signals;
        this.results = results;
        this.ranking = ranking;
        this.properties = properties;
    }

    /**
     * Validates the need, ranks the category pool, persists the snapshot and
     * returns the response mirroring it (echo {@code created_at}/{@code id}).
     * A non-null {@code parent_selection_id} links the refinement to its
     * original (must exist, else 404); the original row is never modified.
     *
     * <p>OQ-style note: refinement chain depth is unrestricted by design —
     * docs do not restrict depth, a parent only needs to pre-exist and
     * snapshots are immutable INSERT-only, so cycles are structurally
     * impossible. Each chain level costs one {@code findById}; no depth
     * limit is enforced here.
     */
    @Transactional
    public SelectionResponse create(SelectionRequest request) {
        String specialization = request.specializationCode().trim();
        String grade = request.grade().trim();
        validateTaxonomy(specialization, grade);
        String categoryCode = specialization + "-" + grade;

        List<String> requestedStack = normalizeStack(request.stack());
        String[] tags = requestedStack.isEmpty() ? null : requestedStack.toArray(String[]::new);

        List<CandidateCardResponse> pool = fetchFullPool(specialization, grade, tags);

        Map<Long, RankSignalRow> byCandidate = new HashMap<>();
        for (RankSignalRow row : signals.findByCategoryCode(categoryCode)) {
            byCandidate.put(row.candidateId(), row);
        }
        Map<Long, CandidateCardResponse> cards = new HashMap<>();
        for (CandidateCardResponse card : pool) {
            cards.put(card.candidateId(), card);
        }
        List<RankingService.RankInput> inputs = pool.stream()
                .map(card -> {
                    RankSignalRow signal = byCandidate.get(card.candidateId());
                    return new RankingService.RankInput(
                            card.candidateId(),
                            signal == null ? null : signal.theta(),
                            signal != null && signal.fspVerified(),
                            signal == null ? 0.0 : signal.fspAchievementsWeighted(),
                            card.categoryConfirmed());
                })
                .toList();

        List<RankingService.CandidateScore> ranked = ranking.rank(inputs);

        List<SelectionItemResponse> items = new ArrayList<>(ranked.size());
        int position = 1;
        for (RankingService.CandidateScore scored : ranked) {
            CandidateCardResponse card = cards.get(scored.candidateId());
            if (card == null) {
                continue;
            }
            items.add(new SelectionItemResponse(
                    card.candidateId(), position++, explain(categoryCode, card, requestedStack)));
        }

        List<String> recommended = recommendedCategories(specialization, categoryCode);

        Long parentSelectionId = request.parentSelectionId();
        if (parentSelectionId != null && results.findById(parentSelectionId).isEmpty()) {
            throw ApiException.notFound("Подборка не найдена: " + parentSelectionId);
        }
        Saved saved = results.insert(
                parentSelectionId,
                JSON.writeValueAsString(request),
                JSON.writeValueAsString(recommended),
                JSON.writeValueAsString(items));
        return new SelectionResponse(
                saved.id(), parentSelectionId, saved.createdAt(), request, recommended, items);
    }

    /**
     * Returns the stored snapshot exactly as persisted: no re-ranking, no
     * card enrichment. Unknown ids fail with 404.
     */
    @Transactional(readOnly = true)
    public SelectionResponse getById(long id) {
        SelectionResultRepository.Snapshot snapshot = results.findById(id)
                .orElseThrow(() -> ApiException.notFound("Подборка не найдена: " + id));
        SelectionRequest request = JSON.readValue(snapshot.requestJson(), SelectionRequest.class);
        String[] recommended = JSON.readValue(snapshot.recommendedCategoriesJson(), String[].class);
        SelectionItemResponse[] items = JSON.readValue(snapshot.itemsJson(), SelectionItemResponse[].class);
        return new SelectionResponse(
                snapshot.id(), snapshot.parentSelectionId(), snapshot.createdAt(),
                request, List.of(recommended), List.of(items));
    }

    /**
     * Drains the whole category pool by paginating {@code search} to
     * exhaustion (page 0.. until a page returns fewer than
     * {@code POOL_PAGE_SIZE}). Ordering stays deterministic: repository pages
     * are id-ordered and concatenated in page order; final order comes from
     * {@code RankingService} with its deterministic tie-breaks.
     */
    private List<CandidateCardResponse> fetchFullPool(String specialization, String grade, String[] tags) {
        List<CandidateCardResponse> pool = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<CandidateCardResponse> page = search.search(
                    specialization, grade, null, tags, null, POOL_PAGE_SIZE, offset);
            pool.addAll(page);
            if (page.size() < POOL_PAGE_SIZE) {
                break;
            }
            offset += POOL_PAGE_SIZE;
        }
        return pool;
    }

    /**
     * OQ-27: {@code specialization_code} + {@code grade} must exist in the
     * active taxonomy (membership over
     * {@code TaxonomyRepository.findActiveSpecializations/findActiveGrades},
     * mirroring assessment {@code SurveyService.validateTaxonomy}). Unknown
     * codes fail with 400 {@code validation_error}.
     */
    private void validateTaxonomy(String specialization, String grade) {
        Set<String> specializations = new HashSet<>();
        for (TaxonomyRow row : taxonomy.findActiveSpecializations()) {
            specializations.add(row.code());
        }
        if (!specializations.contains(specialization)) {
            throw ApiException.validation("Unknown specialization_code: " + specialization);
        }
        Set<String> grades = new HashSet<>();
        for (TaxonomyRow row : taxonomy.findActiveGrades()) {
            grades.add(row.code());
        }
        if (!grades.contains(grade)) {
            throw ApiException.validation("Unknown grade: " + grade);
        }
    }

    /**
     * Frozen illustrative rule ({@code docs/selection.md §4.2} underdetermined,
     * OQ-style default): requested category first, then same-specialization
     * grades holding at least one {@code candidate_categories} row, ordered by
     * taxonomy {@code grade_order}. Informational only — does not affect items.
     */
    private List<String> recommendedCategories(String specialization, String categoryCode) {
        List<String> recommended = new ArrayList<>();
        recommended.add(categoryCode);
        for (SelectionResultRepository.GradeRow row : results.findGradesBySpecialization(specialization)) {
            String code = specialization + "-" + row.grade();
            if (!code.equals(categoryCode) && !recommended.contains(code)) {
                recommended.add(code);
            }
        }
        return recommended;
    }

    /**
     * Four qualitative explanation blocks per item ({@code docs/selection.md
     * §4.3}): category match, confirmed test basis, ФСП achievements, stack
     * match. No {@code theta}, no score.
     */
    private static SelectionExplanationResponse explain(String categoryCode,
                                                        CandidateCardResponse card,
                                                        List<String> requestedStack) {
        String categoryMatch = "Категория " + categoryCode + " совпадает с потребностью"
                + " (" + card.specializationCode() + " × " + card.categoryGrade() + ")";
        String confirmedTestBasis = card.categoryConfirmed()
                ? "Грейд подтверждён тестом — основание позиции"
                : "Грейд не подтверждён, ранжируется ниже подтверждённых";
        String fspAchievements = card.fspAchievementsCount() > 0
                ? "Подтверждённые достижения ФСП: " + card.fspAchievementsCount()
                : "Подтверждённые достижения ФСП отсутствуют (нейтрально)";
        String stackMatch;
        if (requestedStack.isEmpty()) {
            stackMatch = "Требуемый стек не указан";
        } else {
            List<String> overlap = new ArrayList<>(card.stack());
            overlap.retainAll(new HashSet<>(requestedStack));
            stackMatch = overlap.isEmpty()
                    ? "Совпадений с требуемым стеком нет"
                    : "Совпадение стека: " + String.join(", ", overlap);
        }
        return new SelectionExplanationResponse(
                categoryMatch, confirmedTestBasis, fspAchievements, stackMatch);
    }

    private static List<String> normalizeStack(List<String> stack) {
        if (stack == null) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String entry : stack) {
            if (entry == null) {
                continue;
            }
            for (String part : entry.split(",")) {
                String tag = part.trim();
                if (!tag.isEmpty() && !tags.contains(tag)) {
                    tags.add(tag);
                }
            }
        }
        return tags;
    }
}
