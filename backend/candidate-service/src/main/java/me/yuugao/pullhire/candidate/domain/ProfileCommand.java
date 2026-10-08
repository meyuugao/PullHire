package me.yuugao.pullhire.candidate.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Editable profile fields from {@code PUT /candidate/profile}
 * ({@code docs/data_model.md §4.1}). {@code userId} is resolved from the JWT by
 * the application layer, never from the body.
 */
public record ProfileCommand(
        String fullName,
        String contactEmail,
        String contactPhone,
        String positionTitle,
        String gradeDeclared,
        BigDecimal experienceYears,
        List<String> stack,
        List<String> skills,
        List<String> softSkills,
        String summary,
        String specializationCode,
        String industryCode) {
}
