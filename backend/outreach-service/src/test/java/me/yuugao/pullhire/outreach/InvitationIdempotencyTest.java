package me.yuugao.pullhire.outreach;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.FilterChain;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import me.yuugao.pullhire.events.OutboxWriter;
import me.yuugao.pullhire.idempotency.IdempotencyFilter;
import me.yuugao.pullhire.idempotency.IdempotencyResultStore;
import me.yuugao.pullhire.outreach.application.CategoryCheckClient;
import me.yuugao.pullhire.outreach.application.EventPublisher;
import me.yuugao.pullhire.outreach.application.InvitationService;
import me.yuugao.pullhire.outreach.application.InvitationService.CreateCommand;
import me.yuugao.pullhire.outreach.config.AppProperties;
import me.yuugao.pullhire.outreach.domain.Invitation;
import me.yuugao.pullhire.outreach.error.ApiException;
import me.yuugao.pullhire.outreach.repo.InvitationEventRepository;
import me.yuugao.pullhire.outreach.repo.InvitationRepository;
import me.yuugao.pullhire.outreach.web.dto.InvitationResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ST-9.3 — idempotency-lib wiring for POST /invitations creation only
 * (docs/api.md §1, decision C): transitions stay key-less.
 *
 * <p>Runs only when {@code OUTREACH_TEST_DB_URL} (or
 * {@code -Dpullhire.test.db.url}) is set; otherwise the class is skipped.
 * No Spring context: {@link IdempotencyResultStore} +
 * {@link IdempotencyFilter} are constructed directly over the migrated schema
 * (V5 picked up by {@link OutreachTestDb#migrate}), downstream is the real
 * {@link InvitationService} with stubbed {@link CategoryCheckClient} /
 * {@link EventPublisher} (mirrors {@link InvitationFlowTest}).
 */
class InvitationIdempotencyTest {

    private static final JsonMapper JSON = new JsonMapper();

    private static String url;

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate named;
    private InvitationService service;
    private IdempotencyFilter filter;

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
        jdbc.execute("TRUNCATE invitations, invitation_events, outbox_events, processed_events, idempotency_keys "
                + "RESTART IDENTITY CASCADE");
        CategoryCheckClient categories = new CategoryCheckClient(new AppProperties()) {
            @Override
            public boolean hasCategory(long candidateId) {
                return true;
            }
        };
        EventPublisher publisher = new EventPublisher(new OutboxEventPublisher(new OutboxWriter(jdbc)));
        service = new InvitationService(new InvitationRepository(named),
                new InvitationEventRepository(named), categories, publisher);
        filter = new IdempotencyFilter(new IdempotencyResultStore(jdbc, null), List.of("/invitations"));
    }

    @Test
    void firstPostWithKeyCreatesAndRepeatReplaysWithoutNewWrites() throws Exception {
        CreateCommand command = validCommand(601L);
        FilterChain create = createChain(command);

        MockHttpServletResponse first = post("/invitations", "idem-key-1", create);
        assertThat(first.getStatus()).isEqualTo(201);
        String firstBody = first.getContentAsString();
        assertThat(firstBody).contains("\"status\":\"sent\"");
        assertCounts(1, 1, 1);

        MockHttpServletResponse replay = post("/invitations", "idem-key-1", create);
        assertThat(replay.getStatus()).isEqualTo(201);
        assertThat(replay.getContentAsString()).isEqualTo(firstBody);
        assertCounts(1, 1, 1);
    }

    @Test
    void missingKeyReturns409NestedEnvelopeWithoutWrite() throws Exception {
        FilterChain create = createChain(validCommand(602L));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/invitations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, create);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"error\"").contains("\"code\":\"conflict\"");
        assertCounts(0, 0, 0);
    }

    @Test
    void failedValidationIsNotStoredAndCorrectedRetrySucceeds() throws Exception {
        CreateCommand invalid = new CreateCommand(603L, null, null, "  ",
                "Acme", new BigDecimal("100000"), new BigDecimal("150000"),
                "gross", "hr@acme.test", null, null);
        MockHttpServletResponse failed = post("/invitations", "idem-key-fix", createChain(invalid));
        assertThat(failed.getStatus()).isEqualTo(400);
        assertThat(failed.getContentAsString()).contains("validation_error");
        assertCounts(0, 0, 0);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM idempotency_keys WHERE idempotency_key = ?", Long.class, "idem-key-fix"))
                .isZero();

        MockHttpServletResponse retried = post("/invitations", "idem-key-fix", createChain(validCommand(603L)));
        assertThat(retried.getStatus()).isEqualTo(201);
        assertCounts(1, 1, 1);
    }

    @Test
    void acceptTransitionDoesNotRequireKey() throws Exception {
        Invitation created = service.create(11L, validCommand(604L));
        assertCounts(1, 1, 1);

        long invitationId = created.id();
        FilterChain accept = (req, res) -> {
            Invitation accepted = service.accept(invitationId, 604L);
            byte[] body = JSON.writeValueAsString(InvitationResponse.from(accepted))
                    .getBytes(StandardCharsets.UTF_8);
            res.setContentType("application/json");
            res.getOutputStream().write(body);
        };

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/invitations/" + invitationId + "/accept");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, accept);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"status\":\"accepted\"");
    }

    private FilterChain createChain(CreateCommand command) {
        return (req, res) -> {
            try {
                Invitation created = service.create(11L, command);
                byte[] body = JSON.writeValueAsString(InvitationResponse.from(created))
                        .getBytes(StandardCharsets.UTF_8);
                res.setContentType("application/json");
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201);
                res.getOutputStream().write(body);
            } catch (ApiException ex) {
                byte[] body = ("{\"error\":{\"code\":\"" + ex.code() + "\",\"message\":\""
                        + ex.getMessage() + "\"}}").getBytes(StandardCharsets.UTF_8);
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(ex.status().value());
                res.setContentType("application/problem+json");
                res.getOutputStream().write(body);
            }
        };
    }

    private MockHttpServletResponse post(String uri, String key, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        if (key != null) {
            request.addHeader(IdempotencyFilter.HEADER, key);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private void assertCounts(long invitations, long sentEvents, long outbox) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invitations", Long.class)).isEqualTo(invitations);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM invitation_events WHERE event_type = 'sent'", Long.class))
                .isEqualTo(sentEvents);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isEqualTo(outbox);
    }

    private static CreateCommand validCommand(long candidateId) {
        return new CreateCommand(candidateId, null, null, "Приглашаем в команду",
                "Acme", new BigDecimal("100000"), new BigDecimal("150000"),
                "gross", "hr@acme.test", null, null);
    }
}
