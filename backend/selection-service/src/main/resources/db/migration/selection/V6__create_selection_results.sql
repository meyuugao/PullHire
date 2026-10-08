-- selection.selection_results (docs/api.md §3.5, docs/selection.md §5). Snapshot of a formed selection.
-- Immutable: refinement creates a new row pointing at the original via parent_selection_id; originals are never overwritten.
-- items holds only candidate_id + qualitative explanation strings; theta value is never stored here (not disclosed to employer).
CREATE TABLE selection_results (
    id                      bigserial    PRIMARY KEY,
    parent_selection_id     bigint       NULL REFERENCES selection_results(id),
    request                 jsonb        NOT NULL,
    recommended_categories  jsonb        NOT NULL,
    items                   jsonb        NOT NULL,
    created_at              timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_selection_results_created_at ON selection_results (created_at);
CREATE INDEX idx_selection_results_parent_selection_id ON selection_results (parent_selection_id);
