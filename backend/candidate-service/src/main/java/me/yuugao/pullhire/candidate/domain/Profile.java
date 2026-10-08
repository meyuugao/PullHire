package me.yuugao.pullhire.candidate.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Row of {@code candidate.profiles} ({@code docs/data_model.md §4.1}).
 * {@code userId} is a logical reference to {@code auth.users.id} (no FK).
 */
public record Profile(
        long id,
        long userId,
        String fullName,
        String contactEmail,
        String contactPhone,
        String positionTitle,
        String gradeDeclared,
        BigDecimal experienceYears,
        String[] stack,
        String[] skills,
        String[] softSkills,
        String summary,
        String specializationCode,
        String industryCode,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
