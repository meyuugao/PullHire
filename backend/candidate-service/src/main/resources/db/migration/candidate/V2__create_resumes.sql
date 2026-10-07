-- candidate.resumes (docs/data_model.md §4.2). Uploaded/generated PDF metadata;
-- the file bytes live in the CANDIDATE_FILES_DIR volume, not in the database.
CREATE TABLE resumes (
    id           bigserial   PRIMARY KEY,
    profile_id   bigint      NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    kind         text        NOT NULL CHECK (kind IN ('uploaded', 'generated')),
    file_name    text        NOT NULL,
    file_path    text        NOT NULL,
    mime_type    text        NULL,
    size_bytes   bigint      NULL CHECK (size_bytes IS NULL OR size_bytes >= 0),
    parse_status text        NOT NULL DEFAULT 'pending' CHECK (parse_status IN ('pending', 'parsed', 'failed')),
    parsed_at    timestamptz NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_resumes_profile_id ON resumes (profile_id);
CREATE INDEX idx_resumes_kind ON resumes (kind);
