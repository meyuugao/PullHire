package me.yuugao.pullhire.outreach.repo;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Insert-only append to {@code outreach.invitation_events} ({@code docs/data_model.md §9.2}). */
@Repository
public class InvitationEventRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public InvitationEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Appends an audit event; {@code payloadJson} is raw JSON (nullable), {@code occurred_at} defaults to now(). */
    public void insert(long invitationId, String eventType, String actorRole, String payloadJson) {
        jdbc.update(
                "INSERT INTO invitation_events (invitation_id, event_type, actor_role, payload) "
                        + "VALUES (:invitationId, :eventType, :actorRole, CAST(:payload AS jsonb))",
                new MapSqlParameterSource()
                        .addValue("invitationId", invitationId)
                        .addValue("eventType", eventType)
                        .addValue("actorRole", actorRole)
                        .addValue("payload", payloadJson));
    }
}
