package me.yuugao.pullhire.outreach.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.yuugao.pullhire.outreach.domain.Invitation;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Invitation response — all {@code docs/data_model.md §9.1} fields, ids as strings. */
public record InvitationResponse(
        String id,
        @JsonProperty("employer_id") String employerId,
        @JsonProperty("candidate_id") String candidateId,
        @JsonProperty("need_id") String needId,
        @JsonProperty("vacancy_id") String vacancyId,
        String message,
        @JsonProperty("company_name") String companyName,
        @JsonProperty("employer_contact") Object employerContact,
        @JsonProperty("salary_from") BigDecimal salaryFrom,
        @JsonProperty("salary_to") BigDecimal salaryTo,
        @JsonProperty("salary_basis") String salaryBasis,
        String status,
        @JsonProperty("released_contact") Object releasedContact,
        @JsonProperty("contact_released_at") Instant contactReleasedAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    private static final JsonMapper JSON = new JsonMapper();

    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(
                Long.toString(invitation.id()),
                Long.toString(invitation.employerId()),
                Long.toString(invitation.candidateId()),
                invitation.needId() == null ? null : invitation.needId().toString(),
                invitation.vacancyId() == null ? null : invitation.vacancyId().toString(),
                invitation.message(),
                invitation.companyName(),
                jsonOrNull(invitation.employerContact()),
                invitation.salaryFrom(),
                invitation.salaryTo(),
                invitation.salaryBasis(),
                invitation.status(),
                jsonOrNull(invitation.releasedContact()),
                invitation.contactReleasedAt(),
                invitation.createdAt(),
                invitation.updatedAt());
    }

    private static Object jsonOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(raw);
        } catch (RuntimeException ex) {
            ObjectNode fallback = JSON.createObjectNode();
            fallback.put("raw", raw);
            return fallback;
        }
    }
}
