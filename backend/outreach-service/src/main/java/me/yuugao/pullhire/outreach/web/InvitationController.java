package me.yuugao.pullhire.outreach.web;

import java.util.List;

import me.yuugao.pullhire.outreach.application.CandidateUserClient;
import me.yuugao.pullhire.outreach.application.InvitationService;
import me.yuugao.pullhire.outreach.error.ApiException;
import me.yuugao.pullhire.outreach.security.JwtAuthFilter;
import me.yuugao.pullhire.outreach.web.dto.CreateInvitationRequest;
import me.yuugao.pullhire.outreach.web.dto.InvitationResponse;
import me.yuugao.pullhire.outreach.web.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /invitations}: employers send invitations, candidates read and move
 * their own through the lifecycle. Owner-scoped via the JWT {@code sub};
 * candidate identity is resolved to the logical profile id via
 * {@link CandidateUserClient}. List is paginated ({@code docs/api.md §1});
 * POST returns 201 with the created body (no {@code Location} header).
 */
@RestController
@RequestMapping("/invitations")
public class InvitationController {

    private final InvitationService invitations;
    private final CandidateUserClient candidates;

    public InvitationController(InvitationService invitations, CandidateUserClient candidates) {
        this.invitations = invitations;
        this.candidates = candidates;
    }

    @GetMapping
    public PageResponse<InvitationResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        String role = role(request);
        if ("employer".equalsIgnoreCase(role)) {
            InvitationService.InvitationPage result = invitations.listForEmployer(CurrentUser.id(request), page, size);
            return toPage(result);
        }
        if ("candidate".equalsIgnoreCase(role)) {
            long candidateId = candidates.resolveProfileId(CurrentUser.id(request));
            InvitationService.InvitationPage result = invitations.listForCandidate(candidateId, page, size);
            return toPage(result);
        }
        throw ApiException.forbidden("Недостаточно прав для просмотра приглашений");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse create(@RequestBody CreateInvitationRequest body, HttpServletRequest request) {
        requireRole(request, "employer");
        if (body.candidateId() == null) {
            throw ApiException.validation("candidate_id обязателен");
        }
        CreateInvitationRequest.EmployerContact contact = body.employerContact();
        InvitationService.CreateCommand command = new InvitationService.CreateCommand(
                body.candidateId(),
                body.needId(),
                body.vacancyId(),
                body.message(),
                body.companyName(),
                body.salaryFrom(),
                body.salaryTo(),
                body.salaryBasis(),
                contact == null ? null : contact.contactEmail(),
                contact == null ? null : contact.contactPhone(),
                contact == null ? null : contact.website());
        return InvitationResponse.from(invitations.create(CurrentUser.id(request), command));
    }

    @PostMapping("/{id}/mark-viewed")
    public InvitationResponse markViewed(@PathVariable long id, HttpServletRequest request) {
        requireRole(request, "candidate");
        long candidateId = candidates.resolveProfileId(CurrentUser.id(request));
        return InvitationResponse.from(invitations.markViewed(id, candidateId));
    }

    @PostMapping("/{id}/accept")
    public InvitationResponse accept(@PathVariable long id, HttpServletRequest request) {
        requireRole(request, "candidate");
        long candidateId = candidates.resolveProfileId(CurrentUser.id(request));
        return InvitationResponse.from(invitations.accept(id, candidateId));
    }

    @PostMapping("/{id}/decline")
    public InvitationResponse decline(@PathVariable long id, HttpServletRequest request) {
        requireRole(request, "candidate");
        long candidateId = candidates.resolveProfileId(CurrentUser.id(request));
        return InvitationResponse.from(invitations.decline(id, candidateId));
    }

    private static PageResponse<InvitationResponse> toPage(InvitationService.InvitationPage result) {
        List<InvitationResponse> items = result.items().stream().map(InvitationResponse::from).toList();
        return new PageResponse<>(items, result.page(), result.size(), result.total());
    }

    private static String role(HttpServletRequest request) {
        Object value = request.getAttribute(JwtAuthFilter.ATTR_ROLE);
        return value == null ? null : String.valueOf(value);
    }

    private static void requireRole(HttpServletRequest request, String role) {
        if (!role.equalsIgnoreCase(role(request))) {
            throw ApiException.forbidden("Операция доступна только роли " + role);
        }
    }
}
