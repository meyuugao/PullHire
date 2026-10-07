package com.pullhire.candidate.web.dto;

import com.pullhire.candidate.domain.Profile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** {@code GET|PUT /candidate/profile} response ({@code docs/api.md §3.2}). */
public record ProfileResponse(
        String id,
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
        String industryCode,
        String status) {

    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(
                Long.toString(profile.id()),
                profile.fullName(),
                profile.contactEmail(),
                profile.contactPhone(),
                profile.positionTitle(),
                profile.gradeDeclared(),
                profile.experienceYears(),
                toList(profile.stack()),
                toList(profile.skills()),
                toList(profile.softSkills()),
                profile.summary(),
                profile.specializationCode(),
                profile.industryCode(),
                profile.status());
    }

    private static List<String> toList(String[] values) {
        return values == null ? List.of() : Arrays.stream(values).filter(Objects::nonNull).toList();
    }
}
