package me.yuugao.pullhire.assessment.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * {@code POST /assessment/sessions/{id}/start} response ({@code docs/api.md
 * §3.4}, {@code docs/testing_model.md §4}): the first item of the assembled
 * attempt form.
 *
 * <p>Wire shape follows {@code docs/api.md §1}: {@code snake_case} keys and
 * stringified identifiers (like {@code SurveyResponse}/{@code EligibilityResponse}).
 *
 * <p>Presentation-only by construction ({@code docs/testing_model.md §9.1}):
 * {@code content} is the item's {@code model} node (statement, options,
 * parameters), and {@code option_order} is the deterministic permutation of the
 * answer options. The answer {@code key}, the IRT difficulty {@code b}, scoring
 * parameters and the raw {@code variant_seed} are never part of this contract.
 */
public record SessionStartResponse(
        @JsonProperty("session_id") String sessionId,
        String status,
        ServedItem item) {

    public static SessionStartResponse from(long sessionId, String status, long itemId,
                                            String itemKind, JsonNode content, List<Integer> optionOrder) {
        return new SessionStartResponse(Long.toString(sessionId), status,
                ServedItem.from(itemId, itemKind, content, optionOrder));
    }

    /**
     * One servable item of the form.
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
