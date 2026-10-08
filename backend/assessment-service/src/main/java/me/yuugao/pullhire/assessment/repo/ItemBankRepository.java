package me.yuugao.pullhire.assessment.repo;

import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.ItemKind;
import me.yuugao.pullhire.assessment.domain.ItemStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Raw JdbcTemplate access to {@code assessment.item_bank}
 * ({@code docs/data_model.md §6.1}): item load/seed, rotation lookup by
 * specialization (btree {@code (specialization_code, difficulty)}), exposure
 * bookkeeping and retirement. No JPA and no cross-schema access.
 */
@Repository
public class ItemBankRepository {

    private static final String COLUMNS =
            "id, item_kind, template_family, concept_code, specialization_code, difficulty, "
                    + "is_provisional, exposure_limit, exposure_count, payload::text AS payload, "
                    + "status, created_at, updated_at";

    private static final RowMapper<Item> MAPPER = (rs, rowNum) -> new Item(
            rs.getLong("id"),
            ItemKind.fromWire(rs.getString("item_kind")),
            rs.getString("template_family"),
            rs.getString("concept_code"),
            rs.getString("specialization_code"),
            rs.getBigDecimal("difficulty"),
            rs.getBoolean("is_provisional"),
            rs.getObject("exposure_limit", Integer.class),
            rs.getInt("exposure_count"),
            JdbcRows.jsonb(rs, "payload"),
            ItemStatus.fromWire(rs.getString("status")),
            JdbcRows.instant(rs, "created_at"),
            JdbcRows.instant(rs, "updated_at"));

    private static final String INSERT = """
            INSERT INTO item_bank (item_kind, template_family, concept_code, specialization_code,
                    difficulty, is_provisional, exposure_limit, exposure_count, payload, status,
                    created_at, updated_at)
            VALUES (:itemKind, :templateFamily, :conceptCode, :specializationCode, :difficulty,
                    :isProvisional, :exposureLimit, :exposureCount, CAST(:payload AS jsonb), :status,
                    :createdAt, :updatedAt)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ItemBankRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts one anchor or template row and returns its generated id. */
    public long insert(Item item) {
        Long id = jdbc.queryForObject(INSERT + System.lineSeparator() + "RETURNING id",
                itemParams(item), Long.class);
        return id == null ? -1L : id;
    }

    /** Batch seed-load of anchor + template rows ({@code insert} per row, no ids returned). */
    public void seedAll(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = items.stream()
                .map(ItemBankRepository::itemParams)
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT, batch);
    }

    /**
     * Active items of one specialization ordered by {@code difficulty}, driven by the
     * btree index {@code (specialization_code, difficulty)}. Item-exposure rotation
     * is applied by the caller via {@link #isExposureExhausted(long)}.
     */
    public List<Item> findActiveBySpecialization(String specializationCode) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM item_bank "
                        + "WHERE specialization_code = :specializationCode AND status = 'active' "
                        + "ORDER BY difficulty",
                new MapSqlParameterSource("specializationCode", specializationCode),
                MAPPER);
    }

    /** Distinct specialization codes present in the seeded taxonomy (owner-side validation). */
    public List<String> listDistinctSpecializations() {
        return jdbc.queryForList(
                "SELECT DISTINCT specialization_code FROM item_bank ORDER BY specialization_code",
                new MapSqlParameterSource(),
                String.class);
    }

    /** Distinct concept codes available for one specialization (declared_dimensions validation). */
    public List<String> listConceptCodesBySpecialization(String specializationCode) {
        return jdbc.queryForList(
                "SELECT DISTINCT concept_code FROM item_bank "
                        + "WHERE specialization_code = :specializationCode ORDER BY concept_code",
                new MapSqlParameterSource("specializationCode", specializationCode),
                String.class);
    }

    /** Atomically increments {@code exposure_count}. Returns the rows changed (0 when missing). */
    public int incrementExposure(long itemId) {
        return jdbc.update("""
                UPDATE item_bank
                SET exposure_count = exposure_count + 1, updated_at = now()
                WHERE id = :itemId
                """,
                new MapSqlParameterSource("itemId", itemId));
    }

    /**
     * True when the item's limit is set and reached
     * ({@code exposure_limit IS NOT NULL AND exposure_count >= exposure_limit}).
     */
    public boolean isExposureExhausted(long itemId) {
        Boolean exhausted = jdbc.queryForObject(
                "SELECT (exposure_limit IS NOT NULL AND exposure_count >= exposure_limit) "
                        + "FROM item_bank WHERE id = :itemId",
                new MapSqlParameterSource("itemId", itemId),
                Boolean.class);
        return Boolean.TRUE.equals(exhausted);
    }

    /** Retires the item ({@code status = 'retired'}). Idempotent: returns 0 when already retired. */
    public int retire(long itemId) {
        return jdbc.update("""
                UPDATE item_bank
                SET status = 'retired', updated_at = now()
                WHERE id = :itemId AND status <> 'retired'
                """,
                new MapSqlParameterSource("itemId", itemId));
    }

    private static MapSqlParameterSource itemParams(Item item) {
        return new MapSqlParameterSource()
                .addValue("itemKind", item.itemKind().wire())
                .addValue("templateFamily", item.templateFamily())
                .addValue("conceptCode", item.conceptCode())
                .addValue("specializationCode", item.specializationCode())
                .addValue("difficulty", item.difficulty())
                .addValue("isProvisional", item.provisional())
                .addValue("exposureLimit", item.exposureLimit())
                .addValue("exposureCount", item.exposureCount())
                .addValue("payload", item.payload())
                .addValue("status", item.status().wire())
                .addValue("createdAt", JdbcRows.utc(item.createdAt()))
                .addValue("updatedAt", JdbcRows.utc(item.updatedAt()));
    }
}
