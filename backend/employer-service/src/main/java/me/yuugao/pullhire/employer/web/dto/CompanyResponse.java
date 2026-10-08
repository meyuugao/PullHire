package me.yuugao.pullhire.employer.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.yuugao.pullhire.employer.domain.Company;

import java.time.Instant;

/** {@code GET|PUT /employer/company} response ({@code docs/api.md §3.6}). */
public record CompanyResponse(
        String id,
        String name,
        String description,
        String industry,
        @JsonProperty("contact_email") String contactEmail,
        @JsonProperty("contact_phone") String contactPhone,
        String website,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
                Long.toString(company.id()),
                company.name(),
                company.description(),
                company.industry(),
                company.contactEmail(),
                company.contactPhone(),
                company.website(),
                company.createdAt(),
                company.updatedAt());
    }
}
