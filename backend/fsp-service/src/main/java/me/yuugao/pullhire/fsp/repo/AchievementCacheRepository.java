package me.yuugao.pullhire.fsp.repo;

import me.yuugao.pullhire.fsp.domain.AchievementCache;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** JdbcTemplate access to {@code fsp.achievements_cache} ({@code docs/data_model.md §5.2}). */
@Repository
public class AchievementCacheRepository {

    private static final String COLUMNS =
            "id, fsp_link_id, external_achievement_id, title, event_type, achieved_at, place, "
                    + "payload::text AS payload, synced_at";

    private static final RowMapper<AchievementCache> MAPPER = (rs, rowNum) -> new AchievementCache(
            rs.getLong("id"),
            rs.getLong("fsp_link_id"),
            rs.getString("external_achievement_id"),
            rs.getString("title"),
            rs.getString("event_type"),
            rs.getObject("achieved_at", LocalDate.class),
            rs.getObject("place", Integer.class),
            rs.getString("payload"),
            JdbcRows.instant(rs, "synced_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public AchievementCacheRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Achievements of a link, oldest inserted first (stable across re-syncs). */
    public List<AchievementCache> listByLinkId(long fspLinkId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM achievements_cache WHERE fsp_link_id = :fspLinkId ORDER BY id",
                new MapSqlParameterSource("fspLinkId", fspLinkId),
                MAPPER);
    }

    /**
     * Replaces the whole cache of one link inside its transaction: the previous set
     * is deleted and the new one inserted atomically, so readers never observe a
     * half-updated cache. An empty {@code items} list clears the cache (used for
     * {@code no_history} and on unlink).
     */
    @Transactional
    public void replaceByLinkId(long fspLinkId, List<AchievementCache> items) {
        jdbc.update("DELETE FROM achievements_cache WHERE fsp_link_id = :fspLinkId",
                new MapSqlParameterSource("fspLinkId", fspLinkId));
        if (items == null || items.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = items.stream()
                .map(item -> new MapSqlParameterSource()
                        .addValue("fspLinkId", fspLinkId)
                        .addValue("externalAchievementId", item.externalAchievementId())
                        .addValue("title", item.title())
                        .addValue("eventType", item.eventType())
                        .addValue("achievedAt", item.achievedAt())
                        .addValue("place", item.place())
                        .addValue("payload", item.payload()))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO achievements_cache (fsp_link_id, external_achievement_id, title, event_type,
                        achieved_at, place, payload)
                VALUES (:fspLinkId, :externalAchievementId, :title, :eventType, :achievedAt, :place,
                        CAST(:payload AS jsonb))
                """, batch);
    }
}
