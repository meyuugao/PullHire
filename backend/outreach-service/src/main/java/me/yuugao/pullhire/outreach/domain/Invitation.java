package me.yuugao.pullhire.outreach.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Row of {@code outreach.invitations} ({@code docs/data_model.md §9.1}).
 * {@code employerContact}/{@code releasedContact} map the {@code jsonb} columns
 * as raw JSON strings; {@code releasedContact} is {@code null} until the
 * {@code candidate.contact_released} consumer fills it.
 */
public record Invitation(
        long id,
        long employerId,
        long candidateId,
        Long needId,
        Long vacancyId,
        String message,
        String companyName,
        String employerContact,
        BigDecimal salaryFrom,
        BigDecimal salaryTo,
        String salaryBasis,
        String status,
        String releasedContact,
        Instant contactReleasedAt,
        Instant createdAt,
        Instant updatedAt) {
}
