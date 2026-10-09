package me.yuugao.pullhire.outreach.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.yuugao.pullhire.outreach.domain.Invitation;
import me.yuugao.pullhire.outreach.error.ApiException;
import me.yuugao.pullhire.outreach.repo.InvitationEventRepository;
import me.yuugao.pullhire.outreach.repo.InvitationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Invitation lifecycle ({@code docs/data_model.md §9}): employers send
 * invitations only to categorised candidates; candidates move their own
 * invitations through {@code sent → viewed → accepted | declined}.
 *
 * <p>Every state change and its audit row in {@code invitation_events} share one
 * transaction with the outbox publish. Unknown id is 404 {@code not_found};
 * an existing row owned by another party is 403 {@code forbidden}; a wrong
 * source status is 409 {@code conflict}. Contact disclosure never happens here —
 * {@code released_contact} is filled only by the
 * {@code candidate.contact_released} consumer.
 */
@Service
public class InvitationService {

    private static final Set<String> SALARY_BASES = Set.of("gross", "net");
    private static final Set<String> ACCEPT_FROM = Set.of("sent", "viewed");

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private static final JsonMapper JSON = new JsonMapper();

    private final InvitationRepository invitations;
    private final InvitationEventRepository events;
    private final CategoryCheckClient categories;
    private final EventPublisher publisher;

    public InvitationService(InvitationRepository invitations, InvitationEventRepository events,
                             CategoryCheckClient categories, EventPublisher publisher) {
        this.invitations = invitations;
        this.events = events;
        this.categories = categories;
        this.publisher = publisher;
    }

    /**
     * Sends an invitation from the employer's JWT {@code sub} to a candidate.
     * Validates in order: message, company name, salary, contact snapshot,
     * candidate category — then inserts, audits and publishes in one transaction.
     */
    @Transactional
    public Invitation create(long employerId, CreateCommand command) {
        if (command.message() == null || command.message().isBlank()) {
            throw ApiException.validation("message обязателен");
        }
        if (command.companyName() == null || command.companyName().isBlank()) {
            throw ApiException.validation("company_name обязателен");
        }
        String salaryBasis = command.salaryBasis() == null || command.salaryBasis().isBlank()
                ? "gross"
                : command.salaryBasis().trim();
        if (command.salaryFrom() == null || command.salaryTo() == null
                || command.salaryFrom().signum() < 0 || command.salaryTo().signum() < 0
                || command.salaryTo().compareTo(command.salaryFrom()) < 0
                || !SALARY_BASES.contains(salaryBasis)) {
            throw ApiException.validation("Некорректная зарплата: salary_from/salary_to ≥ 0, salary_to ≥ salary_from, "
                    + "salary_basis один из: gross, net");
        }
        if (isBlank(command.contactEmail()) && isBlank(command.contactPhone()) && isBlank(command.website())) {
            throw ApiException.validation("employer_contact должен содержать хотя бы один канал связи");
        }
        boolean hasCategory;
        try {
            hasCategory = categories.hasCategory(command.candidateId());
        } catch (IllegalStateException ex) {
            throw ex;
        }
        if (!hasCategory) {
            throw ApiException.validation("Нельзя пригласить кандидата без категории");
        }

        Invitation invitation = invitations.insert(employerId, command.candidateId(),
                command.needId(), command.vacancyId(), command.message().trim(),
                command.companyName().trim(), employerContactJson(command),
                command.salaryFrom(), command.salaryTo(), salaryBasis);
        events.insert(invitation.id(), "sent", "employer", eventPayload(invitation.id()));
        publisher.publishSent(invitation.id(), invitation.candidateId(), invitation.employerId());
        return invitation;
    }

    /** Marks a {@code sent} invitation as viewed by its owning candidate. */
    @Transactional
    public Invitation markViewed(long invitationId, long candidateId) {
        Invitation invitation = requireOwned(invitationId, candidateId);
        if (!"sent".equals(invitation.status())) {
            throw ApiException.conflict("Приглашение уже обработано: " + invitation.status());
        }
        if (invitations.transitionUpdate(invitationId, "sent", "viewed") == 0) {
            throw ApiException.conflict("Приглашение уже обработано");
        }
        events.insert(invitationId, "viewed", "candidate", eventPayload(invitationId));
        publisher.publishViewed(invitationId, invitation.candidateId(), invitation.employerId());
        return invitations.findById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Приглашение не найдено"));
    }

    /** Accepts a {@code sent|viewed} invitation by its owning candidate. No contact disclosure here. */
    @Transactional
    public Invitation accept(long invitationId, long candidateId) {
        Invitation invitation = requireOwned(invitationId, candidateId);
        if (!ACCEPT_FROM.contains(invitation.status())) {
            throw ApiException.conflict("Приглашение уже обработано: " + invitation.status());
        }
        if (invitations.transitionUpdate(invitationId, invitation.status(), "accepted") == 0) {
            throw ApiException.conflict("Приглашение уже обработано");
        }
        events.insert(invitationId, "accepted", "candidate", eventPayload(invitationId));
        publisher.publishAccepted(invitationId, invitation.candidateId(), invitation.employerId());
        return invitations.findById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Приглашение не найдено"));
    }

    /** Declines a {@code sent|viewed} invitation by its owning candidate. */
    @Transactional
    public Invitation decline(long invitationId, long candidateId) {
        Invitation invitation = requireOwned(invitationId, candidateId);
        if (!ACCEPT_FROM.contains(invitation.status())) {
            throw ApiException.conflict("Приглашение уже обработано: " + invitation.status());
        }
        if (invitations.transitionUpdate(invitationId, invitation.status(), "declined") == 0) {
            throw ApiException.conflict("Приглашение уже обработано");
        }
        events.insert(invitationId, "declined", "candidate", eventPayload(invitationId));
        publisher.publishDeclined(invitationId, invitation.candidateId(), invitation.employerId());
        return invitations.findById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Приглашение не найдено"));
    }

    /** Paged list of an employer's own invitations, newest first. */
    @Transactional(readOnly = true)
    public InvitationPage listForEmployer(long employerId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        List<Invitation> items = invitations.findByEmployerId(employerId, null, safeSize, (long) safePage * safeSize);
        long total = invitations.countByEmployerId(employerId, null);
        return new InvitationPage(items, safePage, safeSize, total);
    }

    /** Paged list of a candidate's own invitations, newest first. */
    @Transactional(readOnly = true)
    public InvitationPage listForCandidate(long candidateId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        List<Invitation> items = invitations.findByCandidateId(candidateId, null, safeSize, (long) safePage * safeSize);
        long total = invitations.countByCandidateId(candidateId, null);
        return new InvitationPage(items, safePage, safeSize, total);
    }

    private Invitation requireOwned(long invitationId, long candidateId) {
        Invitation invitation = invitations.findById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Приглашение не найдено"));
        if (invitation.candidateId() != candidateId) {
            throw ApiException.forbidden("Приглашение принадлежит другому кандидату");
        }
        return invitation;
    }

    private static String employerContactJson(CreateCommand command) {
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("contact_email", trimmedOrNull(command.contactEmail()));
        contact.put("contact_phone", trimmedOrNull(command.contactPhone()));
        contact.put("website", trimmedOrNull(command.website()));
        return JSON.writeValueAsString(contact);
    }

    private static String eventPayload(long invitationId) {
        return JSON.writeValueAsString(Map.of("invitation_id", String.valueOf(invitationId)));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimmedOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** Validated parameters of {@link #create}. */
    public record CreateCommand(
            long candidateId,
            Long needId,
            Long vacancyId,
            String message,
            String companyName,
            BigDecimal salaryFrom,
            BigDecimal salaryTo,
            String salaryBasis,
            String contactEmail,
            String contactPhone,
            String website) {
    }

    /** Paged invitation list plus the total count. */
    public record InvitationPage(List<Invitation> items, int page, int size, long total) {
    }
}
