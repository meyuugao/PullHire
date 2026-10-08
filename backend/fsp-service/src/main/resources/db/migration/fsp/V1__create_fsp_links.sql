-- fsp.fsp_links (docs/data_model.md §5.1). Link between a candidate and an FSP
-- participant id. candidate_id is a logical reference to candidate.profiles.id
-- (no cross-schema FK; docs/data_model.md §1, HARD RULE). link_status separates the
-- technical link state from history_status, which records whether the registry
-- returned any achievements (docs/fsp_integration.md §3/§6).
CREATE TABLE fsp_links (
    id             bigserial   PRIMARY KEY,
    candidate_id   bigint      NOT NULL,
    fsp_id         text        NOT NULL,
    link_status    text        NOT NULL DEFAULT 'pending'
                               CHECK (link_status IN ('pending', 'linked', 'unlinked', 'error')),
    history_status text        NOT NULL DEFAULT 'unknown'
                               CHECK (history_status IN ('unknown', 'present', 'no_history')),
    linked_at      timestamptz NULL,
    last_sync_at   timestamptz NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_fsp_links_candidate_id UNIQUE (candidate_id),
    CONSTRAINT uq_fsp_links_fsp_id UNIQUE (fsp_id)
);
