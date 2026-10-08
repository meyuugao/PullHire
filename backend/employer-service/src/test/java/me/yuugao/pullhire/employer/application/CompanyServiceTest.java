package me.yuugao.pullhire.employer.application;

import me.yuugao.pullhire.employer.domain.Company;
import me.yuugao.pullhire.employer.domain.CompanyCommand;
import me.yuugao.pullhire.employer.error.ApiException;
import me.yuugao.pullhire.employer.repo.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Company upsert semantics (create vs. update, owner isolation) and validation, with a mocked repository. */
class CompanyServiceTest {

    private CompanyRepository companies;
    private CompanyService service;

    @BeforeEach
    void setUp() {
        companies = mock(CompanyRepository.class);
        service = new CompanyService(companies);
    }

    @Test
    void firstUpsertCreatesRowForOwner() {
        when(companies.upsert(eq(1L), any())).thenReturn(company(7L, 1L, "Acme"));

        Company result = service.upsert(1L, command("Acme", "hr@acme.example"));

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.name()).isEqualTo("Acme");
        ArgumentCaptor<CompanyCommand> sent = ArgumentCaptor.forClass(CompanyCommand.class);
        verify(companies).upsert(eq(1L), sent.capture());
        assertThat(sent.getValue().name()).isEqualTo("Acme");
    }

    @Test
    void secondUpsertUpdatesSameRow() {
        when(companies.upsert(eq(1L), any())).thenReturn(company(7L, 1L, "Acme v2"));

        Company result = service.upsert(1L, command("Acme v2", "hr@acme.example"));

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.name()).isEqualTo("Acme v2");
    }

    @Test
    void otherUserSeesNeitherRowNorWrite() {
        when(companies.findByUserId(2L)).thenReturn(Optional.empty());
        when(companies.upsert(eq(2L), any())).thenReturn(company(9L, 2L, "Beta"));

        assertThatThrownBy(() -> service.getRequired(2L))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("not_found"));

        Company created = service.upsert(2L, command("Beta", null));

        assertThat(created.userId()).isEqualTo(2L);
        verify(companies).upsert(eq(2L), any());
    }

    @Test
    void getRequiredReturnsOwnerProfile() {
        when(companies.findByUserId(1L)).thenReturn(Optional.of(company(7L, 1L, "Acme")));

        assertThat(service.getRequired(1L).name()).isEqualTo("Acme");
    }

    @Test
    void getBeforeAnyPutIsNotFound() {
        when(companies.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRequired(1L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("not_found");
                    assertThat(ex.status().value()).isEqualTo(404);
                });
    }

    @Test
    void blankNameIsValidationError() {
        assertThatThrownBy(() -> service.upsert(1L, command("   ", null)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("validation_error");
                    assertThat(ex.status().value()).isEqualTo(400);
                });
    }

    @Test
    void badEmailIsValidationError() {
        assertThatThrownBy(() -> service.upsert(1L, command("Acme", "not-an-email")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("validation_error");
                    assertThat(ex.status().value()).isEqualTo(400);
                });
    }

    private static CompanyCommand command(String name, String email) {
        return new CompanyCommand(name, "About us", "IT", email, "+7", "https://acme.example");
    }

    private static Company company(long id, long userId, String name) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Company(id, userId, name, "About us", "IT", "hr@acme.example", "+7",
                "https://acme.example", now, now);
    }
}
