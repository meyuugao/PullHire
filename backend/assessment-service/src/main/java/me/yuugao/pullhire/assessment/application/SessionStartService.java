package me.yuugao.pullhire.assessment.application;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.Attempt;
import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.domain.form.FormAssembler;
import me.yuugao.pullhire.assessment.domain.form.FormAssembler.Form;
import me.yuugao.pullhire.assessment.domain.form.FormAssembler.FormItem;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.repo.AttemptRepository;
import me.yuugao.pullhire.assessment.repo.ItemBankRepository;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import me.yuugao.pullhire.assessment.web.dto.SessionStartResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /assessment/sessions/{id}/start} ({@code docs/api.md §3.4},
 * {@code docs/testing_model.md §3.4, §4}): assembles the deterministic attempt
 * form from the session's {@code form_seed} and serves its first item.
 *
 * <p>The form is assembled once via {@link FormAssembler} — item order, per-item
 * {@code variant_seed} and the answer-option permutation are deterministic in the
 * form seed and the candidate's prior-served-variant ledger
 * ({@code AttemptRepository.priorServedVariants}). Starting the session persists
 * {@code form_seed} (generated once when the session does not carry one) and
 * advances {@code status} {@code started → in_progress}, then records the first
 * item as an {@link Attempt} with its {@code variant_seed}. Re-starting an
 * already-started session is idempotent: the first attempt is served again
 * unchanged (its persisted {@code variant_seed} is reused) rather than re-inserted.
 *
 * <p>Anti-leak ({@code docs/testing_model.md §9.1}): the service reads the raw
 * item {@code payload} and exposes only its {@code model} (presentation) node —
 * the {@code key} (answer key), the IRT difficulty {@code b} and any scoring
 * parameter never leave the service. {@code content} is the item model as-is;
 * {@code optionOrder} is the deterministic permutation of the item's answer
 * options.
 */
@Service
public class SessionStartService {

    private static final JsonMapper JSON = new JsonMapper();

    private final TestSessionRepository sessions;
    private final ItemBankRepository items;
    private final AttemptRepository attempts;
    private final AssessmentProperties properties;

    public SessionStartService(TestSessionRepository sessions, ItemBankRepository items,
                               AttemptRepository attempts, AssessmentProperties properties) {
        this.sessions = sessions;
        this.items = items;
        this.attempts = attempts;
        this.properties = properties;
    }

    /**
     * Starts (or idempotently re-serves) one session owned by the candidate.
     *
     * @param sessionId   {@code test_sessions.id}
     * @param candidateId authenticated candidate ({@code JwtAuthFilter});
     *                    a session of another candidate is reported as missing
     * @return the first servable item with its deterministic option order
     * @throws ApiException 404 when the session is missing or not owned, 409 when
     *                      it is already terminal or no item can be served
     */
    @Transactional
    public SessionStartResponse start(long sessionId, long candidateId) {
        TestSession session = sessions.findById(sessionId)
                .filter(owned -> owned.candidateId() == candidateId)
                .orElseThrow(() -> ApiException.notFound("Сессия не найдена"));
        if (session.status() == SessionStatus.COMPLETED
                || session.status() == SessionStatus.ABANDONED
                || session.status() == SessionStatus.NEEDS_REVIEW) {
            throw ApiException.conflict("Сессия уже завершена");
        }

        long formSeed = session.formSeed() != null
                ? session.formSeed()
                : ThreadLocalRandom.current().nextLong();

        List<Item> bank = items.findActiveBySpecialization(session.specializationCode());
        Map<Long, Item> byId = indexById(bank);
        Map<String, List<Long>> priorServed = priorServedVariants(session.candidateId(), bank);
        int optionCount = maxOptionCount(bank);

        Form form = FormAssembler.assemble(bank, session.declaredDimensions(), session.targetGrade(), formSeed, priorServed,
                properties.getMaxItems(), optionCount);
        if (form.items().isEmpty()) {
            throw ApiException.conflict("Нет доступных заданий для сессии");
        }
        FormItem first = form.items().get(0);

        persistProgress(session, formSeed);

        long variantSeed = first.variantSeed();
        long servedItemId = first.itemId();
        List<Integer> servedOptionOrder = first.optionOrder();
        Optional<Attempt> served = attempts.findBySession(sessionId).stream()
                .filter(attempt -> attempt.itemOrder() == first.itemOrder()
                        && attempt.itemId() == first.itemId())
                .findFirst();
        if (served.isPresent()) {
            if (served.get().variantSeed() != null) {
                variantSeed = served.get().variantSeed();
            }
        } else {
            Optional<Attempt> persistedByOrder = attempts.findBySession(sessionId).stream()
                    .filter(attempt -> attempt.itemOrder() == first.itemOrder())
                    .findFirst();
            if (persistedByOrder.isPresent()) {
                servedItemId = persistedByOrder.get().itemId();
                servedOptionOrder = optionOrderFor(form, servedItemId, first.optionOrder());
                if (persistedByOrder.get().variantSeed() != null) {
                    variantSeed = persistedByOrder.get().variantSeed();
                }
            } else {
                Optional<Long> inserted = attempts.insertIgnoringConflict(new Attempt(0L, sessionId,
                        first.itemId(), first.itemOrder(), variantSeed, null, null, null, null, null));
                if (inserted.isPresent()) {
                    trackExposure(first.itemId());
                } else {
                    Attempt winner = attempts.findBySession(sessionId).stream()
                            .filter(attempt -> attempt.itemOrder() == first.itemOrder())
                            .findFirst()
                            .orElseThrow(() -> ApiException.conflict("Сессия уже запущена"));
                    servedItemId = winner.itemId();
                    servedOptionOrder = optionOrderFor(form, servedItemId, first.optionOrder());
                    variantSeed = winner.variantSeed() != null ? winner.variantSeed() : variantSeed;
                }
            }
        }

        Item servedItem = byId.get(servedItemId);
        JsonNode content = modelOf(servedItem);
        String servedKind = servedItem != null ? servedItem.itemKind().wire()
                : first.itemKind().wire();
        List<Integer> optionOrder = presentationOrder(servedOptionOrder, optionCount(content));
        return SessionStartResponse.from(sessionId, SessionStatus.IN_PROGRESS.wire(),
                servedItemId, servedKind, content, optionOrder);
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

    /**
     * Deterministic option permutation for the effectively served item: the
     * form entry of that item when present, otherwise the current first-item
     * permutation as a fallback.
     */
    private static List<Integer> optionOrderFor(Form form, long servedItemId, List<Integer> fallback) {
        for (FormItem candidate : form.items()) {
            if (candidate.itemId() == servedItemId) {
                return candidate.optionOrder();
            }
        }
        return fallback;
    }

    /**
     * Persists {@code form_seed} and advances {@code status} to {@code in_progress}
     * without disturbing the running IRT columns; skipped when both are already set.
     */
    private void persistProgress(TestSession session, long formSeed) {
        if (session.formSeed() != null && session.status() == SessionStatus.IN_PROGRESS) {
            return;
        }
        sessions.update(new TestSession(
                session.id(),
                session.candidateId(),
                session.industryCode(),
                session.specializationCode(),
                session.targetGrade(),
                session.surveyAnswers(),
                session.declaredDimensions(),
                formSeed,
                SessionStatus.IN_PROGRESS,
                session.theta(),
                session.thetaSe(),
                session.itemCount(),
                session.startedAt(),
                session.completedAt()));
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

    /**
     * Prior-served variant seeds per template family for the candidate
     * ({@code docs/testing_model.md §3.4}), keyed by {@code template_family} as
     * {@link FormAssembler} expects. Families absent from the current bank are not
     * queried.
     */
    private Map<String, List<Long>> priorServedVariants(long candidateId, List<Item> bank) {
        Set<String> families = new LinkedHashSet<>();
        for (Item item : bank) {
            if (item != null && item.templateFamily() != null) {
                families.add(item.templateFamily());
            }
        }
        Map<String, List<Long>> prior = new LinkedHashMap<>();
        for (String family : families) {
            List<Long> seeds = attempts.priorServedVariants(candidateId, family);
            if (seeds != null && !seeds.isEmpty()) {
                prior.put(family, List.copyOf(seeds));
            }
        }
        return prior;
    }

    /** Largest answer-option count declared by any model in the bank (0 when none). */
    private static int maxOptionCount(List<Item> bank) {
        int max = 0;
        for (Item item : bank) {
            max = Math.max(max, optionCount(modelOf(item)));
        }
        return max;
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

    /**
     * Narrows a form permutation over the form-wide option count to the item's own
     * options. Filtering the permutation to values below the item count preserves
     * order and still yields a bijection onto {@code [0, itemOptionCount)}.
     */
    private static List<Integer> presentationOrder(List<Integer> optionOrder, int itemOptionCount) {
        if (itemOptionCount <= 0) {
            return List.of();
        }
        return optionOrder.stream()
                .filter(index -> index < itemOptionCount)
                .toList();
    }
}
