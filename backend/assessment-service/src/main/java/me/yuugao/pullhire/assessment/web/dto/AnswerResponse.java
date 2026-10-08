package me.yuugao.pullhire.assessment.web.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * {@code POST /assessment/sessions/{id}/answers} response ({@code docs/api.md
 * §3.4}, {@code docs/testing_model.md §4, §5}): the updated estimate plus
 * either the next item to serve or the completion outcome.
 *
 * <p>Wire shape mirrors the {@link SessionStartResponse} presentation-only
 * pattern ({@code docs/testing_model.md §9.1}): {@code snake_case} keys,
 * stringified identifiers, and the served {@code item} exposes only the item
 * {@code model} (statement, options, parameters) with its deterministic
 * {@code option_order}. The answer {@code key}, the IRT difficulty {@code b},
 * scoring parameters and the raw {@code variant_seed} are never part of this
 * contract. {@code item} is {@code null} once the attempt is complete; then
 * {@code grade} (and {@code category_code} when auto-assigned) carry the
 * {@code CategoryAssignmentService} outcome.
 */
public record AnswerResponse(
        @JsonProperty("session_id") String sessionId,
        String status,
        BigDecimal theta,
        @JsonProperty("theta_se") BigDecimal thetaSe,
        @JsonProperty("item_count") int itemCount,
        boolean completed,
        ServedItem item,
        String grade,
        @JsonProperty("category_code") String categoryCode) {

    public static AnswerResponse next(long sessionId, String status, double theta, double standardError,
                                      int itemCount, long itemId, String itemKind, JsonNode content,
                                      List<Integer> optionOrder) {
        return new AnswerResponse(Long.toString(sessionId), status,
                BigDecimal.valueOf(theta), BigDecimal.valueOf(standardError), itemCount, false,
                ServedItem.from(itemId, itemKind, content, optionOrder), null, null);
    }

    public static AnswerResponse completed(long sessionId, String status, double theta, double standardError,
                                           int itemCount, String grade, String categoryCode) {
        return new AnswerResponse(Long.toString(sessionId), status,
                BigDecimal.valueOf(theta), BigDecimal.valueOf(standardError), itemCount, true,
                null, grade, categoryCode);
    }

    /**
     * One servable item of the form (presentation only, never the key or {@code b}).
     *
     * @param itemId      {@code item_bank.id} as a string
     * @param itemKind    {@code anchor} or {@code template}
     * @param content     the item presentation model (may be {@code null} when the
     *                    stored payload carries no model)
     * @param optionOrder a permutation of {@code [0, optionCount)} for the item's
     *                    answer options; empty when the item has no options
     */
    public record ServedItem(
            @JsonProperty("item_id") String itemId,
            @JsonProperty("item_kind") String itemKind,
            JsonNode content,
            @JsonProperty("option_order") List<Integer> optionOrder) {

        public ServedItem {
            optionOrder = List.copyOf(optionOrder);
        }

        public static ServedItem from(long itemId, String itemKind, JsonNode content,
                                      List<Integer> optionOrder) {
            return new ServedItem(Long.toString(itemId), itemKind, content, optionOrder);
        }
    }
}
