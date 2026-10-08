package me.yuugao.pullhire.selection.repo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Projection of {@code assessment.category_assigned} into
 * {@code selection.candidate_categories} (TD-1, {@code docs/data_model.md §7.2}).
 * One row per candidate ({@code UNIQUE (candidate_id)}); every completed
 * attempt republishes, so each delivery overwrites the row
 * ({@code ON CONFLICT (candidate_id) DO UPDATE}). {@code theta} is deliberately
 * never written here — the event carries no {@code theta}, it stays {@code NULL}.
 */
@Repository
public class CandidateCategoryRepository {

    private final JdbcTemplate jdbc;

    public CandidateCategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts or overwrites the candidate's current category row.
     */
    public void upsert(long candidateId, String specializationCode, String grade,
                       String categoryCode, String sourceEventId, Instant assignedAt) {
        jdbc.update(
                "INSERT INTO candidate_categories"
                        + " (candidate_id, specialization_code, grade, category_code, theta,"
                        + " source_event_id, assigned_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, NULL, ?, ?, now())"
                        + " ON CONFLICT (candidate_id) DO UPDATE SET"
                        + " specialization_code = EXCLUDED.specialization_code,"
                        + " grade = EXCLUDED.grade,"
                        + " category_code = EXCLUDED.category_code,"
                        + " theta = NULL,"
                        + " source_event_id = EXCLUDED.source_event_id,"
                        + " assigned_at = EXCLUDED.assigned_at,"
                        + " updated_at = now()",
                candidateId, specializationCode, grade, categoryCode, sourceEventId, utc(assignedAt));
    }

    /**
     * Converts to the {@link OffsetDateTime} type Spring JDBC/pgjdbc binds for
     * {@code timestamptz}; {@link Instant} itself has no SQL type mapping
     * (mirrors candidate/assessment {@code JdbcRows.utc}).
     */
    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
