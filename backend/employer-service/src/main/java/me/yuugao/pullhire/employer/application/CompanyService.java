package me.yuugao.pullhire.employer.application;

import me.yuugao.pullhire.employer.domain.Company;
import me.yuugao.pullhire.employer.domain.CompanyCommand;
import me.yuugao.pullhire.employer.error.ApiException;
import me.yuugao.pullhire.employer.repo.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Company profile lifecycle ({@code docs/api.md §3.6},
 * {@code docs/data_model.md §8.1}). The owner is resolved from the JWT
 * {@code sub} (logical {@code user_id}).
 */
@Service
public class CompanyService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CompanyRepository companies;

    public CompanyService(CompanyRepository companies) {
        this.companies = companies;
    }

    public Company getRequired(long userId) {
        return companies.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Профиль компании не найден"));
    }

    /** Creates or updates the caller's company profile; idempotent, always 200 at the web layer. */
    @Transactional
    public Company upsert(long userId, CompanyCommand command) {
        validate(command);
        return companies.upsert(userId, new CompanyCommand(
                command.name().trim(),
                trimmed(command.description()),
                trimmed(command.industry()),
                trimmed(command.contactEmail()),
                trimmed(command.contactPhone()),
                trimmed(command.website())));
    }

    private static void validate(CompanyCommand command) {
        if (command == null || command.name() == null || command.name().isBlank()) {
            throw ApiException.validation("name обязателен");
        }
        String email = command.contactEmail();
        if (email != null && !email.isBlank() && !EMAIL.matcher(email.trim()).matches()) {
            throw ApiException.validation("contact_email некорректен");
        }
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
