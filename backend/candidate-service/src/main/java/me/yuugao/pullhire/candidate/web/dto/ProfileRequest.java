package me.yuugao.pullhire.candidate.web.dto;

import me.yuugao.pullhire.candidate.domain.ProfileCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code PUT /candidate/profile} body ({@code docs/api.md §3.2}, {@code docs/data_model.md §4.1}).
 *
 * <p>Wire shape follows {@code docs/api.md §1}: {@code snake_case} keys.
 */
public record ProfileRequest(
        @JsonProperty("full_name") @NotBlank @Size(max = 255) String fullName,
        @JsonProperty("contact_email") @Email String contactEmail,
        @JsonProperty("contact_phone") String contactPhone,
        @JsonProperty("position_title") String positionTitle,
        @JsonProperty("grade_declared") String gradeDeclared,
        @JsonProperty("experience_years") @PositiveOrZero BigDecimal experienceYears,
        List<String> stack,
        List<String> skills,
        @JsonProperty("soft_skills") List<String> softSkills,
        String summary,
        @JsonProperty("specialization_code") String specializationCode,
        @JsonProperty("industry_code") String industryCode) {

    public ProfileCommand toCommand() {
        return new ProfileCommand(fullName, contactEmail, contactPhone, positionTitle, gradeDeclared,
                experienceYears, stack, skills, softSkills, summary, specializationCode, industryCode);
    }
}
