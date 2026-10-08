package me.yuugao.pullhire.candidate.web.dto;

import me.yuugao.pullhire.candidate.domain.ProfileCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** {@code PUT /candidate/profile} body ({@code docs/api.md §3.2}, {@code docs/data_model.md §4.1}). */
public record ProfileRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Email String contactEmail,
        String contactPhone,
        String positionTitle,
        String gradeDeclared,
        @PositiveOrZero BigDecimal experienceYears,
        List<String> stack,
        List<String> skills,
        List<String> softSkills,
        String summary,
        String specializationCode,
        String industryCode) {

    public ProfileCommand toCommand() {
        return new ProfileCommand(fullName, contactEmail, contactPhone, positionTitle, gradeDeclared,
                experienceYears, stack, skills, softSkills, summary, specializationCode, industryCode);
    }
}
