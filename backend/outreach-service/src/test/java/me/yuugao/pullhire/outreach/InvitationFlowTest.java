package me.yuugao.pullhire.outreach;

import java.math.BigDecimal;
import java.util.Map;

import me.yuugao.pullhire.events.OutboxEventPublisher;
import me.yuugao.pullhire.events.OutboxWriter;
import me.yuugao.pullhire.outreach.application.CategoryCheckClient;
import me.yuugao.pullhire.outreach.application.EventPublisher;
import me.yuugao.pullhire.outreach.application.InvitationService;
import me.yuugao.pullhire.outreach.application.InvitationService.CreateCommand;
import me.yuugao.pullhire.outreach.config.AppProperties;
import me.yuugao.pullhire.outreach.domain.Invitation;
import me.yuugao.pullhire.outreach.error.ApiException;
import me.yuugao.pullhire.outreach.repo.InvitationEventRepository;
import me.yuugao.pullhire.outreach.repo.InvitationRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * ST-8.12 — invitation validation + lifecycle over a real PostgreSQL
 * (criteria 1, 2, 4): salary guards, contact-snapshot guards, category gate,
 * one audit row per transition, illegal transitions as 409 with no audit row.
 *
 * <p>Runs only when {@code OUTREACH_TEST_DB_URL} (or
 * {@code -Dpullhire.test.db.url}) is set; otherwise the class is skipped.
 * Direct service/repo invocation — no Spring context, no Testcontainers.
 * The category check is stubbed by subclassing {@link CategoryCheckClient}
 * (no HTTP).
 */
class InvitationFlowTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static String url;

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate named;
    private InvitationRepository invitations;

    @BeforeAll
    static void setUpSchema() {
        url = OutreachTestDb.requireDb();
        OutreachTestDb.migrate(url);
    }

    @BeforeEach
    void reset() {
        DriverManagerDataSource dataSource = OutreachTestDb.dataSource(url);
        jdbc = new JdbcTemplate(dataSource);
        named = new NamedParameterJdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE invitations, invitation_events, outbox_events, processed_events "
                + "RESTART IDENTITY CASCADE");
        invitations = new InvitationRepository(named);
    }

    @Test
    void createWithoutSalaryRangeFailsValidation() {
        InvitationService service = service(true);

        // salary_to < salary_from.
        Throwable inverted = catchThrowable(() -> service.create(11L, command(501L)
                .salaryFrom(new BigDecimal("150000")).salaryTo(new BigDecimal("100000")).build()));
        assertValidation(inverted);

        // Missing salary bound.
        Throwable missing = catchThrowable(() -> service.create(11L, command(501L)
                .salaryFrom(null).salaryTo(new BigDecimal("100000")).build()));
        assertValidation(missing);

        // Unknown salary basis.
        Throwable basis = catchThrowable(() -> service.create(11L, command(501L)
                .salaryBasis("brutto").build()));
        assertValidation(basis);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invitations", Long.class)).isZero();
    }

    @Test
    void createWithoutContactChannelsFailsButSingleChannelStoresSnapshotVerbatim() {
        InvitationService service = service(true);

        Throwable noContact = catchThrowable(() -> service.create(11L, command(502L)
                .contactEmail(null).contactPhone("  ").website(null).build()));
        assertValidation(noContact);

        Invitation created = service.create(11L, command(502L)
                .contactEmail("hr@acme.test").contactPhone(null).website(null).build());

        assertThat(created.status()).isEqualTo("sent");
        assertThat(created.releasedContact()).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = JSON.readValue(created.employerContact(), Map.class);
        assertThat(snapshot).containsExactlyInAnyOrderEntriesOf(
                Map.of("contact_email", "hr@acme.test", "contact_phone", null, "website", null));
        assertThat(eventCount(created.id(), "sent")).isOne();
    }

    @Test
    void createForCandidateWithoutCategoryFailsButWithCategorySucceeds() {
        Throwable noCategory = catchThrowable(() -> service(false).create(11L, command(503L).build()));
        assertThat(noCategory).isInstanceOf(ApiException.class);
        assertThat(((ApiException) noCategory).code()).isEqualTo("validation_error");
        assertThat(((ApiException) noCategory).status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(noCategory).hasMessageContaining("категори");

        Invitation created = service(true).create(11L, command(503L).build());
        assertThat(created.id()).isPositive();
        assertThat(created.status()).isEqualTo("sent");
    }

    @Test
    void lifecycleTransitionsAppendOneAuditRowEachAndIllegalTransitionConflictsWithoutAudit() {
        InvitationService service = service(true);
        Invitation created = service.create(11L, command(504L).build());

        // Unknown id is 404.
        Throwable unknown = catchThrowable(() -> service.markViewed(999_999L, 504L));
        assertThat(unknown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) unknown).code()).isEqualTo("not_found");

        // Foreign candidate is 403.
        Throwable foreign = catchThrowable(() -> service.markViewed(created.id(), 505L));
        assertThat(foreign).isInstanceOf(ApiException.class);
        assertThat(((ApiException) foreign).code()).isEqualTo("forbidden");

        Invitation viewed = service.markViewed(created.id(), 504L);
        assertThat(viewed.status()).isEqualTo("viewed");
        assertThat(eventCount(created.id(), "viewed")).isOne();
        assertThat(actorOf(created.id(), "viewed")).isEqualTo("candidate");

        Invitation accepted = service.accept(created.id(), 504L);
        assertThat(accepted.status()).isEqualTo("accepted");
        // Accept discloses nothing: release columns stay empty until contact_released.
        assertThat(accepted.releasedContact()).isNull();
        assertThat(accepted.contactReleasedAt()).isNull();
        assertThat(eventCount(created.id(), "accepted")).isOne();

        // Illegal transition: decline after accepted ⇒ 409, no audit row.
        Throwable conflict = catchThrowable(() -> service.decline(created.id(), 504L));
        assertThat(conflict).isInstanceOf(ApiException.class);
        assertThat(((ApiException) conflict).code()).isEqualTo("conflict");
        assertThat(((ApiException) conflict).status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(eventCount(created.id(), "declined")).isZero();

        // Second accept is also a conflict with no extra audit row.
        Throwable second = catchThrowable(() -> service.accept(created.id(), 504L));
        assertThat(((ApiException) second).code()).isEqualTo("conflict");
        assertThat(eventCount(created.id(), "accepted")).isOne();
    }

    @Test
    void declineFromSentAppendsExactlyOneDeclinedRow() {
        InvitationService service = service(true);
        Invitation created = service.create(11L, command(506L).build());

        Invitation declined = service.decline(created.id(), 506L);
        assertThat(declined.status()).isEqualTo("declined");
        assertThat(eventCount(created.id(), "declined")).isOne();
        assertThat(actorOf(created.id(), "declined")).isEqualTo("candidate");
        assertThat(eventCount(created.id(), "sent")).isOne();
    }

    private InvitationService service(boolean hasCategory) {
        CategoryCheckClient categories = new CategoryCheckClient(new AppProperties()) {
            @Override
            public boolean hasCategory(long candidateId) {
                return hasCategory;
            }
        };
        EventPublisher publisher = new EventPublisher(new OutboxEventPublisher(new OutboxWriter(jdbc)));
        return new InvitationService(invitations, new InvitationEventRepository(named), categories, publisher);
    }

    private long eventCount(long invitationId, String eventType) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM invitation_events WHERE invitation_id = ? AND event_type = ?",
                Long.class, invitationId, eventType);
        return count == null ? 0L : count;
    }

    private String actorOf(long invitationId, String eventType) {
        return jdbc.queryForObject(
                "SELECT actor_role FROM invitation_events WHERE invitation_id = ? AND event_type = ?",
                String.class, invitationId, eventType);
    }

    private static void assertValidation(Throwable thrown) {
        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).code()).isEqualTo("validation_error");
        assertThat(((ApiException) thrown).status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static CommandBuilder command(long candidateId) {
        return new CommandBuilder(candidateId);
    }

    private static final class CommandBuilder {
        private final long candidateId;
        private BigDecimal salaryFrom = new BigDecimal("100000");
        private BigDecimal salaryTo = new BigDecimal("150000");
        private String salaryBasis = "gross";
        private String contactEmail = "hr@acme.test";
        private String contactPhone;
        private String website;

        private CommandBuilder(long candidateId) {
            this.candidateId = candidateId;
        }

        CommandBuilder salaryFrom(BigDecimal value) {
            this.salaryFrom = value;
            return this;
        }

        CommandBuilder salaryTo(BigDecimal value) {
            this.salaryTo = value;
            return this;
        }

        CommandBuilder salaryBasis(String value) {
            this.salaryBasis = value;
            return this;
        }

        CommandBuilder contactEmail(String value) {
            this.contactEmail = value;
            return this;
        }

        CommandBuilder contactPhone(String value) {
            this.contactPhone = value;
            return this;
        }

        CommandBuilder website(String value) {
            this.website = value;
            return this;
        }

        CreateCommand build() {
            return new CreateCommand(candidateId, null, null, "Приглашаем в команду",
                    "Acme", salaryFrom, salaryTo, salaryBasis, contactEmail, contactPhone, website);
        }
    }
}
