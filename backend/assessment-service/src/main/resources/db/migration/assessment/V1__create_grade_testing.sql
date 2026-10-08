-- assessment grade-testing schema (docs/data_model.md §6.1): item_bank,
-- test_sessions, attempts, grade_history, cooldowns. candidate_id is a logical
-- reference to candidate.profiles.id (no cross-schema FK; docs/data_model.md §1,
-- HARD RULE). outbox_events is NOT created here (S5.4 / V2).

-- item_bank — банк заданий: якорные задания и семейства шаблонов; параметры
-- калибровки. Вариантность задаётся form_seed/variant_seed в test_sessions/
-- attempts, а не в банке (docs/data_model.md §6.1).
CREATE TABLE assessment.item_bank (
    id                  bigserial   PRIMARY KEY,
    item_kind           text        NOT NULL
                                    CHECK (item_kind IN ('anchor', 'template')),
    template_family     text        NULL
                                    CHECK (item_kind <> 'template' OR template_family IS NOT NULL),
    concept_code        text        NOT NULL,
    specialization_code text        NOT NULL,
    difficulty          numeric(5,3) NOT NULL,
    is_provisional      boolean     NOT NULL DEFAULT true,
    exposure_limit      integer     NULL CHECK (exposure_limit IS NULL OR exposure_limit >= 0),
    exposure_count      integer     NOT NULL DEFAULT 0 CHECK (exposure_count >= 0),
    payload             jsonb       NOT NULL,
    status              text        NOT NULL DEFAULT 'active'
                                    CHECK (status IN ('active', 'retired')),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_item_bank_specialization_difficulty
    ON assessment.item_bank (specialization_code, difficulty);
CREATE INDEX idx_item_bank_template_family
    ON assessment.item_bank (template_family);
CREATE INDEX idx_item_bank_item_kind
    ON assessment.item_bank (item_kind);

-- test_sessions — сессия прохождения: опрос → выбор грейда → тест; хранит θ и SE
-- (docs/data_model.md §6.1).
CREATE TABLE assessment.test_sessions (
    id                  bigserial   PRIMARY KEY,
    candidate_id        bigint      NOT NULL,
    industry_code       text        NULL,
    specialization_code text        NOT NULL,
    target_grade        text        NOT NULL,
    survey_answers      jsonb       NULL,
    declared_dimensions text[]      NULL,
    form_seed           bigint      NULL,
    status              text        NOT NULL DEFAULT 'started'
                                    CHECK (status IN ('started', 'in_progress', 'completed',
                                                      'abandoned', 'needs_review')),
    theta               numeric(6,3) NULL,
    theta_se            numeric(6,3) NULL,
    item_count          integer     NOT NULL DEFAULT 0 CHECK (item_count >= 0),
    started_at          timestamptz NOT NULL DEFAULT now(),
    completed_at        timestamptz NULL
);

CREATE INDEX idx_test_sessions_candidate_status
    ON assessment.test_sessions (candidate_id, status);
CREATE INDEX idx_test_sessions_specialization_code
    ON assessment.test_sessions (specialization_code);

-- attempts — ответы на задания внутри сессии; прокторинг-флаги с временными
-- метками (docs/data_model.md §6.1). Only session_id cascades on delete; item_id
-- has no cascade.
CREATE TABLE assessment.attempts (
    id               bigserial   PRIMARY KEY,
    session_id       bigint      NOT NULL
                                 REFERENCES assessment.test_sessions (id) ON DELETE CASCADE,
    item_id          bigint      NOT NULL
                                 REFERENCES assessment.item_bank (id),
    item_order       integer     NOT NULL CHECK (item_order >= 1),
    variant_seed     bigint      NULL,
    answer           jsonb       NULL,
    is_correct       boolean     NULL,
    response_ms      integer     NULL CHECK (response_ms IS NULL OR response_ms >= 0),
    proctoring_flags jsonb       NULL,
    served_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_attempts_session_item_order UNIQUE (session_id, item_order),
    CONSTRAINT uq_attempts_session_item UNIQUE (session_id, item_id)
);

CREATE INDEX idx_attempts_item_id
    ON assessment.attempts (item_id);

-- grade_history — append-only история смен грейда/категории; аудит §13
-- (docs/data_model.md §6.1). No UNIQUE (append-only).
CREATE TABLE assessment.grade_history (
    id                  bigserial   PRIMARY KEY,
    candidate_id        bigint      NOT NULL,
    grade               text        NOT NULL,
    specialization_code text        NOT NULL,
    direction           text        NOT NULL
                                    CHECK (direction IN ('initial', 'up', 'down', 'recheck')),
    session_id          bigint      NULL
                                    REFERENCES assessment.test_sessions (id),
    category_code       text        NULL,
    reason              text        NULL,
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_grade_history_candidate_created
    ON assessment.grade_history (candidate_id, created_at);
CREATE INDEX idx_grade_history_session_id
    ON assessment.grade_history (session_id);

-- cooldowns — ограничение частоты смены грейда (1 месяц); docs/testing_model.md
-- §7.3. Cooldown ставится только на смену грейда (scope = grade_change).
CREATE TABLE assessment.cooldowns (
    id              bigserial   PRIMARY KEY,
    candidate_id    bigint      NOT NULL,
    scope           text        NOT NULL DEFAULT 'grade_change'
                                CHECK (scope IN ('grade_change')),
    available_after timestamptz NOT NULL,
    set_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_cooldowns_candidate_scope UNIQUE (candidate_id, scope)
);

CREATE INDEX idx_cooldowns_available_after
    ON assessment.cooldowns (available_after);
