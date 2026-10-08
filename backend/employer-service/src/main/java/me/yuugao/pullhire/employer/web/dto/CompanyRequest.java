package me.yuugao.pullhire.employer.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.yuugao.pullhire.employer.domain.CompanyCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code PUT /employer/company} body ({@code docs/api.md §3.6}, {@code docs/data_model.md §8.1}). */
public record CompanyRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        String industry,
        @Email @JsonProperty("contact_email") String contactEmail,
        @JsonProperty("contact_phone") String contactPhone,
        String website) {

    public CompanyCommand toCommand() {
        return new CompanyCommand(name, description, industry, contactEmail, contactPhone, website);
    }
}
