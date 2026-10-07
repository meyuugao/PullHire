package com.pullhire.candidate.repo;

import com.pullhire.candidate.domain.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** JdbcTemplate access to {@code candidate.profiles} ({@code docs/data_model.md §4.1}). */
@Repository
public class ProfileRepository {

    private static final String COLUMNS =
            "id, user_id, full_name, contact_email, contact_phone, position_title, grade_declared, "
                    + "experience_years, stack, skills, soft_skills, summary, specialization_code, "
                    + "industry_code, status, created_at, updated_at";

    private static final RowMapper<Profile> MAPPER = (rs, rowNum) -> new Profile(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("full_name"),
            rs.getString("contact_email"),
            rs.getString("contact_phone"),
            rs.getString("position_title"),
            rs.getString("grade_declared"),
            rs.getBigDecimal("experience_years"),
            JdbcRows.strings(rs, "stack"),
            JdbcRows.strings(rs, "skills"),
            JdbcRows.strings(rs, "soft_skills"),
            rs.getString("summary"),
            rs.getString("specialization_code"),
            rs.getString("industry_code"),
            rs.getString("status"),
            JdbcRows.instant(rs, "created_at"),
            JdbcRows.instant(rs, "updated_at"));

    private static final String UPSERT_BY_USER_ID = """
            INSERT INTO profiles (user_id, full_name, contact_email, contact_phone, position_title,
                    grade_declared, experience_years, stack, skills, soft_skills, summary,
                    specialization_code, industry_code, status)
            VALUES (:userId, :fullName, :contactEmail, :contactPhone, :positionTitle, :gradeDeclared,
                    :experienceYears, :stack, :skills, :softSkills, :summary, :specializationCode,
                    :industryCode, COALESCE(:status, 'draft'))
            ON CONFLICT (user_id) DO UPDATE SET
                full_name = EXCLUDED.full_name,
                contact_email = EXCLUDED.contact_email,
                contact_phone = EXCLUDED.contact_phone,
                position_title = EXCLUDED.position_title,
                grade_declared = EXCLUDED.grade_declared,
                experience_years = EXCLUDED.experience_years,
                stack = EXCLUDED.stack,
                skills = EXCLUDED.skills,
                soft_skills = EXCLUDED.soft_skills,
                summary = EXCLUDED.summary,
                specialization_code = EXCLUDED.specialization_code,
                industry_code = EXCLUDED.industry_code,
                status = EXCLUDED.status,
                updated_at = now()
            RETURNING id
            """;

    private static final String UPSERT_BY_ID = """
            INSERT INTO profiles (id, user_id, full_name, contact_email, contact_phone, position_title,
                    grade_declared, experience_years, stack, skills, soft_skills, summary,
                    specialization_code, industry_code, status)
            VALUES (:id, :userId, :fullName, :contactEmail, :contactPhone, :positionTitle, :gradeDeclared,
                    :experienceYears, :stack, :skills, :softSkills, :summary, :specializationCode,
                    :industryCode, COALESCE(:status, 'draft'))
            ON CONFLICT (id) DO UPDATE SET
                full_name = EXCLUDED.full_name,
                contact_email = EXCLUDED.contact_email,
                contact_phone = EXCLUDED.contact_phone,
                position_title = EXCLUDED.position_title,
                grade_declared = EXCLUDED.grade_declared,
                experience_years = EXCLUDED.experience_years,
                stack = EXCLUDED.stack,
                skills = EXCLUDED.skills,
                soft_skills = EXCLUDED.soft_skills,
                summary = EXCLUDED.summary,
                specialization_code = EXCLUDED.specialization_code,
                industry_code = EXCLUDED.industry_code,
                status = EXCLUDED.status,
                updated_at = now()
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ProfileRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Profile> findById(long id) {
        List<Profile> profiles = jdbc.query(
                "SELECT " + COLUMNS + " FROM profiles WHERE id = :id",
                new MapSqlParameterSource("id", id),
                MAPPER);
        return profiles.stream().findFirst();
    }

    public Optional<Profile> findByUserId(long userId) {
        List<Profile> profiles = jdbc.query(
                "SELECT " + COLUMNS + " FROM profiles WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId),
                MAPPER);
        return profiles.stream().findFirst();
    }

    /** Inserts or updates the profile keyed by its logical owner {@code user_id}; returns its id. */
    public long upsertByUserId(Profile profile) {
        Long id = jdbc.queryForObject(UPSERT_BY_USER_ID, params(profile), Long.class);
        return id == null ? -1L : id;
    }

    /** Inserts or updates the profile keyed by its primary key. */
    public void upsertById(Profile profile) {
        jdbc.update(UPSERT_BY_ID, params(profile));
    }

    private static MapSqlParameterSource params(Profile profile) {
        return new MapSqlParameterSource()
                .addValue("id", profile.id())
                .addValue("userId", profile.userId())
                .addValue("fullName", profile.fullName())
                .addValue("contactEmail", profile.contactEmail())
                .addValue("contactPhone", profile.contactPhone())
                .addValue("positionTitle", profile.positionTitle())
                .addValue("gradeDeclared", profile.gradeDeclared())
                .addValue("experienceYears", profile.experienceYears())
                .addValue("stack", profile.stack())
                .addValue("skills", profile.skills())
                .addValue("softSkills", profile.softSkills())
                .addValue("summary", profile.summary())
                .addValue("specializationCode", profile.specializationCode())
                .addValue("industryCode", profile.industryCode())
                .addValue("status", profile.status());
    }
}
