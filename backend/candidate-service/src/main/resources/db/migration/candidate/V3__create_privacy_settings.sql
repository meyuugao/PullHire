-- candidate.privacy_settings (docs/data_model.md §4.3). 1:1 with profiles; the
-- owner's visibility switches (further gated by the §12 rule).
CREATE TABLE privacy_settings (
    profile_id           bigint      PRIMARY KEY REFERENCES profiles (id) ON DELETE CASCADE,
    visible_to_employers boolean     NOT NULL DEFAULT true,
    show_fsp             boolean     NOT NULL DEFAULT true,
    show_contacts        boolean     NOT NULL DEFAULT false,
    updated_at           timestamptz NOT NULL DEFAULT now()
);
