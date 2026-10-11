package me.yuugao.pullhire.employer.application;

import me.yuugao.pullhire.employer.domain.Company;
import me.yuugao.pullhire.employer.domain.Need;
import me.yuugao.pullhire.employer.domain.NeedCommand;
import me.yuugao.pullhire.employer.error.ApiException;
import me.yuugao.pullhire.employer.repo.CompanyRepository;
import me.yuugao.pullhire.employer.repo.NeedRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owner-scoped need CRUD ({@code docs/api.md §3.6}, {@code docs/data_model.md §8.2}).
 * The owner is resolved from the JWT {@code sub} via the company profile
 * (logical {@code user_id}); every read/update/delete does a two-step access
 * check — missing row is 404 {@code not_found}, a row owned by another company
 * is 403 {@code forbidden}.
 */
@Service
public class NeedService {

    private static final Logger log = LoggerFactory.getLogger(NeedService.class);

    private static final Set<String> STATUSES = Set.of("active", "archived");

    /** Whitelist of sortable columns — never raw client input in SQL. */
    private static final Map<String, String> SORTABLE = Map.of(
            "created_at", "created_at",
            "updated_at", "updated_at",
            "id", "id",
            "title", "title",
            "status", "status");

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final CompanyRepository companies;
    private final NeedRepository needs;
    private final EventPublisher events;

    public NeedService(CompanyRepository companies, NeedRepository needs, EventPublisher events) {
        this.companies = companies;
        this.needs = needs;
        this.events = events;
    }

    /** Resolves the caller's company; 404 when the caller has no profile yet. */
    public Company requireCompany(long userId) {
        return companies.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Профиль компании не найден"));
    }

    /** Creates a need under the caller's company. */
    @Transactional
    public Need create(long userId, NeedCommand command) {
        Company company = requireCompany(userId);
        NeedCommand effective = withStatusDefault(command, "active");
        validate(effective);
        Need need = needs.insert(company.id(), trim(effective));
        // Success marker for the chain: MDC correlation_id comes from CorrelationMdcFilter.
        log.info("Потребность создана: need_id={}", need.id());
        return need;
    }

    /** Paged list of the caller's own company needs plus the total count. */
    @Transactional(readOnly = true)
    public NeedPage list(long userId, int page, int size, String sort) {
        Company company = requireCompany(userId);
        Paging paging = Paging.of(page, size, sort);
        List<Need> items = needs.findByCompanyId(company.id(), paging.size(),
                (long) paging.page() * paging.size(), paging.column(), paging.ascending());
        long total = needs.countByCompanyId(company.id());
        return new NeedPage(items, paging.page(), paging.size(), total);
    }

    /** Single need of the caller's company; 404 when missing, 403 when foreign. */
    @Transactional(readOnly = true)
    public Need get(long userId, long needId) {
        Company company = requireCompany(userId);
        Need need = needs.findById(needId)
                .orElseThrow(() -> ApiException.notFound("Потребность не найдена"));
        if (need.companyId() != company.id()) {
            throw ApiException.forbidden("Потребность принадлежит другой компании");
        }
        return need;
    }

    /** Replaces a need of the caller's company; 404 when missing, 403 when foreign. */
    @Transactional
    public Need update(long userId, long needId, NeedCommand command) {
        Company company = requireCompany(userId);
        Need current = needs.findById(needId)
                .orElseThrow(() -> ApiException.notFound("Потребность не найдена"));
        if (current.companyId() != company.id()) {
            throw ApiException.forbidden("Потребность принадлежит другой компании");
        }
        // Status rides on NeedRequest as an optional field: null on PUT keeps the current value.
        NeedCommand effective = command.status() == null
                ? new NeedCommand(command.title(), command.description(), command.specializationCode(),
                        command.grade(), command.stack(), current.status())
                : command;
        validate(effective);
        int affected = needs.update(needId, company.id(), trim(effective));
        if (affected == 0) {
            throw ApiException.notFound("Потребность не найдена");
        }
        return needs.findById(needId)
                .orElseThrow(() -> ApiException.notFound("Потребность не найдена"));
    }

    /** Deletes a need row of the caller's company (plain 204 semantics at the web layer). */
    @Transactional
    public void delete(long userId, long needId) {
        Company company = requireCompany(userId);
        Need current = needs.findById(needId)
                .orElseThrow(() -> ApiException.notFound("Потребность не найдена"));
        if (current.companyId() != company.id()) {
            throw ApiException.forbidden("Потребность принадлежит другой компании");
        }
        int affected = needs.delete(needId, company.id());
        if (affected == 0) {
            throw ApiException.notFound("Потребность не найдена");
        }
        events.publishNeedDeleted(String.valueOf(needId), String.valueOf(company.id()),
                String.valueOf(company.userId()));
    }

    private static NeedCommand withStatusDefault(NeedCommand command, String fallback) {
        if (command.status() == null) {
            return new NeedCommand(command.title(), command.description(), command.specializationCode(),
                    command.grade(), command.stack(), fallback);
        }
        return command;
    }

    private static void validate(NeedCommand command) {
        if (command.title() == null || command.title().isBlank()) {
            throw ApiException.validation("title обязателен");
        }
        if (command.specializationCode() == null || command.specializationCode().isBlank()) {
            throw ApiException.validation("specialization_code обязателен");
        }
        if (command.grade() == null || command.grade().isBlank()) {
            throw ApiException.validation("grade обязателен");
        }
        if (command.status() == null || !STATUSES.contains(command.status().trim())) {
            throw ApiException.validation("status должен быть одним из: active, archived");
        }
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static NeedCommand trim(NeedCommand command) {
        return new NeedCommand(
                command.title().trim(),
                trimmed(command.description()),
                command.specializationCode().trim(),
                command.grade().trim(),
                command.stack(),
                command.status().trim());
    }

    /** Normalised page/size plus parsed {@code sort=field,dir}; unknown fields fall back to {@code created_at desc}. */
    public record NeedPage(List<Need> items, int page, int size, long total) {
    }

    /** Normalised page/size plus parsed {@code sort=field,dir}; unknown fields fall back to {@code created_at desc}. */
    public record Paging(int page, int size, String column, boolean ascending) {
        public static Paging of(int page, int size, String sort) {
            Sort parsed = Sort.parse(sort);
            int safePage = Math.max(page, 0);
            int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
            return new Paging(safePage, safeSize, parsed.column(), parsed.ascending());
        }
    }

    /** Parsed {@code sort=field,dir}; unknown fields fall back to {@code created_at desc}. */
    private record Sort(String column, boolean ascending) {

        static Sort parse(String sort) {
            if (sort == null || sort.isBlank()) {
                return new Sort("created_at", false);
            }
            String[] parts = sort.split(",", 2);
            String column = SORTABLE.get(parts[0].trim());
            if (column == null) {
                return new Sort("created_at", false);
            }
            boolean ascending = parts.length == 2 && "asc".equalsIgnoreCase(parts[1].trim());
            return new Sort(column, ascending);
        }
    }
}
