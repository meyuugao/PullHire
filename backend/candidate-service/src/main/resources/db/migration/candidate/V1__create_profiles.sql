-- candidate.profiles (docs/data_model.md §4.1). Owner of the candidate profile.
-- user_id is a logical reference to auth.users.id (no cross-schema FK; docs/data_model.md §1).
CREATE TABLE profiles (
    id                  bigserial    PRIMARY KEY,
    user_id             bigint       NOT NULL,
    full_name           text         NOT NULL,
    contact_email       text         NULL,
    contact_phone       text         NULL,
    position_title      text         NULL,
    grade_declared      text         NULL,
    experience_years    numeric(4,1) NULL CHECK (experience_years IS NULL OR experience_years >= 0),
    stack               text[]       NULL,
    skills              text[]       NULL,
    soft_skills         text[]       NULL,
    summary             text         NULL,
    specialization_code text         NULL,
    industry_code       text         NULL,
    status              text         NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_profiles_user_id UNIQUE (user_id)
);

CREATE INDEX idx_profiles_specialization_code ON profiles (specialization_code);
CREATE INDEX idx_profiles_stack ON profiles USING gin (stack);
