package me.yuugao.pullhire.selection.web;

import java.util.List;
import me.yuugao.pullhire.selection.application.CatalogService;
import me.yuugao.pullhire.selection.web.dto.CategoryResponse;
import me.yuugao.pullhire.selection.web.dto.TaxonomyItemResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /catalog/*} ({@code docs/api.md §3.5}). */
@RestController
@RequestMapping("/catalog")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/specializations")
    public List<TaxonomyItemResponse> specializations() {
        return catalog.specializations();
    }

    @GetMapping("/grades")
    public List<TaxonomyItemResponse> grades() {
        return catalog.grades();
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalog.categories();
    }
}
