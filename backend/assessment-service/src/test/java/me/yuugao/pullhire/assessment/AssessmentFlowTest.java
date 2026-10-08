package me.yuugao.pullhire.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.yuugao.pullhire.assessment.application.AnswerService;
import me.yuugao.pullhire.assessment.application.CategoryAssignmentService;
import me.yuugao.pullhire.assessment.application.EventPublisher;
import me.yuugao.pullhire.assessment.application.GradeChoiceService;
import me.yuugao.pullhire.assessment.application.GradeEligibilityService;
import me.yuugao.pullhire.assessment.application.GradeEligibilityService.GradeEligibility;
import me.yuugao.pullhire.assessment.application.SessionStartService;
import me.yuugao.pullhire.assessment.application.SurveyService;
import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.GradeDirection;
import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.ItemKind;
import me.yuugao.pullhire.assessment.domain.ItemStatus;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.domain.form.RoutingRules;
import me.yuugao.pullhire.assessment.domain.scoring.GradeBandClassifier;
import me.yuugao.pullhire.assessment.domain.scoring.GradeBandClassifier.GradeBand;
import me.yuugao.pullhire.assessment.repo.AttemptRepository;
import me.yuugao.pullhire.assessment.repo.CooldownRepository;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository.GradeHistoryEntry;
import me.yuugao.pullhire.assessment.repo.ItemBankRepository;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import me.yuugao.pullhire.assessment.web.dto.AnswerRequest;
import me.yuugao.pullhire.assessment.web.dto.AnswerResponse;
import me.yuugao.pullhire.assessment.web.dto.SessionStartResponse;
import me.yuugao.pullhire.assessment.web.dto.SurveyRequest;
import me.yuugao.pullhire.events.EventNames;
import me.yuugao.pullhire.events.OutboxEventPublisher;
import me.yuugao.pullhire.events.OutboxWriter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end path survey → grade-choice → session → category → outbox
 * ({@code docs/testing_model.md §1, §5.2, §6.3, §7.2–7.3, §8}): the real
 * services run against PostgreSQL — two full attempts (initial + retry) each
 * publish {@code assessment.category_assigned}; the SE stop rule respects
 * min/max; an {@code up} move burns the 1-month cooldown so a further attempt
 * is refused; {@code grade_history} stays append-only with no forced
 * downgrade; a boundary estimate routes to {@code needs_review}.
 *
 * <p>Runs only when a PostgreSQL URL is provided via
 * {@code -Dpullhire.test.db.url} or {@code ASSESSMENT_TEST_DB_URL}; otherwise
 * the class is skipped. No extra dependency is introduced.
 */
class AssessmentFlowTest {

    private static final JsonMapper JSON = new JsonMapper();
    private static final long CANDIDATE = 42L;

    private static String url;

    private JdbcTemplate jdbc;
    private TestSessionRepository sessions;
    private AttemptRepository attempts;
    private ItemBankRepository items;
    private GradeHistoryRepository history;
    private CooldownRepository cooldowns;
    private AssessmentProperties properties;
    private SurveyService survey;
    private GradeChoiceService choices;
    private SessionStartService starts;
    private CategoryAssignmentService categories;
    private AnswerService answers;
    private GradeEligibilityService eligibility;

    @BeforeAll
    static void setUpSchema() {
        url = firstNonBlank(System.getProperty("pullhire.test.db.url"), System.getenv("ASSESSMENT_TEST_DB_URL"));
        Assumptions.assumeTrue(url != null, "ASSESSMENT_TEST_DB_URL not set: DB-backed flow checks skipped");
        Flyway.configure()
                .dataSource(url, System.getProperty("pullhire.test.db.user"),
                        System.getProperty("pullhire.test.db.password"))
                .schemas("assessment")
                .defaultSchema("assessment")
                .locations("classpath:db/migration/assessment")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void reset() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE test_sessions, attempts, item_bank, grade_history, cooldowns, "
                + "outbox_events RESTART IDENTITY CASCADE");
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
        sessions = new TestSessionRepository(named);
        attempts = new AttemptRepository(named);
        items = new ItemBankRepository(named);
        history = new GradeHistoryRepository(named);
        cooldowns = new CooldownRepository(named);
        properties = new AssessmentProperties();
        properties.setMinItems(3);
        properties.setMaxItems(5);
        properties.setSeThreshold(10.0);
        properties.setCiConfidence(0.01);
        properties.getCutScores().put("senior", 0.5);
        EventPublisher events = new EventPublisher(new OutboxEventPublisher(new OutboxWriter(jdbc)));
        survey = new SurveyService(sessions, items, properties);
        eligibility = new GradeEligibilityService(cooldowns);
        choices = new GradeChoiceService(sessions, properties, eligibility);
        starts = new SessionStartService(sessions, items, attempts, properties);
        categories = new CategoryAssignmentService(sessions, history, cooldowns, events, properties, eligibility);
        answers = new AnswerService(sessions, items, attempts, categories, properties);
        seedBank(12);
    }

    @Test
    void surveyToOutboxPublishesCategoryAssignedForInitialAndRetry() {
        AnswerResponse first = runAttempt(CANDIDATE, true);
        AnswerResponse retry = runAttempt(CANDIDATE, true);

        assertThat(first.completed()).isTrue();
        assertThat(retry.completed()).isTrue();
        assertThat(categoryAssignedCount(sessionOf(first))).isEqualTo(1);
        assertThat(categoryAssignedCount(sessionOf(retry))).isEqualTo(1);

        List<GradeHistoryEntry> rows = history.listByCandidate(CANDIDATE);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).direction()).isEqualTo(GradeDirection.INITIAL);
    }

    @Test
    void seStopRespectsMinAndMax() {
        double threshold = properties.getSeThreshold();
        int min = properties.getMinItems();
        int max = properties.getMaxItems();

        assertThat(RoutingRules.shouldStop(0.0, 0.0, min - 1, threshold, min, max)).isFalse();
        assertThat(RoutingRules.shouldStop(0.0, threshold, min, threshold, min, max)).isTrue();
        assertThat(RoutingRules.shouldStop(0.0, threshold + 5.0, min, threshold, min, max)).isFalse();
        assertThat(RoutingRules.shouldStop(0.0, Double.MAX_VALUE, max, threshold, min, max)).isTrue();
        assertThat(RoutingRules.shouldStop(0.0, Double.MAX_VALUE, max + 4, threshold, min, max)).isTrue();

        AnswerResponse completed = runAttempt(CANDIDATE, true);
        assertThat(completed.completed()).isTrue();
        assertThat(completed.itemCount()).isBetween(min, max);
    }

    @Test
    void secondAttemptInsideCooldownIsRefused() {
        runAttempt(CANDIDATE, true);
        runAttempt(CANDIDATE, true);

        long promotion = survey.submit(CANDIDATE, surveyRequest());
        CategoryAssignmentService.Outcome up =
                categories.complete(promotion, CANDIDATE, 5.0, 0.1);
        assertThat(up.manualReview()).isFalse();
        assertThat(up.direction()).isEqualTo(GradeDirection.UP);

        GradeEligibility blocked = eligibility.check(CANDIDATE);
        assertThat(blocked.eligible()).isFalse();
        assertThat(blocked.availableAfter()).isAfter(Instant.now());
        assertThat(cooldowns.findActive(CANDIDATE)).isPresent();
    }

    @Test
    void gradeHistoryAppendOnlyWithNoForcedDowngrade() {
        runAttempt(CANDIDATE, true);
        List<GradeHistoryEntry> baseline = history.listByCandidate(CANDIDATE);
        assertThat(baseline).hasSize(1);
        String current = baseline.get(0).grade();

        long recheck = survey.submit(CANDIDATE, surveyRequest());
        CategoryAssignmentService.Outcome outcome =
                categories.complete(recheck, CANDIDATE, -3.0, 0.1);

        assertThat(outcome.manualReview()).isFalse();
        assertThat(outcome.grade()).isEqualTo(current);
        assertThat(outcome.direction()).isEqualTo(GradeDirection.RECHECK);
        List<GradeHistoryEntry> rows = history.listByCandidate(CANDIDATE);
        assertThat(rows).hasSize(baseline.size() + 1);
        assertThat(rows.get(rows.size() - 1).grade()).isEqualTo(current);
        assertThat(rows).extracting(GradeHistoryEntry::grade).contains(current);
    }

    @Test
    void boundaryEstimateNeedsReview() {
        double middleCut = properties.getCutScores().get("middle");

        GradeBand boundary = GradeBandClassifier.classify(middleCut, 0.1,
                properties.getCutScores(), properties.getSeThreshold(), 1.96);
        assertThat(boundary.needsReview()).isTrue();

        GradeBand precise = GradeBandClassifier.classify(0.0, 0.1,
                properties.getCutScores(), properties.getSeThreshold(),
                properties.getCiConfidence());
        assertThat(precise.needsReview()).isFalse();
        assertThat(precise.grade()).isEqualTo("middle");
    }

    @Test
    void exposureCapRetiresAndExcludesAtFlowLevel() {
        jdbc.update("UPDATE item_bank SET exposure_limit = 1 WHERE specialization_code = 'backend'");
        Long capped = jdbc.queryForObject(
                "SELECT count(*) FROM item_bank WHERE exposure_limit IS NOT NULL", Long.class);
        assertThat(capped).isGreaterThanOrEqualTo((long) properties.getMaxItems());
        items.insert(new Item(0L, ItemKind.TEMPLATE, "flow-null-fam",
                "flow-concept", "backend", BigDecimal.valueOf(-2.0), true, null, 0,
                "{\"model\":{\"format\":\"single_choice\",\"statement\":\"Choose one.\","
                        + "\"options\":[\"option-a\",\"option-b\",\"option-c\",\"option-d\"]},"
                        + "\"key\":{\"correct\":\"option-b\"}}",
                ItemStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH));

        AnswerResponse completed = runAttempt(CANDIDATE, true);
        assertThat(completed.completed()).isTrue();

        Long retired = jdbc.queryForObject(
                "SELECT count(*) FROM item_bank WHERE status = 'retired'", Long.class);
        assertThat(retired).isGreaterThanOrEqualTo(1L);
        Long exposed = jdbc.queryForObject(
                "SELECT count(*) FROM item_bank WHERE exposure_count > 0", Long.class);
        assertThat(exposed).isGreaterThanOrEqualTo(1L);
        List<Long> retiredIds = jdbc.query("SELECT id FROM item_bank WHERE status = 'retired'",
                (rs, rowNum) -> rs.getLong("id"));
        List<Long> activeIds = items.findActiveBySpecialization("backend").stream()
                .map(Item::id).toList();
        assertThat(activeIds).doesNotContainAnyElementsOf(retiredIds);
        Long nullLimit = jdbc.queryForObject(
                "SELECT id FROM item_bank WHERE template_family = 'flow-null-fam'", Long.class);
        assertThat(nullLimit).isNotNull();
        assertThat(items.isExposureExhausted(nullLimit)).isFalse();
    }

    @Test
    void boundaryEstimateEndsNeedsReviewThroughRunningFlow() {
        properties.setSeThreshold(0.01);
        properties.setCiConfidence(1.96);
        double middleCut = properties.getCutScores().get("middle");

        long sessionId = survey.submit(CANDIDATE, surveyRequest());
        choices.choose(sessionId, CANDIDATE, "middle");
        CategoryAssignmentService.Outcome outcome =
                categories.complete(sessionId, CANDIDATE, middleCut, 0.005);

        assertThat(outcome.manualReview()).isTrue();
        assertThat(sessions.findById(sessionId)).isPresent();
        assertThat(sessions.findById(sessionId).get().status()).isEqualTo(SessionStatus.NEEDS_REVIEW);
        assertThat(categoryAssignedCount(sessionId)).isZero();
        assertThat(history.listByCandidate(CANDIDATE)).isEmpty();
    }

    @Test
    void secondGradeChangeAfterCooldownBurnIsRefusedThroughRealPath() {
        runAttempt(CANDIDATE, true);
        runAttempt(CANDIDATE, true);

        long promotion = survey.submit(CANDIDATE, surveyRequest());
        CategoryAssignmentService.Outcome up =
                categories.complete(promotion, CANDIDATE, 5.0, 0.1);
        assertThat(up.manualReview()).isFalse();
        assertThat(up.direction()).isEqualTo(GradeDirection.UP);
        assertThat(cooldowns.findActive(CANDIDATE)).isPresent();
        List<GradeHistoryEntry> before = history.listByCandidate(CANDIDATE);
        String current = before.get(before.size() - 1).grade();

        long second = survey.submit(CANDIDATE, surveyRequest());
        assertThatThrownBy(() -> choices.choose(second, CANDIDATE, "senior"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(409));

        CategoryAssignmentService.Outcome retained =
                categories.complete(second, CANDIDATE, 5.0, 0.1);
        assertThat(retained.manualReview()).isFalse();
        assertThat(retained.direction()).isEqualTo(GradeDirection.RECHECK);
        assertThat(retained.grade()).isEqualTo(current);
    }

    /** One full attempt with every served item answered (correctly or not) until completion. */
    private AnswerResponse runAttempt(long candidateId, boolean answerCorrectly) {
        long sessionId = survey.submit(candidateId, surveyRequest());
        choices.choose(sessionId, candidateId, "middle");
        SessionStartResponse start = starts.start(sessionId, candidateId);
        String currentItem = start.item().itemId();
        AnswerResponse response = null;
        for (int guard = 0; guard < properties.getMaxItems() + 2; guard++) {
            response = answers.answer(sessionId, candidateId,
                    new AnswerRequest(currentItem, answer(answerCorrectly), 500, null));
            if (response.completed()) {
                return response;
            }
            currentItem = response.item().itemId();
        }
        throw new IllegalStateException("Attempt did not complete within the max-items bound");
    }

    private static JsonNode answer(boolean correct) {
        return JSON.readTree(correct ? "\"option-b\"" : "\"option-a\"");
    }

    private static long sessionOf(AnswerResponse response) {
        return Long.parseLong(response.sessionId());
    }

    private long categoryAssignedCount(long sessionId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = ? AND payload->>'session_id' = ?",
                Long.class, EventNames.ASSESSMENT_CATEGORY_ASSIGNED, Long.toString(sessionId));
        return count == null ? 0L : count;
    }

    private static SurveyRequest surveyRequest() {
        return new SurveyRequest("it", "backend", "middle",
                List.of("backend", "middle"), Map.of("q", 1));
    }

    /** Easy single-choice items (b = -2.0): correct answers keep θ near the
     * middle band, so a later high-θ completion deterministically moves up. */
    private void seedBank(int count) {
        List<Item> bank = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            bank.add(new Item(0L, ItemKind.TEMPLATE, "flow-fam-" + index,
                    "flow-concept", "backend", BigDecimal.valueOf(-2.0), true, null, 0,
                    "{\"model\":{\"format\":\"single_choice\",\"statement\":\"Choose one.\","
                            + "\"options\":[\"option-a\",\"option-b\",\"option-c\",\"option-d\"]},"
                            + "\"key\":{\"correct\":\"option-b\"}}",
                    ItemStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH));
        }
        items.seedAll(bank);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
