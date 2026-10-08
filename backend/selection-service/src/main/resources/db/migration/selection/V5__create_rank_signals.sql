-- selection.rank_signals (docs/data_model.md §7.4). Ranking components per candidate x category.
CREATE TABLE rank_signals (
    id bigserial PRIMARY KEY,
    candidate_id bigint NOT NULL,
    category_code text NOT NULL,
    theta numeric(6,3) NULL,
    fsp_verified boolean NOT NULL DEFAULT false,
    fsp_achievements_count integer NOT NULL DEFAULT 0 CHECK (fsp_achievements_count >= 0),
    fsp_achievements_weighted numeric(6,3) NOT NULL DEFAULT 0 CHECK (fsp_achievements_weighted >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_rank_signals_candidate_category UNIQUE (candidate_id, category_code)
);

CREATE INDEX ix_rank_signals_category_code ON rank_signals (category_code);
