package me.yuugao.pullhire.outreach.web.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code POST /invitations} request. Salary is mandatory; vacancy binding is optional. */
public record CreateInvitationRequest(
        @JsonProperty("candidate_id") Long candidateId,
        @JsonProperty("need_id") Long needId,
        @JsonProperty("vacancy_id") Long vacancyId,
        String message,
        @JsonProperty("company_name") String companyName,
        @JsonProperty("salary_from") BigDecimal salaryFrom,
        @JsonProperty("salary_to") BigDecimal salaryTo,
        @JsonProperty("salary_basis") String salaryBasis,
        @JsonProperty("employer_contact") EmployerContact employerContact) {

    /** Employer contact snapshot, stored verbatim; at least one channel is required. */
    public record EmployerContact(
            @JsonProperty("contact_email") String contactEmail,
            @JsonProperty("contact_phone") String contactPhone,
            String website) {
    }
}
