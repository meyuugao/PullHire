package com.pullhire.candidate.repo;

import com.pullhire.candidate.domain.PrivacySettings;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** JdbcTemplate access to {@code candidate.privacy_settings} ({@code docs/data_model.md §4.3}). */
@Repository
public class PrivacySettingsRepository {

    private static final String COLUMNS =
            "profile_id, visible_to_employers, show_fsp, show_contacts, updated_at";

    private static final RowMapper<PrivacySettings> MAPPER = (rs, rowNum) -> new PrivacySettings(
            rs.getLong("profile_id"),
            rs.getBoolean("visible_to_employers"),
            rs.getBoolean("show_fsp"),
            rs.getBoolean("show_contacts"),
            JdbcRows.instant(rs, "updated_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public PrivacySettingsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PrivacySettings> findByProfileId(long profileId) {
        List<PrivacySettings> settings = jdbc.query(
                "SELECT " + COLUMNS + " FROM privacy_settings WHERE profile_id = :profileId",
                new MapSqlParameterSource("profileId", profileId),
                MAPPER);
        return settings.stream().findFirst();
    }

    /** Inserts or replaces the 1:1 privacy row for a profile. */
    public void upsert(PrivacySettings settings) {
        jdbc.update("""
                INSERT INTO privacy_settings (profile_id, visible_to_employers, show_fsp, show_contacts)
                VALUES (:profileId, :visibleToEmployers, :showFsp, :showContacts)
                ON CONFLICT (profile_id) DO UPDATE SET
                    visible_to_employers = EXCLUDED.visible_to_employers,
                    show_fsp = EXCLUDED.show_fsp,
                    show_contacts = EXCLUDED.show_contacts,
                    updated_at = now()
                """,
                new MapSqlParameterSource()
                        .addValue("profileId", settings.profileId())
                        .addValue("visibleToEmployers", settings.visibleToEmployers())
                        .addValue("showFsp", settings.showFsp())
                        .addValue("showContacts", settings.showContacts()));
    }
}
