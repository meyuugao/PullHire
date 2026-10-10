package me.yuugao.pullhire.assessment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.Attempt;
import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.domain.form.RoutingRules;
import me.yuugao.pullhire.assessment.domain.scoring.IrtScorer;
import me.yuugao.pullhire.assessment.domain.scoring.IrtScorer.Estimate;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.repo.AttemptRepository;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository;
import me.yuugao.pullhire.assessment.repo.ItemBankRepository;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import me.yuugao.pullhire.assessment.web.dto.AnswerRequest;
import me.yuugao.pullhire.assessment.web.dto.AnswerResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /assessment/sessions/{id}/answers} ({@code docs/api.md §3.4},
 * {@code docs/testing_model.md §5.1, §5.2, §4}): grades one served item,
 * persists the answer, advances the IRT-lite estimate ({@code θ}/{@code SE})
 * and serves the next item — or completes the attempt.
 *
 * <p>Answer flow: the request must address a served-but-unanswered attempt of
 * the session (answering an unserved item is 404, a terminal session is 409).
 * The answer is graded against the item {@code key} (auto-checkable formats
 * only; {@code code} items with {@code reference_tests} stay ungraded with a
 * {@code null} {@code is_correct} and do not move {@code θ}), then persisted
 * via {@code AttemptRepository.saveAnswer} together with {@code response_ms}
 * and the proctoring flags. The estimate is recomputed from scratch over all
 * answered attempts (initial {@code θ} from
 * {@code AssessmentProperties.startThetaByGrade} by declared grade, initial
 * {@code SE} {@code 1.0}), one {@link IrtScorer#update} step per graded item.
 *
 * <p>Routing: the next item is {@link RoutingRules#nextItem} (minimum
 * {@code |θ − b|}) over the active bank minus already-served and
 * exposure-exhausted items; the attempt stops via
 * {@link RoutingRules#shouldStop} (SE threshold after the minimum, forced at
 * the maximum). Every served item increments {@code exposure_count} (TD-5) and
 * retires the item once its limit is exhausted.
 *
 * <p>Completion: any stored proctoring flags route the session straight to
 * {@code needs_review} (never auto-fail, {@code §9.2}); otherwise
 * {@link CategoryAssignmentService#complete} classifies {@code θ + SE},
 * appends the grade history and publishes the category events (or routes a
 * borderline estimate to manual review, {@code §6.3}).
 *
 * <p>Idempotency ({@code docs/api.md §1}): the {@code Idempotency-Key} header
 * is enforced by the idempotency-lib filter backed by the persistent
 * {@code idempotency_keys} store (S9). Within the session the POST is
 * additionally replay-safe — re-posting an answer for an already-answered
 * item performs no repeated effect and returns the current progress instead
 * of a 409.
 *
 * <p>Anti-leak ({@code docs/testing_model.md §9.1}): the response carries the
 * same presentation-only surface as {@code SessionStartResponse} — the item
 * {@code model} node plus a deterministic option permutation. The answer
 * {@code key}, the difficulty {@code b}, scoring parameters and the raw
 * {@code variant_seed} never leave the service.
 */
@Service
public class AnswerService {

    private static final JsonMapper JSON = new JsonMapper();
    private static final double INITIAL_SE = 1.0;

    private final TestSessionRepository sessions;
    private final ItemBankRepository items;
    private final AttemptRepository attempts;
    private final CategoryAssignmentService categories;
    private final AssessmentProperties properties;
    private final GradeHistoryRepository gradeHistory;
    private final ProctoringService proctoring;

    public AnswerService(TestSessionRepository sessions, ItemBankRepository items,
                         AttemptRepository attempts, CategoryAssignmentService categories,
                         AssessmentProperties properties,
                         GradeHistoryRepository gradeHistory,
                         ProctoringService proctoring) {
        this.sessions = sessions;
        this.items = items;
        this.attempts = attempts;
        this.categories = categories;
        this.properties = properties;
        this.gradeHistory = gradeHistory;
        this.proctoring = proctoring;
    }

    /** Legacy seam for callers that do not track grade history or proctoring. */
    public AnswerService(TestSessionRepository sessions, ItemBankRepository items,
                         AttemptRepository attempts, CategoryAssignmentService categories,
                         AssessmentProperties properties) {
        this(sessions, items, attempts, categories, properties, null, null);
    }

    /**
     * Grades and persists one answer without an idempotency key.
     */
    @Transactional
    public AnswerResponse answer(long sessionId, long candidateId, AnswerRequest request) {
        return answer(sessionId, candidateId, request, null);
    }

    /**
     * Grades and persists one answer, serving the next item or completing.
     *
     * @param sessionId      {@code test_sessions.id}
     * @param candidateId    authenticated candidate; another candidate's
     *                       session is reported as missing
     * @param request        the answered item with the candidate's answer
     * @param idempotencyKey {@code Idempotency-Key} header value; accepted for
     *                       the {@code docs/api.md §1} contract but not yet
     *                       stored — in-session replay (already-answered item)
     *                       is a no-op returning current progress; a durable
     *                       key store is deferred (S9 zone)
     * @throws ApiException 404 when the session is missing/not owned or the
     *                      item was never served, 409 when the session is
     *                      already terminal, 400 for a malformed
     *                      {@code item_id}
     */
    @Transactional
    public AnswerResponse answer(long sessionId, long candidateId, AnswerRequest request,
                                 String idempotencyKey) {
        TestSession session = sessions.findById(sessionId)
                .filter(owned -> owned.candidateId() == candidateId)
                .orElseThrow(() -> ApiException.notFound("Сессия не найдена"));
        if (session.status() == SessionStatus.COMPLETED
                || session.status() == SessionStatus.ABANDONED
                || session.status() == SessionStatus.NEEDS_REVIEW) {
            throw ApiException.conflict("Сессия уже завершена");
        }
        long itemId = itemId(request);

        List<Item> bank = items.findActiveBySpecialization(session.specializationCode());
        Map<Long, Item> byId = indexById(bank);

        List<Attempt> served = attempts.findBySession(sessionId);
        Attempt target = served.stream()
                .filter(attempt -> attempt.itemId() == itemId)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Задание не выдано для сессии"));

        // Idempotent replay: an already-answered item is a strict no-op —
        // return the current progress unchanged without recompute/advance/serve/complete.
        if (target.answer() != null) {
            return currentProgress(session);
        }

        String proctoringJson = proctoringJson(target, request);
        String answerJson = request.answer() == null ? null : JSON.writeValueAsString(request.answer());

        Boolean isCorrect = grade(byId.get(itemId), request.answer());
        attempts.saveAnswer(target.id(), answerJson, isCorrect, request.responseMs(), proctoringJson);
        List<Attempt> answered = withAnswer(served, target,
                new Attempt(target.id(), target.sessionId(), target.itemId(), target.itemOrder(),
                        target.variantSeed(), answerJson, isCorrect, request.responseMs(),
                        proctoringJson, target.servedAt()));

        Estimate estimate = estimate(session, byId, answered);
        int answeredCount = answeredCount(answered);

        if (RoutingRules.shouldStop(estimate.theta(), estimate.standardError(), answeredCount,
                properties.getSeThreshold(), properties.getMinItems(), properties.getMaxItems())) {
            return complete(session, estimate, answeredCount, answered);
        }

        List<Item> available = available(bank, answered);
        Optional<Item> next = RoutingRules.nextItem(estimate.theta(), available);
        if (next.isEmpty()) {
            return complete(session, estimate, answeredCount, answered);
        }
        persistProgress(session, estimate, answeredCount);
        return serveNext(session, estimate, answeredCount, served, next.get());
    }

    /**
     * Completion: proctoring-flagged sessions route straight to
     * {@code needs_review} (never auto-fail, {@code §9.2}); otherwise the
     * {@link CategoryAssignmentService} classifies {@code θ + SE} into a grade
     * band (borderline estimates route to manual review, {@code §6.3}).
     */
    private AnswerResponse complete(TestSession session, Estimate estimate, int answeredCount,
                                    List<Attempt> answered) {
        TestSession current = sessions.findById(session.id()).orElse(session);
        if (hasProctoringFlags(answered)) {
            sessions.update(progress(current, SessionStatus.NEEDS_REVIEW, estimate, answeredCount, null));
            return AnswerResponse.completed(session.id(), SessionStatus.NEEDS_REVIEW.wire(),
                    estimate.theta(), estimate.standardError(), answeredCount, null, null);
        }
        CategoryAssignmentService.Outcome outcome = categories.complete(
                session.id(), session.candidateId(), estimate.theta(), estimate.standardError(),
                candidateInitiatedDowngrade(session));
        if (outcome.manualReview()) {
            return AnswerResponse.completed(session.id(), SessionStatus.NEEDS_REVIEW.wire(),
                    estimate.theta(), estimate.standardError(), answeredCount,
                    outcome.grade(), null);
        }
        return AnswerResponse.completed(session.id(), SessionStatus.COMPLETED.wire(),
                estimate.theta(), estimate.standardError(), answeredCount,
                outcome.grade(), outcome.categoryCode());
    }

    /**
     * Serves the next item: conflict-safe insert, TD-5 exposure bookkeeping on
     * the winning insert, presentation-only response (model + deterministic
     * option permutation, never the key or {@code b}).
     */
    private AnswerResponse serveNext(TestSession session, Estimate estimate, int answeredCount,
                                     List<Attempt> served, Item next) {
        int order = served.stream().mapToInt(Attempt::itemOrder).max().orElse(0) + 1;
        long variantSeed = variantSeed(session.formSeed(), next.id(), order);
        Optional<Long> inserted = attempts.insertIgnoringConflict(
                new Attempt(0L, session.id(), next.id(), order, variantSeed,
                        null, null, null, null, null));
        if (inserted.isPresent()) {
            trackExposure(next.id());
        } else {
            Attempt winner = attempts.findBySession(session.id()).stream()
                    .filter(attempt -> attempt.itemOrder() == order)
                    .findFirst()
                    .orElseThrow(() -> ApiException.conflict("Задание уже выдано"));
            Item winnerItem = indexById(items.findActiveBySpecialization(session.specializationCode()))
                    .get(winner.itemId());
            Item presented = winnerItem != null ? winnerItem : next;
            return present(session, estimate, answeredCount, presented, winner.variantSeed());
        }
        return present(session, estimate, answeredCount, next, variantSeed);
    }

    private AnswerResponse present(TestSession session, Estimate estimate, int answeredCount,
                                   Item item, Long variantSeed) {
        JsonNode content = modelOf(item);
        List<Integer> optionOrder = optionOrder(optionCount(content),
                variantSeed == null ? item.id() : variantSeed);
        return AnswerResponse.next(session.id(), SessionStatus.IN_PROGRESS.wire(),
                estimate.theta(), estimate.standardError(), answeredCount,
                item.id(), item.itemKind().wire(), content, optionOrder);
    }

    /** Full recomputation over answered attempts in serving order. */
    private Estimate estimate(TestSession session, Map<Long, Item> byId, List<Attempt> answered) {
        String grade = session.targetGrade();
        Double startTheta = grade == null ? null : properties.getStartThetaByGrade().get(grade);
        Estimate estimate = IrtScorer.initial(startTheta == null ? 0.0 : startTheta, INITIAL_SE);
        List<Attempt> ordered = new ArrayList<>(answered);
        ordered.sort((left, right) -> Integer.compare(left.itemOrder(), right.itemOrder()));
        for (Attempt attempt : ordered) {
            if (attempt.answer() == null || attempt.isCorrect() == null) {
                continue;
            }
            Item item = byId.get(attempt.itemId());
            if (item == null || item.difficulty() == null) {
                continue;
            }
            estimate = IrtScorer.update(estimate, item.difficulty().doubleValue(), attempt.isCorrect());
        }
        return estimate;
    }

    private void persistProgress(TestSession session, Estimate estimate, int answeredCount) {
        sessions.update(new TestSession(
                session.id(),
                session.candidateId(),
                session.industryCode(),
                session.specializationCode(),
                session.targetGrade(),
                session.surveyAnswers(),
                session.declaredDimensions(),
                session.formSeed(),
                SessionStatus.IN_PROGRESS,
                BigDecimal.valueOf(estimate.theta()),
                BigDecimal.valueOf(estimate.standardError()),
                answeredCount,
                session.startedAt(),
                session.completedAt()));
    }

    private static TestSession progress(TestSession session, SessionStatus status, Estimate estimate,
                                        int answeredCount, Instant completedAt) {
        return new TestSession(
                session.id(),
                session.candidateId(),
                session.industryCode(),
                session.specializationCode(),
                session.targetGrade(),
                session.surveyAnswers(),
                session.declaredDimensions(),
                session.formSeed(),
                status,
                BigDecimal.valueOf(estimate.theta()),
                BigDecimal.valueOf(estimate.standardError()),
                answeredCount,
                session.startedAt(),
                completedAt);
    }

    /**
     * Grades the candidate's answer against the item {@code key}. Auto-checkable
     * formats compare canonically ({@code value} scalar equality;
     * {@code correct} scalar equality or order-insensitive set equality for
     * multiple choice). {@code code} items ({@code reference_tests}) are not
     * auto-gradable and yield {@code null}; an unknown or key-less item yields
     * {@code null} as well so {@code θ} stays untouched.
     */
    static Boolean grade(Item item, JsonNode answer) {
        if (item == null || item.payload() == null || item.payload().isBlank() || answer == null) {
            return null;
        }
        JsonNode key;
        try {
            JsonNode payload = JSON.readTree(item.payload());
            key = payload == null ? null : payload.get("key");
        } catch (RuntimeException ex) {
            return null;
        }
        if (key == null || !key.isObject()) {
            return null;
        }
        if (key.has("reference_tests")) {
            return null;
        }
        if (key.has("value")) {
            return canonicalEquals(key.get("value"), answer);
        }
        if (key.has("correct")) {
            JsonNode expected = key.get("correct");
            if (expected != null && expected.isArray() && answer.isArray()) {
                return setEquals(expected, answer);
            }
            return canonicalEquals(expected, answer);
        }
        return null;
    }

    private static boolean canonicalEquals(JsonNode expected, JsonNode actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected.isTextual() && actual.isTextual()) {
            return expected.stringValue().trim().equals(actual.stringValue().trim());
        }
        if (expected.isNumber() && actual.isNumber()) {
            return Double.compare(expected.doubleValue(), actual.doubleValue()) == 0;
        }
        return JSON.writeValueAsString(expected).equals(JSON.writeValueAsString(actual));
    }

    private static boolean setEquals(JsonNode expected, JsonNode actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        Set<String> remaining = new HashSet<>();
        for (JsonNode node : expected) {
            remaining.add(canonicalForm(node));
        }
        for (JsonNode node : actual) {
            if (!remaining.remove(canonicalForm(node))) {
                return false;
            }
        }
        return remaining.isEmpty();
    }

    private static String canonicalForm(JsonNode node) {
        if (node != null && node.isTextual()) {
            return node.stringValue().trim();
        }
        return JSON.writeValueAsString(node);
    }

    private static long itemId(AnswerRequest request) {
        if (request == null || request.itemId() == null) {
            throw ApiException.validation("Некорректный item_id");
        }
        try {
            return Long.parseLong(request.itemId().trim());
        } catch (NumberFormatException ex) {
            throw ApiException.validation("Некорректный item_id");
        }
    }

    private static String proctoringJson(Attempt target, AnswerRequest request) {
        String incoming = null;
        if (request != null && request.proctoringFlags() != null && !request.proctoringFlags().isNull()) {
            incoming = JSON.writeValueAsString(request.proctoringFlags());
        }
        // Single shared append-merge semantics with ProctoringService: never
        // overwrite stored flags, only append the incoming document.
        return ProctoringService.mergeJson(target.proctoringFlags(), incoming);
    }

    /**
     * Candidate-initiated downgrade signal (§7.1): the session target grade
     * ranks below the candidate's current grade for the specialization.
     * Recorded as {@code down} only in this case; never forced.
     */
    private boolean candidateInitiatedDowngrade(TestSession session) {
        if (gradeHistory == null || session.targetGrade() == null) {
            return false;
        }
        String current = null;
        try {
            List<GradeHistoryRepository.GradeHistoryEntry> history =
                    gradeHistory.listByCandidate(session.candidateId());
            if (history != null) {
                for (GradeHistoryRepository.GradeHistoryEntry row : history) {
                    if (session.specializationCode() != null
                            && session.specializationCode().equals(row.specializationCode())) {
                        current = row.grade();
                    }
                }
            }
        } catch (RuntimeException ex) {
            return false;
        }
        if (current == null) {
            return false;
        }
        int targetRank = rankOf(session.targetGrade());
        int currentRank = rankOf(current);
        return targetRank >= 0 && currentRank >= 0 && targetRank < currentRank;
    }

    private int rankOf(String grade) {
        List<Map.Entry<String, Double>> ordered =
                new ArrayList<>(properties.getCutScores().entrySet());
        ordered.sort((left, right) -> Double.compare(left.getValue(), right.getValue()));
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getKey().equals(grade)) {
                return i;
            }
        }
        return -1;
    }

    /** Stored progress snapshot for idempotent replay: no side effects. */
    private static AnswerResponse currentProgress(TestSession session) {
        BigDecimal theta = session.theta() != null ? session.theta() : BigDecimal.ZERO;
        BigDecimal thetaSe = session.thetaSe() != null ? session.thetaSe() : BigDecimal.ONE;
        return new AnswerResponse(Long.toString(session.id()), session.status().wire(),
                theta, thetaSe, session.itemCount(), false, null, null, null);
    }

    private static boolean hasProctoringFlags(List<Attempt> answered) {
        for (Attempt attempt : answered) {
            String flags = attempt.proctoringFlags();
            if (flags == null || flags.isBlank()
                    || flags.trim().equals("null") || flags.trim().equals("[]")) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static List<Attempt> withAnswer(List<Attempt> served, Attempt target, Attempt answered) {
        List<Attempt> merged = new ArrayList<>(served.size());
        for (Attempt attempt : served) {
            merged.add(attempt.id() == target.id() ? answered : attempt);
        }
        return List.copyOf(merged);
    }

    private static int answeredCount(List<Attempt> attempts) {
        int count = 0;
        for (Attempt attempt : attempts) {
            if (attempt.answer() != null) {
                count++;
            }
        }
        return count;
    }

    private static List<Item> available(List<Item> bank, List<Attempt> answered) {
        Set<Long> servedIds = new HashSet<>();
        for (Attempt attempt : answered) {
            servedIds.add(attempt.itemId());
        }
        List<Item> available = new ArrayList<>();
        for (Item item : bank) {
            if (item != null && !servedIds.contains(item.id()) && !item.exposureExhausted()) {
                available.add(item);
            }
        }
        return available;
    }

    /** Deterministic per-item parameter seed under the session's form seed. */
    private static long variantSeed(Long formSeed, long itemId, int order) {
        long base = formSeed == null ? 0L : formSeed;
        return base * 31L + order * 1315423911L + itemId;
    }

    /**
     * TD-5 exposure bookkeeping: increments {@code exposure_count} on serve and
     * retires the item once its limit is exhausted.
     */
    private void trackExposure(long itemId) {
        items.incrementExposure(itemId);
        if (items.isExposureExhausted(itemId)) {
            items.retire(itemId);
        }
    }

    private Map<Long, Item> indexById(List<Item> bank) {
        Map<Long, Item> byId = new LinkedHashMap<>();
        for (Item item : bank) {
            if (item != null) {
                byId.put(item.id(), item);
            }
        }
        return byId;
    }

    /** The item's presentation model; the {@code key} node is deliberately never read. */
    private static JsonNode modelOf(Item item) {
        if (item == null || item.payload() == null || item.payload().isBlank()) {
            return null;
        }
        JsonNode payload = JSON.readTree(item.payload());
        return payload == null ? null : payload.get("model");
    }

    private static int optionCount(JsonNode model) {
        if (model == null) {
            return 0;
        }
        JsonNode options = model.get("options");
        return options != null && options.isArray() ? options.size() : 0;
    }

    /** Deterministic permutation of {@code [0, count)} seeded by the variant seed. */
    private static List<Integer> optionOrder(int count, long seed) {
        if (count <= 0) {
            return List.of();
        }
        List<Integer> order = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            order.add(index);
        }
        Random random = new Random(seed);
        for (int index = count - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            int current = order.get(index);
            order.set(index, order.get(swap));
            order.set(swap, current);
        }
        return List.copyOf(order);
    }
}
