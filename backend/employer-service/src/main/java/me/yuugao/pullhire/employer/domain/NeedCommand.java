package me.yuugao.pullhire.employer.domain;

/**
 * Editable need fields ({@code docs/data_model.md §8.2}).
 * Built from {@code web.dto.NeedRequest.toCommand()}; the owning
 * {@code companyId} is resolved from the JWT by the application layer, never from the body.
 */
public record NeedCommand(
        String title,
        String description,
        String specializationCode,
        String grade,
        String[] stack,
        String status) {
}
