-- selection.candidate_categories (docs/data_model.md §7.2). Current category per candidate; projection of assessment.category_assigned.
-- candidate_id is a logical reference to candidate.profiles.id (no cross-schema FK; docs/data_model.md §1).
CREATE TABLE candidate_categories (
    id                  bigserial    PRIMARY KEY,
    candidate_id        bigint       NOT NULL,
    specialization_code text         NOT NULL,
    grade               text         NOT NULL,
    category_code       text         NOT NULL,
    theta               numeric(6,3) NULL,
    source_event_id     text         NULL,
    assigned_at         timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_candidate_categories_candidate_id UNIQUE (candidate_id)
);

CREATE INDEX idx_candidate_categories_category_code ON candidate_categories (category_code);
CREATE INDEX idx_candidate_categories_spec_grade ON candidate_categories (specialization_code, grade);
