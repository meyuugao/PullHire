package me.yuugao.pullhire.employer.application;

import me.yuugao.pullhire.employer.domain.Company;
import me.yuugao.pullhire.employer.domain.Need;
import me.yuugao.pullhire.employer.domain.NeedCommand;
import me.yuugao.pullhire.employer.error.ApiException;
import me.yuugao.pullhire.employer.repo.CompanyRepository;
import me.yuugao.pullhire.employer.repo.NeedRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Owner-scoped need CRUD: happy paths, 403/404 access checks, and validation, with mocked repositories. */
class NeedServiceTest {

    private CompanyRepository companies;
    private NeedRepository needs;
    private EventPublisher events;
    private NeedService service;

    @BeforeEach
    void setUp() {
        companies = mock(CompanyRepository.class);
        needs = mock(NeedRepository.class);
        events = mock(EventPublisher.class);
        service = new NeedService(companies, needs, events);
        when(companies.findByUserId(1L)).thenReturn(Optional.of(company(7L, 1L)));
    }

    @Test
    void createStoresNeedUnderCallerCompany() {
        when(needs.insert(eq(7L), any())).thenReturn(need(11L, 7L, "Backend", "active"));

        Need result = service.create(1L, command("Backend", "active"));

        assertThat(result.id()).isEqualTo(11L);
        verify(needs).insert(eq(7L), any());
    }

    @Test
    void createWithoutStatusDefaultsToActive() {
        when(needs.insert(eq(7L), any())).thenReturn(need(11L, 7L, "Backend", "active"));

        Need result = service.create(1L, command("Backend", null));

        assertThat(result.status()).isEqualTo("active");
    }

    @Test
    void listReturnsOwnerPage() {
        when(needs.findByCompanyId(eq(7L), any(int.class), any(long.class), any(), any(boolean.class)))
                .thenReturn(List.of(need(11L, 7L, "Backend", "active")));
        when(needs.countByCompanyId(7L)).thenReturn(1L);

        NeedService.NeedPage page = service.list(1L, 0, 20, "created_at,desc");

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.page()).isEqualTo(0);
    }

    @Test
    void getReturnsOwnerNeed() {
        when(needs.findById(11L)).thenReturn(Optional.of(need(11L, 7L, "Backend", "active")));

        assertThat(service.get(1L, 11L).title()).isEqualTo("Backend");
    }

    @Test
    void getUnknownIdIsNotFound() {
        when(needs.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L, 99L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("not_found");
                    assertThat(ex.status().value()).isEqualTo(404);
                });
    }

    @Test
    void getForeignNeedIsForbidden() {
        when(companies.findByUserId(2L)).thenReturn(Optional.of(company(9L, 2L)));
        when(needs.findById(11L)).thenReturn(Optional.of(need(11L, 7L, "Backend", "active")));

        assertThatThrownBy(() -> service.get(2L, 11L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("forbidden");
                    assertThat(ex.status().value()).isEqualTo(403);
                });
    }

    @Test
    void updateReplacesOwnerNeed() {
        when(needs.findById(11L)).thenReturn(Optional.of(need(11L, 7L, "Backend", "active")));
        when(needs.update(eq(11L), eq(7L), any())).thenReturn(1);
        when(needs.findById(11L)).thenReturn(Optional.of(need(11L, 7L, "Backend", "active")))
                .thenReturn(Optional.of(need(11L, 7L, "Backend v2", "archived")));

        Need result = service.update(1L, 11L, command("Backend v2", "archived"));

        assertThat(result.title()).isEqualTo("Backend v2");
        verify(needs).update(eq(11L), eq(7L), any());
    }

    @Test
    void updateWithNullStatusKeepsStoredStatus() {
        when(needs.findById(11L)).thenReturn(Optional.of(need(11L, 7L, "Backend", "archived")))
                .thenReturn(Optional.of(need(11L, 7L, "Backend v2", "archived")));
        when(needs.update(eq(11L), eq(7L), any())).thenReturn(1);

        Need result = service.update(1L, 11L, command("Backend v2", null));

        assertThat(result.status()).isEqualTo("archived");
        verify(needs).update(eq(11L), eq(7L), argThat(cmd -> "archived".equals(cmd.status())));
    }

    @Test
    void updateForeignIsForbidden() {
        when(companies.findByUserId(2L)).thenReturn(Optional.of(company(9L, 2L)));
        when(needs.findById(11L)).thenReturn(Optional.of(need(11L, 7L, "Backend", "active")));

        assertThatThrownBy(() -> service.update(2L, 11L, command("X", "active")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("forbidden"));
    }

    @Test
    void updateUnknownIsNotFound() {
        when(needs.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, 99L, command("X", "active")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("not_found"));
    }

    @Test
    void deleteRemovesOwnerNeed() {
        when(needs.findById(11L)).thenReturn(Optional.of(need(11L, 7L, "Backend", "active")));
        when(needs.delete(11L, 7L)).thenReturn(1);

        service.delete(1L, 11L);

        verify(needs).delete(11L, 7L);
        verify(events).publishNeedDeleted("11", "7", "1");
    }

    @Test
    void deleteForeignIsForbidden() {
        when(companies.findByUserId(2L)).thenReturn(Optional.of(company(9L, 2L)));
        when(needs.findById(11L)).thenReturn(Optional.of(need(11L, 7L, "Backend", "active")));

        assertThatThrownBy(() -> service.delete(2L, 11L))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("forbidden"));
        verify(needs, never()).delete(11L, 9L);
        verifyNoInteractions(events);
    }

    @Test
    void deleteUnknownIsNotFound() {
        when(needs.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 99L))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("not_found"));
        verifyNoInteractions(events);
    }

    @Test
    void callerWithoutCompanyIsNotFound() {
        when(companies.findByUserId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(5L, command("Backend", "active")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("not_found");
                    assertThat(ex.getMessage()).isEqualTo("Профиль компании не найден");
                });
    }

    @Test
    void blankTitleIsValidationError() {
        assertThatThrownBy(() -> service.create(1L, command("   ", "active")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("validation_error");
                    assertThat(ex.status().value()).isEqualTo(400);
                });
    }

    @Test
    void invalidStatusIsValidationError() {
        assertThatThrownBy(() -> service.create(1L, command("Backend", "draft")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("validation_error");
                    assertThat(ex.status().value()).isEqualTo(400);
                });
    }

    private static NeedCommand command(String title, String status) {
        return new NeedCommand(title, "About", "backend", "middle", new String[]{"java"}, status);
    }

    private static Company company(long id, long userId) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Company(id, userId, "Acme", "About us", "IT", "hr@acme.example", "+7",
                "https://acme.example", now, now);
    }

    private static Need need(long id, long companyId, String title, String status) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Need(id, companyId, title, "About", "backend", "middle",
                new String[]{"java"}, status, now, now);
    }
}
