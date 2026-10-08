package me.yuugao.pullhire.selection.application;

import me.yuugao.pullhire.selection.repo.CandidateSearchRepository;
import me.yuugao.pullhire.selection.web.dto.CandidateCardResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Candidate search over own projections ({@code docs/api.md §3.5},
 * {@code docs/selection.md §5}). Filter normalization plus page/size
 * clamping (cf. employer {@code NeedService.Paging.of}).
 */
@Service
public class CandidateSearchService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final CandidateSearchRepository search;

    public CandidateSearchService(CandidateSearchRepository search) {
        this.search = search;
    }

    @Transactional(readOnly = true)
    public SearchPage search(String specialization, String grade, String category,
                             List<String> stack, Boolean fspAchievements,
                             int page, int size) {
        Paging paging = Paging.of(page, size);
        String[] tags = normalizeTags(stack);
        List<CandidateCardResponse> items = search.search(
                blankToNull(specialization), blankToNull(grade), blankToNull(category),
                tags, fspAchievements,
                paging.size(), (long) paging.page() * paging.size());
        long total = search.count(
                blankToNull(specialization), blankToNull(grade), blankToNull(category),
                tags, fspAchievements);
        return new SearchPage(items, paging.page(), paging.size(), total);
    }

    /** Flattens repeatable/comma-separated {@code stack} params; null/empty disables the filter. */
    static String[] normalizeTags(List<String> stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        List<String> tags = new ArrayList<>();
        for (String entry : stack) {
            if (entry == null) {
                continue;
            }
            for (String part : entry.split(",")) {
                String tag = part.trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
        }
        return tags.isEmpty() ? null : tags.toArray(String[]::new);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record SearchPage(List<CandidateCardResponse> items, int page, int size, long total) {
    }

    /** Normalised page/size; non-positive size falls back to the default, capped at max. */
    public record Paging(int page, int size) {
        public static Paging of(int page, int size) {
            int safePage = Math.max(page, 0);
            int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
            return new Paging(safePage, safeSize);
        }
    }
}
