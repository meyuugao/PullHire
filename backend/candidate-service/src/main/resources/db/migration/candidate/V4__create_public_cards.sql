-- candidate.public_cards (docs/data_model.md §4.4). Privacy-filtered snapshot and
-- source of candidate.card_updated. Carries no PII beyond the masked display_name.
CREATE TABLE public_cards (
    id                  bigserial   PRIMARY KEY,
    profile_id          bigint      NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    display_name        text        NOT NULL,
    headline            text        NULL,
    industry_code       text        NULL,
    specialization_code text        NULL,
    grade               text        NULL,
    stack               text[]      NULL,
    fsp_verified        boolean     NOT NULL DEFAULT false,
    contact_released    boolean     NOT NULL DEFAULT false,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_public_cards_profile_id UNIQUE (profile_id)
);

CREATE INDEX idx_public_cards_stack ON public_cards USING gin (stack);
