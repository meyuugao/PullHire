-- employer companies + needs schema (docs/data_model.md §8): companies,
-- needs. user_id is a logical reference to auth.users.id (no cross-schema FK;
-- docs/data_model.md §1, HARD RULE). outbox_events is NOT created here (V2).

-- companies — профиль компании-работодателя (docs/data_model.md §8.1).
CREATE TABLE employer.companies (
    id            bigserial   PRIMARY KEY,
    user_id       bigint      NOT NULL,
    name          text        NOT NULL,
    description   text        NULL,
    industry      text        NULL,
    contact_email text        NULL,
    contact_phone text        NULL,
    website       text        NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_companies_user_id UNIQUE (user_id)
);

-- needs — описание потребности (вход формирования подборки)
-- (docs/data_model.md §8.2). Grade has no CHECK: codes are validated
-- against selection.taxonomy by the service on write, not by the DB
-- (docs/data_model.md §1).
CREATE TABLE employer.needs (
    id                  bigserial   PRIMARY KEY,
    company_id          bigint      NOT NULL
                                    REFERENCES employer.companies (id) ON DELETE CASCADE,
    title               text        NOT NULL,
    description         text        NULL,
    specialization_code text        NOT NULL,
    grade               text        NOT NULL,
    stack               text[]      NULL,
    status              text        NOT NULL DEFAULT 'active'
                                    CHECK (status IN ('active', 'archived')),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_needs_company_id
    ON employer.needs (company_id);
CREATE INDEX idx_needs_specialization_grade
    ON employer.needs (specialization_code, grade);
