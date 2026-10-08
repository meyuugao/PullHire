package me.yuugao.pullhire.selection.application;

import java.util.ArrayList;
import java.util.List;
import me.yuugao.pullhire.selection.repo.TaxonomyRepository;
import me.yuugao.pullhire.selection.repo.TaxonomyRepository.TaxonomyRow;
import me.yuugao.pullhire.selection.web.dto.CategoryResponse;
import me.yuugao.pullhire.selection.web.dto.TaxonomyItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog reads over {@code selection.taxonomy} ({@code docs/data_model.md §7.1}).
 * Categories are derived as specialization × grade pairs; taxonomy stores no
 * category rows.
 */
@Service
public class CatalogService {

    private final TaxonomyRepository taxonomy;

    public CatalogService(TaxonomyRepository taxonomy) {
        this.taxonomy = taxonomy;
    }

    /** Active specializations ordered by code. */
    @Transactional(readOnly = true)
    public List<TaxonomyItemResponse> specializations() {
        return taxonomy.findActiveSpecializations().stream()
                .map(CatalogService::toItem)
                .toList();
    }

    /** Active grades ordered by {@code grade_order}. */
    @Transactional(readOnly = true)
    public List<TaxonomyItemResponse> grades() {
        return taxonomy.findActiveGrades().stream()
                .map(CatalogService::toItem)
                .toList();
    }

    /**
     * Cross join of active specializations × grades, ordered by grade then
     * specialization code. Category code mirrors assessment-service
     * ({@code "<spec-code>-<grade-code>"}).
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        List<TaxonomyRow> specs = taxonomy.findActiveSpecializations();
        List<TaxonomyRow> grades = taxonomy.findActiveGrades();
        List<CategoryResponse> result = new ArrayList<>(specs.size() * grades.size());
        for (TaxonomyRow grade : grades) {
            for (TaxonomyRow spec : specs) {
                result.add(new CategoryResponse(
                        spec.code() + "-" + grade.code(),
                        spec.code(),
                        spec.name(),
                        grade.code(),
                        grade.name()));
            }
        }
        return result;
    }

    private static TaxonomyItemResponse toItem(TaxonomyRow row) {
        return new TaxonomyItemResponse(row.code(), row.name(), row.gradeOrder());
    }
}
