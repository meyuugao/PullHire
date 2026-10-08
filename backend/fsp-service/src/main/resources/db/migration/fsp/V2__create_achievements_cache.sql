-- fsp.achievements_cache (docs/data_model.md §5.2). Display cache of an FSP link's
-- achievements; an empty set is valid when history_status = no_history. payload jsonb
-- absorbs the unknown real registry structure; the base fields (title, event_type,
-- achieved_at, place) stay the stable contract (docs/fsp_integration.md §7).
CREATE TABLE achievements_cache (
    id                      bigserial   PRIMARY KEY,
    fsp_link_id             bigint      NOT NULL REFERENCES fsp_links (id) ON DELETE CASCADE,
    external_achievement_id text        NULL,
    title                   text        NOT NULL,
    event_type              text        NULL,
    achieved_at             date        NULL,
    place                   integer     NULL CHECK (place IS NULL OR place >= 1),
    payload                 jsonb       NULL,
    synced_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_achievements_cache_link_external UNIQUE (fsp_link_id, external_achievement_id)
);

CREATE INDEX idx_achievements_cache_fsp_link_id ON achievements_cache (fsp_link_id);
CREATE INDEX idx_achievements_cache_achieved_at ON achievements_cache (achieved_at);
