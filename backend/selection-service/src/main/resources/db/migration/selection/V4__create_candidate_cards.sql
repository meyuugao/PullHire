-- selection.candidate_cards (docs/data_model.md §7.3). Search projection per candidate, no PII.
CREATE TABLE candidate_cards (
    id bigserial PRIMARY KEY,
    candidate_id bigint NOT NULL,
    display_name text NOT NULL,
    specialization_code text NOT NULL,
    grade text NOT NULL,
    stack text[] NULL,
    fsp_verified boolean NOT NULL DEFAULT false,
    fsp_achievements_count integer NOT NULL DEFAULT 0 CHECK (fsp_achievements_count >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_candidate_cards_candidate_id UNIQUE (candidate_id)
);

CREATE INDEX ix_candidate_cards_spec_grade ON candidate_cards (specialization_code, grade);
CREATE INDEX ix_candidate_cards_stack ON candidate_cards USING GIN (stack);
