package com.pullhire.candidate.repo;

import com.pullhire.candidate.domain.PublicCard;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** JdbcTemplate access to {@code candidate.public_cards} ({@code docs/data_model.md §4.4}). */
@Repository
public class PublicCardRepository {

    private static final String COLUMNS =
            "id, profile_id, display_name, headline, industry_code, specialization_code, grade, "
                    + "stack, fsp_verified, contact_released, updated_at";

    private static final RowMapper<PublicCard> MAPPER = (rs, rowNum) -> new PublicCard(
            rs.getLong("id"),
            rs.getLong("profile_id"),
            rs.getString("display_name"),
            rs.getString("headline"),
            rs.getString("industry_code"),
            rs.getString("specialization_code"),
            rs.getString("grade"),
            JdbcRows.strings(rs, "stack"),
            rs.getBoolean("fsp_verified"),
            rs.getBoolean("contact_released"),
            JdbcRows.instant(rs, "updated_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public PublicCardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PublicCard> findByProfileId(long profileId) {
        List<PublicCard> cards = jdbc.query(
                "SELECT " + COLUMNS + " FROM public_cards WHERE profile_id = :profileId",
                new MapSqlParameterSource("profileId", profileId),
                MAPPER);
        return cards.stream().findFirst();
    }

    /** Inserts or updates the 1:1 public card for a profile; returns its id. */
    public long upsert(PublicCard card) {
        Long id = jdbc.queryForObject("""
                INSERT INTO public_cards (profile_id, display_name, headline, industry_code,
                        specialization_code, grade, stack, fsp_verified, contact_released)
                VALUES (:profileId, :displayName, :headline, :industryCode, :specializationCode,
                        :grade, :stack, :fspVerified, :contactReleased)
                ON CONFLICT (profile_id) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    headline = EXCLUDED.headline,
                    industry_code = EXCLUDED.industry_code,
                    specialization_code = EXCLUDED.specialization_code,
                    grade = EXCLUDED.grade,
                    stack = EXCLUDED.stack,
                    fsp_verified = EXCLUDED.fsp_verified,
                    contact_released = EXCLUDED.contact_released,
                    updated_at = now()
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("profileId", card.profileId())
                        .addValue("displayName", card.displayName())
                        .addValue("headline", card.headline())
                        .addValue("industryCode", card.industryCode())
                        .addValue("specializationCode", card.specializationCode())
                        .addValue("grade", card.grade())
                        .addValue("stack", card.stack())
                        .addValue("fspVerified", card.fspVerified())
                        .addValue("contactReleased", card.contactReleased()),
                Long.class);
        return id == null ? -1L : id;
    }

    /** Removes the public card for a hidden profile ({@code visible_to_employers=false}, OQ-16). */
    public void deleteByProfileId(long profileId) {
        jdbc.update("DELETE FROM public_cards WHERE profile_id = :profileId",
                new MapSqlParameterSource("profileId", profileId));
    }

    /**
     * Idempotently flips {@code contact_released} to true (OQ-22). Returns the rows
     * changed: {@code 0} when it was already released (no-op), {@code 1} on the flip.
     */
    public int markContactReleased(long profileId) {
        return jdbc.update("""
                UPDATE public_cards SET contact_released = true, updated_at = now()
                WHERE profile_id = :profileId AND contact_released = false
                """,
                new MapSqlParameterSource("profileId", profileId));
    }
}
