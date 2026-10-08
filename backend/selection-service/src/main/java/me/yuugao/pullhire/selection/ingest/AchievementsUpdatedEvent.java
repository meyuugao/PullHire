package me.yuugao.pullhire.selection.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The {@code fsp.achievements_updated} event envelope as published by fsp-service
 * ({@code docs/fsp_integration.md §5}, {@code docs/architecture.md §4.2}).
 * Field names mirror the wire JSON (snake_case); unknown future fields are
 * ignored by Jackson.
 *
 * <p>Schema: {@code contracts/events/fsp.achievements_updated.schema.json}.
 * The payload carries raw achievements only — no precomputed count/weighted
 * fields and no {@code category_code}; derivations live in
 * {@link AchievementsUpdatedListener} ({@code docs/selection.md §3},
 * {@code docs/data_model.md §7.4}).
 *
 * <pre>
 * {"event_id","type","version","occurred_at","producer","correlation_id","payload"}
 * </pre>
 */
public record AchievementsUpdatedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("type") String type,
        @JsonProperty("version") Integer version,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("producer") String producer,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("payload") Payload payload) {

    public static final String TYPE = "fsp.achievements_updated";

    /**
     * Payload ({@code contracts/events/fsp.achievements_updated.schema.json});
     * {@code candidate_id} is {@code candidate.profiles.id} (logical reference).
     * {@code history_status} ∈ {@code unknown|present|no_history}.
     */
    public record Payload(
            @JsonProperty("candidate_id") String candidateId,
            @JsonProperty("fsp_id") String fspId,
            @JsonProperty("history_status") String historyStatus,
            @JsonProperty("achievements") List<Achievement> achievements) {
    }

    /**
     * One cached achievement; {@code place} is resultativeness
     * ({@code docs/selection.md §3}): {@code 1} = winner, {@code >= 2} = placer,
     * {@code null} = participant.
     */
    public record Achievement(
            @JsonProperty("external_achievement_id") String externalAchievementId,
            @JsonProperty("title") String title,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("achieved_at") String achievedAt,
            @JsonProperty("place") Integer place) {
    }
}
