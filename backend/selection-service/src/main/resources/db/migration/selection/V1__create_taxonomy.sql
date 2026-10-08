-- selection.taxonomy (docs/data_model.md §7.1). Reference catalogue of industries, specializations and grades.
CREATE TABLE taxonomy (
    id                  bigserial    PRIMARY KEY,
    kind                text         NOT NULL CHECK (kind IN ('industry', 'specialization', 'grade')),
    code                text         NOT NULL,
    name                text         NOT NULL,
    parent_id           bigint       NULL REFERENCES taxonomy (id),
    grade_order         integer      NULL CHECK (grade_order IS NULL OR grade_order >= 0),
    is_active           boolean      NOT NULL DEFAULT true,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_taxonomy_kind_code UNIQUE (kind, code)
);

CREATE INDEX idx_taxonomy_parent_id ON taxonomy (parent_id);
CREATE INDEX idx_taxonomy_kind_is_active ON taxonomy (kind, is_active);
