package me.yuugao.pullhire.fsp.repo;

import me.yuugao.pullhire.fsp.domain.FspLink;
import me.yuugao.pullhire.fsp.domain.HistoryStatus;
import me.yuugao.pullhire.fsp.domain.LinkStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** JdbcTemplate access to {@code fsp.fsp_links} ({@code docs/data_model.md §5.1}). */
@Repository
public class FspLinkRepository {

    private static final String COLUMNS =
            "id, candidate_id, fsp_id, link_status, history_status, linked_at, last_sync_at, "
                    + "created_at, updated_at";

    private static final RowMapper<FspLink> MAPPER = (rs, rowNum) -> new FspLink(
            rs.getLong("id"),
            rs.getLong("candidate_id"),
            rs.getString("fsp_id"),
            LinkStatus.fromWire(rs.getString("link_status")),
            HistoryStatus.fromWire(rs.getString("history_status")),
            JdbcRows.instant(rs, "linked_at"),
            JdbcRows.instant(rs, "last_sync_at"),
            JdbcRows.instant(rs, "created_at"),
            JdbcRows.instant(rs, "updated_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public FspLinkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<FspLink> findByCandidateId(long candidateId) {
        List<FspLink> links = jdbc.query(
                "SELECT " + COLUMNS + " FROM fsp_links WHERE candidate_id = :candidateId",
                new MapSqlParameterSource("candidateId", candidateId),
                MAPPER);
        return links.stream().findFirst();
    }

    public Optional<FspLink> findByFspId(String fspId) {
        List<FspLink> links = jdbc.query(
                "SELECT " + COLUMNS + " FROM fsp_links WHERE fsp_id = :fspId",
                new MapSqlParameterSource("fspId", fspId),
                MAPPER);
        return links.stream().findFirst();
    }

    /**
     * Idempotent link upsert keyed by the {@code UNIQUE (candidate_id)} constraint;
     * a repeat link of the same candidate updates the existing row instead of
     * inserting a duplicate. Returns the row id.
     */
    public long upsert(long candidateId, String fspId, LinkStatus linkStatus, HistoryStatus historyStatus,
                       Instant linkedAt, Instant lastSyncAt) {
        Long id = jdbc.queryForObject("""
                INSERT INTO fsp_links (candidate_id, fsp_id, link_status, history_status, linked_at, last_sync_at)
                VALUES (:candidateId, :fspId, :linkStatus, :historyStatus, :linkedAt, :lastSyncAt)
                ON CONFLICT (candidate_id) DO UPDATE SET
                    fsp_id = EXCLUDED.fsp_id,
                    link_status = EXCLUDED.link_status,
                    history_status = EXCLUDED.history_status,
                    linked_at = EXCLUDED.linked_at,
                    last_sync_at = EXCLUDED.last_sync_at,
                    updated_at = now()
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("candidateId", candidateId)
                        .addValue("fspId", fspId)
                        .addValue("linkStatus", linkStatus.wire())
                        .addValue("historyStatus", historyStatus.wire())
                        .addValue("linkedAt", JdbcRows.utc(linkedAt))
                        .addValue("lastSyncAt", JdbcRows.utc(lastSyncAt)),
                Long.class);
        return id == null ? -1L : id;
    }

    /**
     * Idempotently flips a link to {@code unlinked}. Returns the rows changed:
     * {@code 0} when it was already unlinked (no-op), {@code 1} on the transition.
     */
    public int unlink(long candidateId) {
        return jdbc.update("""
                UPDATE fsp_links
                SET link_status = 'unlinked', history_status = 'unknown',
                    linked_at = NULL, updated_at = now()
                WHERE candidate_id = :candidateId AND link_status <> 'unlinked'
                """,
                new MapSqlParameterSource("candidateId", candidateId));
    }
}
