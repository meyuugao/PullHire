package me.yuugao.pullhire.employer.domain;

/**
 * Editable company fields from {@code PUT /employer/company}
 * ({@code docs/data_model.md §8.1}). {@code userId} is resolved from the JWT by
 * the application layer, never from the body.
 */
public record CompanyCommand(
        String name,
        String description,
        String industry,
        String contactEmail,
        String contactPhone,
        String website) {
}
