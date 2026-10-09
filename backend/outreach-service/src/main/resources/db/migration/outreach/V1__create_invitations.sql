-- outreach.invitations (docs/data_model.md §9.1): invitation to a specific
-- candidate; salary is mandatory, vacancy binding is optional. employer_id,
-- candidate_id, need_id and vacancy_id are logical references (no cross-schema
-- FK; docs/data_model.md §1, HARD RULE). No UNIQUE: repeat invitations are
-- allowed by the model.
CREATE TABLE invitations (
    id                  bigserial     PRIMARY KEY,
    employer_id         bigint        NOT NULL,
    candidate_id        bigint        NOT NULL,
    need_id             bigint        NULL,
    vacancy_id          bigint        NULL,
    message             text          NULL,
    company_name        text          NOT NULL,
    employer_contact    jsonb         NULL,
    salary_from         numeric(12,2) NOT NULL CHECK (salary_from >= 0),
    salary_to           numeric(12,2) NOT NULL CHECK (salary_to >= salary_from),
    salary_basis        text          NOT NULL DEFAULT 'gross'
                                      CHECK (salary_basis IN ('gross', 'net')),
    status              text          NOT NULL DEFAULT 'sent'
                                      CHECK (status IN ('sent', 'viewed', 'accepted', 'declined', 'expired')),
    released_contact    jsonb         NULL,
    contact_released_at timestamptz   NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_invitations_candidate_status
    ON invitations (candidate_id, status);
CREATE INDEX idx_invitations_employer_status
    ON invitations (employer_id, status);
