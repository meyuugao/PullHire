package me.yuugao.pullhire.candidate.repo;

import me.yuugao.pullhire.candidate.domain.ParseStatus;
import me.yuugao.pullhire.candidate.domain.Resume;
import me.yuugao.pullhire.candidate.domain.ResumeKind;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** JdbcTemplate access to {@code candidate.resumes} ({@code docs/data_model.md §4.2}). */
@Repository
public class ResumeRepository {

    private static final String COLUMNS =
            "id, profile_id, kind, file_name, file_path, mime_type, size_bytes, parse_status, parsed_at, created_at";

    private static final RowMapper<Resume> MAPPER = (rs, rowNum) -> new Resume(
            rs.getLong("id"),
            rs.getLong("profile_id"),
            ResumeKind.fromWire(rs.getString("kind")),
            rs.getString("file_name"),
            rs.getString("file_path"),
            rs.getString("mime_type"),
            rs.getObject("size_bytes", Long.class),
            ParseStatus.fromWire(rs.getString("parse_status")),
            JdbcRows.instant(rs, "parsed_at"),
            JdbcRows.instant(rs, "created_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public ResumeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts a resume row and returns the generated id. */
    public long insert(Resume resume) {
        Long id = jdbc.queryForObject("""
                INSERT INTO resumes (profile_id, kind, file_name, file_path, mime_type, size_bytes,
                        parse_status, parsed_at)
                VALUES (:profileId, :kind, :fileName, :filePath, :mimeType, :sizeBytes, :parseStatus, :parsedAt)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("profileId", resume.profileId())
                        .addValue("kind", resume.kind().wire())
                        .addValue("fileName", resume.fileName())
                        .addValue("filePath", resume.filePath())
                        .addValue("mimeType", resume.mimeType())
                        .addValue("sizeBytes", resume.sizeBytes())
                        .addValue("parseStatus", resume.parseStatus().wire())
                        .addValue("parsedAt", JdbcRows.utc(resume.parsedAt())),
                Long.class);
        return id == null ? -1L : id;
    }

    /** Most recent resume of the given kind for a profile (uploaded or generated). */
    public Optional<Resume> findLatestByProfileIdAndKind(long profileId, ResumeKind kind) {
        List<Resume> resumes = jdbc.query(
                "SELECT " + COLUMNS + " FROM resumes WHERE profile_id = :profileId AND kind = :kind "
                        + "ORDER BY created_at DESC, id DESC LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("profileId", profileId)
                        .addValue("kind", kind.wire()),
                MAPPER);
        return resumes.stream().findFirst();
    }
}
