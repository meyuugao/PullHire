package me.yuugao.pullhire.employer.web;

import me.yuugao.pullhire.employer.application.CompanyService;
import me.yuugao.pullhire.employer.web.dto.CompanyRequest;
import me.yuugao.pullhire.employer.web.dto.CompanyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET|PUT /employer/company} ({@code docs/api.md §3.6}): the employer
 * company profile ({@code docs/data_model.md §8.1}). Owner-scoped via the JWT
 * {@code sub}; PUT is idempotent — it creates or replaces the caller's profile
 * in one upsert and always returns 200 with the updated body, never 201. GET
 * returns 404 {@code not_found} when the caller has no profile yet.
 * {@code @Valid} failures surface through {@link ApiExceptionHandler} as the
 * unified problem+json envelope (400 {@code validation_error}).
 */
@RestController
@RequestMapping("/employer/company")
public class CompanyController {

    private final CompanyService companies;

    public CompanyController(CompanyService companies) {
        this.companies = companies;
    }

    @GetMapping
    public CompanyResponse get(HttpServletRequest request) {
        return CompanyResponse.from(companies.getRequired(CurrentUser.id(request)));
    }

    @PutMapping
    public CompanyResponse put(@Valid @RequestBody CompanyRequest body, HttpServletRequest request) {
        return CompanyResponse.from(companies.upsert(CurrentUser.id(request), body.toCommand()));
    }
}
