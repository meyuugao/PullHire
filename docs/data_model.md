# Модель данных

## Назначение документа

`docs/data_model.md` описывает логическую модель данных PullHire: схемы PostgreSQL
по числу бизнес-сервисов, сущности, поля, ключи, ограничения и связи. Документ служит
источником структуры хранения для `docs/architecture.md §5.2` и `§5.4`; механики описаны
отдельно — `docs/testing_model.md`, `docs/selection.md`, `docs/periodic_tasks.md`,
`docs/fsp_integration.md`.

Связанные документы: `docs/architecture.md`, `docs/testing_model.md`, `docs/selection.md`,
`docs/periodic_tasks.md`, `docs/fsp_integration.md`, `docs/mvp_scope.md`, `docs/assumptions.md`,
`docs/glossary.md`. Источники требований: `docs/requirements/roles.md`, `profiles.md`,
`testing.md`, `selection.md`.

Вне рамок: бизнес-правила и алгоритмы (mechanics-документы), контракты эндпоинтов
(`docs/api.md`), инфраструктура и контейнеры (`docs/architecture.md`), MVP-флаги
(`docs/mvp_scope.md`).

## 1. Общие соглашения

| Тема | Правило |
|---|---|
| Имена | snake_case для схем, таблиц, полей; коды перечислений — английский snake_case. |
| Схемы | 8 схем по числу сервисов: auth, candidate, fsp, assessment, selection, employer, outreach, notification. |
| Первичный ключ | `id bigserial` (или `bigint` для проекций-1:1), `PK`. |
| Внешний ключ | Только внутри одной схемы, `FK → table.id`. |
| HARD RULE | Cross-schema FK и cross-schema JOIN запрещены; интеграция между сервисами — только через события (`docs/architecture.md §4.2`). |
| Логическая ссылка | Поле вида `candidate_id`, `user_id`, `vacancy_id` на чужую схему — без FK, помечается «логическая ссылка». |
| Время | `timestamptz`, хранение в UTC; границы окон — в TZ платформы. `created_at`/`updated_at NOT NULL DEFAULT now()`. |
| Деньги | `numeric(12,2)` в рублях; диапазоны «от–до» — два поля + CHECK `to >= from`. |
| Перечисления | `text` + `CHECK ∈ (…)` в таблице-владельце; сводка — §11. |
| Полезная нагрузка | `jsonb` для payload/снимков; `text[]` для списков (stack, skills). |
| Проекции | Read-model таблицы сервиса (например `selection.candidate_categories`) наполняются событиями; источник истины — сервис-владелец. |
| `[ext]` | Опциональные сущности/поля, см. `docs/mvp_scope.md`. |
| Миграции | Flyway, по одной папке миграций на схему. |
| Исключение имён | Сущности семейства заданий (`TaskCycleConfig`, `EmployerTaskPrompt`, `Task`, `TaskVersion`, `TaskAssignment`, `TaskSubmission`, `TaskInterestSubscription`, `ProfileFreshness`) — PascalCase: исторически сложившаяся нотация, на неё ссылаются внешние документы (`docs/periodic_tasks.md`); все прочие таблицы — snake_case. |

Коды справочника (`specialization_code`, `industry_code`, `grade`, `concept_code` и т.п.)
проверяются сервисом-владельцем при записи против засеянного справочника `selection.taxonomy`;
drift выявляется на CI/seed-прогоне. Cross-schema FK на taxonomy запрещён (HARD RULE), поэтому
целостность кодов обеспечивается валидацией на запись, а не ограничением БД.

## 2. ERD (обзор по схемам)

```
auth.users 1-N auth.email_tokens
auth.users 1-N auth.refresh_tokens
auth.users 1-N auth.consents

candidate.profiles 1-N candidate.resumes
candidate.profiles 1-1 candidate.privacy_settings
candidate.profiles 1-1 candidate.public_cards

fsp.fsp_links 1-N fsp.achievements_cache

assessment.item_bank 1-N assessment.attempts
assessment.test_sessions 1-N assessment.attempts
assessment.test_sessions 1-N assessment.grade_history
assessment.cooldowns (per candidate)
assessment.TaskCycleConfig (singleton)
assessment.EmployerTaskPrompt (per vacancy × cycle)
assessment.Task 1-N assessment.TaskVersion
assessment.Task 1-N assessment.TaskAssignment
assessment.TaskAssignment 1-1 assessment.TaskSubmission
assessment.TaskInterestSubscription (per candidate)
assessment.ProfileFreshness (per candidate)

selection.taxonomy 1-N selection.taxonomy (parent)
selection.candidate_categories (проекция, per candidate)
selection.candidate_cards (проекция, per candidate)
selection.rank_signals (per candidate × category)

employer.companies 1-N employer.needs

outreach.invitations 1-N outreach.invitation_events
outreach.vacancies 1-N outreach.applications          [ext]

notification.notification_log
```

Межсхемные связи — только логические, через идентификаторы, без FK:

```
auth.users ── candidate.profiles ── fsp.fsp_links
assessment.candidate_id ── selection.candidate_categories ── outreach.invitations
employer.companies ── employer.needs
outreach.vacancies (vacancy_id) ── assessment.Task   [ext]
employer.needs (need_id) ── assessment.Task   (fallback, когда вакансия [ext] не построена)
```

## 3. Схема auth

Владелец — `auth-service`. Аутентификация, подтверждение email, роли, согласия 152-ФЗ
(`docs/architecture.md §6.1–§6.2`).

### 3.1. users

Назначение: учётные записи кандидатов и работодателей; источник `sub` для JWT.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| email | text | NOT NULL, UNIQUE |
| password_hash | text | NOT NULL — Argon2id/BCrypt |
| role | text | NOT NULL, CHECK ∈ (candidate, employer) |
| status | text | NOT NULL DEFAULT 'pending', CHECK ∈ (pending, active, blocked) |
| email_verified_at | timestamptz | NULL |
| deleted_at | timestamptz | NULL — soft-delete аккаунта (`docs/assumptions.md §10`) |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE email; btree role; btree status; btree deleted_at.
UNIQUE: `email`.

### 3.2. email_tokens

Назначение: одноразовые токены подтверждения адреса (§4 roles.md п.4).

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| user_id | bigint | NOT NULL, FK → users.id ON DELETE CASCADE |
| token_hash | text | NOT NULL, UNIQUE |
| purpose | text | NOT NULL DEFAULT 'verify_email', CHECK ∈ (verify_email) |
| expires_at | timestamptz | NOT NULL |
| used_at | timestamptz | NULL |
| created_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE token_hash; btree (user_id); btree expires_at.
UNIQUE: `token_hash`.

### 3.3. refresh_tokens

Назначение: refresh-сессии JWT RS256 с возможностью отзыва.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| user_id | bigint | NOT NULL, FK → users.id ON DELETE CASCADE |
| token_hash | text | NOT NULL, UNIQUE |
| expires_at | timestamptz | NOT NULL |
| revoked_at | timestamptz | NULL |
| created_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE token_hash; btree (user_id); btree expires_at.
UNIQUE: `token_hash`.

### 3.4. consents

Назначение: append-only журнал согласий 152-ФЗ (обработка, публикация профиля, связь с ФСП,
участие в регулярных заданиях и показ результата — `task_participation`, `docs/periodic_tasks.md §4` C1);
источник аудита §13.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| user_id | bigint | NOT NULL, FK → users.id ON DELETE CASCADE |
| consent_type | text | NOT NULL, CHECK ∈ (data_processing, profile_publication, fsp_link, task_participation) |
| granted | boolean | NOT NULL |
| policy_version | text | NOT NULL |
| granted_at | timestamptz | NULL |
| revoked_at | timestamptz | NULL |
| created_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: btree (user_id, consent_type, created_at).
UNIQUE: нет (append-only; текущее состояние = последняя запись по паре `user_id, consent_type`).

## 4. Схема candidate

Владелец — `candidate-service`. Единственный владелец «что видно» работодателю
(`docs/architecture.md §6.2`).

### 4.1. profiles

Назначение: профиль кандидата (форма и/или автораспознанный PDF-профиль).

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| user_id | bigint | NOT NULL, UNIQUE — логическая ссылка на auth.users.id |
| full_name | text | NOT NULL |
| contact_email | text | NULL |
| contact_phone | text | NULL |
| position_title | text | NULL |
| grade_declared | text | NULL — самообъявленный грейд |
| experience_years | numeric(4,1) | NULL, CHECK >= 0 |
| stack | text[] | NULL |
| skills | text[] | NULL |
| soft_skills | text[] | NULL |
| summary | text | NULL |
| specialization_code | text | NULL — логическая ссылка на selection.taxonomy |
| industry_code | text | NULL — логическая ссылка на selection.taxonomy |
| status | text | NOT NULL DEFAULT 'draft', CHECK ∈ (draft, published) |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE user_id; btree specialization_code; GIN stack.
UNIQUE: `user_id`.

### 4.2. resumes (+файлы)

Назначение: загруженные PDF-резюме и сгенерированный стандартизированный PDF; файлы —
в файловом volume, в БД только метаданные.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| profile_id | bigint | NOT NULL, FK → profiles.id ON DELETE CASCADE |
| kind | text | NOT NULL, CHECK ∈ (uploaded, generated) |
| file_name | text | NOT NULL |
| file_path | text | NOT NULL — путь в хранилище |
| mime_type | text | NULL |
| size_bytes | bigint | NULL, CHECK (size_bytes IS NULL OR size_bytes >= 0) |
| parse_status | text | NOT NULL DEFAULT 'pending', CHECK ∈ (pending, parsed, failed) |
| parsed_at | timestamptz | NULL |
| created_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: btree (profile_id); btree (kind).
UNIQUE: нет.

### 4.3. privacy_settings

Назначение: настройки видимости профиля для работодателя (перекрываются §12).

| Поле | Тип | Ограничения |
|---|---|---|
| profile_id | bigint | PK, FK → profiles.id ON DELETE CASCADE |
| visible_to_employers | boolean | NOT NULL DEFAULT true |
| show_fsp | boolean | NOT NULL DEFAULT true |
| show_contacts | boolean | NOT NULL DEFAULT false — контакты дополнительно гейтятся правилом §12 |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: PK.
UNIQUE: PK `profile_id` (1:1 с profiles).

### 4.4. public_cards

Назначение: privacy-filtered снимок карточки, источник события `candidate.card_updated`.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| profile_id | bigint | NOT NULL, UNIQUE, FK → profiles.id ON DELETE CASCADE |
| display_name | text | NOT NULL — ФИО или маскированное |
| headline | text | NULL |
| industry_code | text | NULL |
| specialization_code | text | NULL |
| grade | text | NULL — снимок текущего грейда |
| stack | text[] | NULL |
| fsp_verified | boolean | NOT NULL DEFAULT false |
| contact_released | boolean | NOT NULL DEFAULT false |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE profile_id; GIN stack.
UNIQUE: `profile_id`.

## 5. Схема fsp

Владелец — `fsp-service`. Граница интеграции с реестром ФСП
(`docs/fsp_integration.md`); на хакатоне — mock-адаптер.

### 5.1. fsp_links

Назначение: связь кандидата с ID участника ФСП; обработка случая «нет истории».

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| candidate_id | bigint | NOT NULL, UNIQUE — логическая ссылка на candidate.profiles.id |
| fsp_id | text | NOT NULL, UNIQUE |
| link_status | text | NOT NULL DEFAULT 'pending', CHECK ∈ (pending, linked, unlinked, error) |
| history_status | text | NOT NULL DEFAULT 'unknown', CHECK ∈ (unknown, present, no_history) |
| linked_at | timestamptz | NULL |
| last_sync_at | timestamptz | NULL |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE candidate_id; UNIQUE fsp_id.
UNIQUE: `candidate_id`, `fsp_id`.

### 5.2. achievements_cache

Назначение: кэш достижений ФСП для отображения в профиле; пустой набор допустим
(`history_status = no_history`).

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| fsp_link_id | bigint | NOT NULL, FK → fsp_links.id ON DELETE CASCADE |
| external_achievement_id | text | NULL |
| title | text | NOT NULL |
| event_type | text | NULL |
| achieved_at | date | NULL |
| place | integer | NULL, CHECK (place IS NULL OR place >= 1) |
| payload | jsonb | NULL |
| synced_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: btree (fsp_link_id); btree (achieved_at).
UNIQUE: `(fsp_link_id, external_achievement_id)`.

## 6. Схема assessment

Владелец — `assessment-service`. Грейд-тестирование и регулярные задания
(`docs/testing_model.md`, `docs/periodic_tasks.md`).

### 6.1. Грейд-тестирование: item_bank, test_sessions, attempts, grade_history, cooldowns

**item_bank** — банк заданий: якорные задания и семейства шаблонов; параметры калибровки.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| item_kind | text | NOT NULL, CHECK ∈ (anchor, template) |
| template_family | text | NULL — CHECK (item_kind = 'template' ⇒ template_family IS NOT NULL) |
| concept_code | text | NOT NULL |
| specialization_code | text | NOT NULL |
| difficulty | numeric(5,3) | NOT NULL — параметр `b` |
| is_provisional | boolean | NOT NULL DEFAULT true |
| exposure_limit | integer | NULL, CHECK >= 0 |
| exposure_count | integer | NOT NULL DEFAULT 0, CHECK >= 0 |
| payload | jsonb | NOT NULL — модель задания и ключ |
| status | text | NOT NULL DEFAULT 'active', CHECK ∈ (active, retired) |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: btree (specialization_code, difficulty); btree template_family; btree item_kind.
UNIQUE: нет. Вариантность задаётся seed формы `test_sessions.form_seed` (набор вариантов сессии)
и item-seed `attempts.variant_seed` (вариант конкретного элемента); якорные задания не имеют `template_family`.

**test_sessions** — сессия прохождения: опрос → выбор грейда → тест; хранит θ и SE.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| candidate_id | bigint | NOT NULL — логическая ссылка на candidate.profiles.id |
| industry_code | text | NULL |
| specialization_code | text | NOT NULL |
| target_grade | text | NOT NULL — заявленный грейд |
| survey_answers | jsonb | NULL — ответы обязательного этапа SURVEY (`docs/testing_model.md §2.1`) |
| declared_dimensions | text[] | NULL — заявленные в опросе отрасль/специализация/грейд |
| form_seed | bigint | NULL — seed формы (набор вариантов заданий сессии) |
| status | text | NOT NULL DEFAULT 'started', CHECK ∈ (started, in_progress, completed, abandoned, needs_review) |
| theta | numeric(6,3) | NULL |
| theta_se | numeric(6,3) | NULL |
| item_count | integer | NOT NULL DEFAULT 0, CHECK >= 0 |
| started_at | timestamptz | NOT NULL DEFAULT now() |
| completed_at | timestamptz | NULL |

Индексы: btree (candidate_id, status); btree specialization_code.
UNIQUE: нет.

**attempts** — ответы на задания внутри сессии; прокторинг-флаги с временными метками.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| session_id | bigint | NOT NULL, FK → test_sessions.id ON DELETE CASCADE |
| item_id | bigint | NOT NULL, FK → item_bank.id |
| item_order | integer | NOT NULL, CHECK >= 1 |
| variant_seed | bigint | NULL — seed варианта элемента в рамках `test_sessions.form_seed` |
| answer | jsonb | NULL |
| is_correct | boolean | NULL |
| response_ms | integer | NULL, CHECK >= 0 |
| proctoring_flags | jsonb | NULL — без авто-провала, только для проверки человеком |
| served_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE (session_id, item_order); btree item_id.
UNIQUE: `(session_id, item_order)`; `(session_id, item_id)`.

**grade_history** — append-only история смен грейда/категории; аудит §13.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| candidate_id | bigint | NOT NULL — логическая ссылка на candidate.profiles.id |
| grade | text | NOT NULL |
| specialization_code | text | NOT NULL |
| direction | text | NOT NULL, CHECK ∈ (initial, up, down, recheck) |
| session_id | bigint | NULL, FK → test_sessions.id |
| category_code | text | NULL |
| reason | text | NULL |
| created_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: btree (candidate_id, created_at); btree session_id.
UNIQUE: нет (append-only). Понижение — только по инициативе кандидата (`docs/testing_model.md §7.2`).

**cooldowns** — ограничение частоты смены грейда (1 месяц); `docs/testing_model.md §7.3`.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| candidate_id | bigint | NOT NULL — логическая ссылка на candidate.profiles.id |
| scope | text | NOT NULL DEFAULT 'grade_change', CHECK ∈ (grade_change) |
| available_after | timestamptz | NOT NULL |
| set_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE (candidate_id, scope); btree available_after.
UNIQUE: `(candidate_id, scope)`. Cooldown ставится только на смену грейда; провал и уход на уровень ниже попытку не сжигают — для этих случаев запись не создаётся (`docs/testing_model.md §7.3`).

### 6.2. Регулярные задания: TaskCycleConfig, EmployerTaskPrompt, Task, TaskVersion, TaskAssignment, TaskSubmission

Поля сущностей — здесь; правила механики — `docs/periodic_tasks.md`.

**TaskCycleConfig** — singleton-конфигурация каденса и окон.

| Поле | Тип | Ограничения |
|---|---|---|
| id | smallint | PK, CHECK (id = 1) — singleton |
| n_days | integer | NOT NULL DEFAULT 7, CHECK > 0 |
| deadline_days | integer | NOT NULL DEFAULT 7, CHECK > 0 |
| timezone | text | NOT NULL DEFAULT 'Europe/Moscow' |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: PK.
UNIQUE: PK `id` (единственная строка).

**EmployerTaskPrompt** — промпт работодателю по вакансии на окно {add|update|keep}.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| employer_id | bigint | NOT NULL — логическая ссылка на auth.users.id |
| vacancy_id | bigint | NULL — логическая ссылка на outreach.vacancies.id ([ext]); NULL при fallback |
| need_id | bigint | NULL — логическая ссылка на employer.needs.id; заполняется в fallback-режиме |
| cycle_date | date | NOT NULL |
| status | text | NOT NULL DEFAULT 'pending', CHECK ∈ (pending, resolved, expired) |
| action | text | NULL, CHECK (action IN ('add','update','keep')) |
| resolved_at | timestamptz | NULL |
| created_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE (vacancy_id, cycle_date) WHERE vacancy_id IS NOT NULL; UNIQUE (need_id, cycle_date) WHERE need_id IS NOT NULL; btree (employer_id, cycle_date); btree status.
UNIQUE: `(vacancy_id, cycle_date) WHERE vacancy_id IS NOT NULL`; `(need_id, cycle_date) WHERE need_id IS NOT NULL` (fallback-режим «потребность/компания»).

**Task** — задание работодателя (агрегат), текущая активная версия и счётчик keep.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| employer_id | bigint | NOT NULL — логическая ссылка на auth.users.id |
| vacancy_id | bigint | NULL — логическая ссылка на outreach.vacancies.id |
| need_id | bigint | NULL — логическая ссылка на employer.needs.id; заполняется в fallback-режиме (нет вакансии) |
| status | text | NOT NULL DEFAULT 'draft', CHECK ∈ (draft, active, retired) |
| current_version_id | bigint | NULL, FK → TaskVersion.id |
| keep_count | integer | NOT NULL DEFAULT 0, CHECK >= 0 |
| stale_since | timestamptz | NULL — `[ext]` |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| retired_at | timestamptz | NULL |

Индексы: btree (employer_id, status); btree vacancy_id; btree need_id.
UNIQUE: нет. Адресная выдача конкретному кандидату запрещена (`docs/periodic_tasks.md §3.4`).
Якорение задания: `vacancy_id` при наличии вакансии, иначе `need_id` — fallback «потребность/компания»
(`docs/assumptions.md §11`); оба поля NULLable, пул выдачи поддерживает оба якоря.

**TaskVersion** — неизменяемая версия задания (snapshot).

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| task_id | bigint | NOT NULL, FK → Task.id ON DELETE CASCADE |
| version_no | integer | NOT NULL, CHECK >= 1 |
| title | text | NOT NULL |
| body | text | NOT NULL |
| format | text | NOT NULL, CHECK ∈ (code, text, quiz) |
| autocheck_spec | jsonb | NULL |
| created_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE (task_id, version_no); btree task_id.
UNIQUE: `(task_id, version_no)`. Строки не обновляются (immutable).

**TaskAssignment** — выдача задания кандидату в окне; одна на окно.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| candidate_id | bigint | NOT NULL — логическая ссылка на candidate.profiles.id |
| task_id | bigint | NOT NULL, FK → Task.id |
| version_id | bigint | NOT NULL, FK → TaskVersion.id |
| cycle_date | date | NOT NULL |
| status | text | NOT NULL DEFAULT 'assigned', CHECK ∈ (assigned, opened, submitted, evaluated, expired) |
| assigned_at | timestamptz | NOT NULL DEFAULT now() |
| deadline_at | timestamptz | NOT NULL |

Индексы: UNIQUE (candidate_id, cycle_date); btree task_id; btree status.
UNIQUE: `(candidate_id, cycle_date)`.

**TaskSubmission** — ответ кандидата («решить» / «предложить подход») и оценка работодателя 1:1.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| assignment_id | bigint | NOT NULL, UNIQUE, FK → TaskAssignment.id ON DELETE CASCADE |
| kind | text | NOT NULL, CHECK ∈ (solution, approach) |
| content | text | NOT NULL |
| submitted_at | timestamptz | NOT NULL DEFAULT now() |
| result | text | NOT NULL DEFAULT 'unreviewed', CHECK ∈ (positive, negative, unreviewed) |
| reviewed_by | bigint | NULL — логическая ссылка на auth.users.id |
| reviewed_at | timestamptz | NULL |

Индексы: UNIQUE assignment_id.
UNIQUE: `assignment_id` (1:1 с TaskAssignment). Результат не меняет грейд/категорию.

### 6.3. Подписка и свежесть: TaskInterestSubscription, ProfileFreshness

**TaskInterestSubscription** — опт-ин кандидата на регулярные задания (по умолчанию OFF).

| Поле | Тип | Ограничения |
|---|---|---|
| candidate_id | bigint | PK — логическая ссылка на candidate.profiles.id |
| enabled | boolean | NOT NULL DEFAULT false |
| scope | jsonb | NULL — `[ext]` опциональный фильтр по грейду; скоуп по специализации обязателен и задаётся вне `scope` |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: PK.
UNIQUE: PK `candidate_id` (1:1, `docs/periodic_tasks.md §4`). Скоуп пула по специализации обязателен
(кандидат видит задания своей специализации); опционален только фильтр по грейду (`[ext]`).

**ProfileFreshness** — атрибут свежести профиля; display-only, не входит в ранжирование.

| Поле | Тип | Ограничения |
|---|---|---|
| candidate_id | bigint | PK — логическая ссылка на candidate.profiles.id |
| last_task_activity_at | timestamptz | NULL |
| tasks_submitted | integer | NOT NULL DEFAULT 0, CHECK >= 0 |
| tasks_positive | integer | NOT NULL DEFAULT 0, CHECK >= 0 |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: PK.
UNIQUE: PK `candidate_id` (1:1). HARD RULE: не влияет на grade/grade_history/категорию
(`docs/testing_model.md §7`, `docs/periodic_tasks.md §6.3`).

## 7. Схема selection

Владелец — `selection-service`. Каталог, категории и проекции для ранжирования
(`docs/selection.md`).

### 7.1. taxonomy

Назначение: справочник отраслей, специализаций и грейдов; категория = специализация × грейд.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| kind | text | NOT NULL, CHECK ∈ (industry, specialization, grade) |
| code | text | NOT NULL |
| name | text | NOT NULL |
| parent_id | bigint | NULL, FK → taxonomy.id |
| grade_order | integer | NULL, CHECK (grade_order IS NULL OR grade_order >= 0) |
| is_active | boolean | NOT NULL DEFAULT true |
| created_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE (kind, code); btree parent_id; btree (kind, is_active).
UNIQUE: `(kind, code)`.

### 7.2. candidate_categories (проекция)

Назначение: текущая категория кандидата; проекция события `assessment.category_assigned`.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| candidate_id | bigint | NOT NULL, UNIQUE — логическая ссылка на candidate.profiles.id |
| specialization_code | text | NOT NULL |
| grade | text | NOT NULL |
| category_code | text | NOT NULL |
| theta | numeric(6,3) | NULL |
| source_event_id | text | NULL — идемпотентность консьюмера |
| assigned_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE candidate_id; btree category_code; btree (specialization_code, grade).
UNIQUE: `candidate_id`.

### 7.3. candidate_cards (без PII)

Назначение: поисковая проекция карточки без PII; наполняется `candidate.card_updated`
(только согласованные поля, `docs/architecture.md §6.2`).

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| candidate_id | bigint | NOT NULL, UNIQUE — логическая ссылка на candidate.profiles.id |
| display_name | text | NOT NULL — маскированное |
| specialization_code | text | NOT NULL |
| grade | text | NOT NULL |
| stack | text[] | NULL |
| fsp_verified | boolean | NOT NULL DEFAULT false |
| fsp_achievements_count | integer | NOT NULL DEFAULT 0, CHECK >= 0 |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE candidate_id; btree (specialization_code, grade); GIN stack.
UNIQUE: `candidate_id`. Контактные данные в проекции отсутствуют (§12).

### 7.4. rank_signals

Назначение: компоненты ранжирования внутри категории (сила подтверждённого профиля).

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| candidate_id | bigint | NOT NULL — логическая ссылка на candidate.profiles.id |
| category_code | text | NOT NULL |
| theta | numeric(6,3) | NULL |
| fsp_verified | boolean | NOT NULL DEFAULT false |
| fsp_achievements_count | integer | NOT NULL DEFAULT 0, CHECK >= 0 |
| fsp_achievements_weighted | numeric(6,3) | NOT NULL DEFAULT 0, CHECK >= 0 — сумма достижений с весом результата (победитель/призёр > участник отбора) |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE (candidate_id, category_code); btree category_code.
UNIQUE: `(candidate_id, category_code)`. В ранжировании учитывается не только число
достижений (`fsp_achievements_count`), но и их результативность
(`fsp_achievements_weighted`), чтобы профиль нельзя было «набить» одними
онлайн-явками; формула ранжирования — `docs/selection.md`.

## 8. Схема employer

Владелец — `employer-service`. Профиль компании и описание потребности.

### 8.1. companies

Назначение: профиль компании-работодателя.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| user_id | bigint | NOT NULL, UNIQUE — логическая ссылка на auth.users.id |
| name | text | NOT NULL |
| description | text | NULL |
| industry | text | NULL |
| contact_email | text | NULL |
| contact_phone | text | NULL |
| website | text | NULL |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE user_id.
UNIQUE: `user_id`.

### 8.2. needs

Назначение: описание потребности (вход формирования подборки).

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| company_id | bigint | NOT NULL, FK → companies.id ON DELETE CASCADE |
| title | text | NOT NULL |
| description | text | NULL |
| specialization_code | text | NOT NULL |
| grade | text | NOT NULL |
| stack | text[] | NULL |
| status | text | NOT NULL DEFAULT 'active', CHECK ∈ (active, archived) |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: btree company_id; btree (specialization_code, grade).
UNIQUE: нет.

## 9. Схема outreach

Владелец — `outreach-service`. LHH-механика выхода на контакт, статусы и раскрытие контактов
(`docs/selection.md`, roles.md пп.1–3).

### 9.1. invitations

Назначение: приглашение конкретному кандидату; зарплата обязательна, привязка к вакансии
не обязательна.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| employer_id | bigint | NOT NULL — логическая ссылка на auth.users.id |
| candidate_id | bigint | NOT NULL — логическая ссылка на candidate.profiles.id |
| need_id | bigint | NULL — логическая ссылка на employer.needs.id |
| vacancy_id | bigint | NULL — логическая ссылка `[ext]` |
| message | text | NULL — описание предложения |
| company_name | text | NOT NULL |
| employer_contact | jsonb | NULL — снимок «способа связи» работодателя на момент создания (cross-schema join запрещён) |
| salary_from | numeric(12,2) | NOT NULL, CHECK >= 0 |
| salary_to | numeric(12,2) | NOT NULL, CHECK >= salary_from |
| salary_basis | text | NOT NULL DEFAULT 'gross', CHECK ∈ (gross, net) — явная пометка: гросс или «на руки» |
| status | text | NOT NULL DEFAULT 'sent', CHECK ∈ (sent, viewed, accepted, declined, expired) |
| released_contact | jsonb | NULL — заполняется только при `contact_released` |
| contact_released_at | timestamptz | NULL |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: btree (candidate_id, status); btree (employer_id, status).
UNIQUE: нет. Контакты раскрываются только после принятия/отклика (§12, roles.md п.2).

### 9.2. invitation_events

Назначение: append-only аудит переходов статусов; аудит §13.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| invitation_id | bigint | NOT NULL, FK → invitations.id ON DELETE CASCADE |
| event_type | text | NOT NULL, CHECK ∈ (sent, viewed, accepted, declined, expired, contact_released) |
| actor_role | text | NULL, CHECK ∈ (employer, candidate, system) |
| payload | jsonb | NULL |
| occurred_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: btree (invitation_id, occurred_at).
UNIQUE: нет (append-only).

### 9.3. [ext] vacancies, applications

**vacancies** — опубликованная вакансия работодателя; модуль `[ext]`.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| employer_id | bigint | NOT NULL — логическая ссылка на auth.users.id |
| company_id | bigint | NULL — логическая ссылка на employer.companies.id |
| title | text | NOT NULL |
| description | text | NULL |
| specialization_code | text | NOT NULL |
| grade | text | NOT NULL |
| stack | text[] | NULL |
| salary_from | numeric(12,2) | NOT NULL, CHECK >= 0 |
| salary_to | numeric(12,2) | NOT NULL, CHECK >= salary_from |
| salary_basis | text | NOT NULL DEFAULT 'gross', CHECK ∈ (gross, net) — явная пометка: гросс или «на руки» |
| status | text | NOT NULL DEFAULT 'published', CHECK ∈ (draft, published, closed) |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |
| closed_at | timestamptz | NULL |

Индексы: btree (employer_id, status); btree (specialization_code, grade).
UNIQUE: нет.

**applications** — самостоятельный отклик кандидата на вакансию; модуль `[ext]`.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| vacancy_id | bigint | NOT NULL, FK → vacancies.id ON DELETE CASCADE |
| candidate_id | bigint | NOT NULL — логическая ссылка на candidate.profiles.id |
| status | text | NOT NULL DEFAULT 'submitted', CHECK ∈ (submitted, viewed, accepted, rejected, withdrawn) |
| released_contact | jsonb | NULL — заполняется при раскрытии контактов (§12) |
| contact_released_at | timestamptz | NULL |
| applied_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |

Индексы: UNIQUE (vacancy_id, candidate_id); btree (candidate_id, status).
UNIQUE: `(vacancy_id, candidate_id)`. Отклик раскрывает контакты (§12).

## 10. Схема notification

Владелец — `notification-service`. Исходящая почта; изоляция SMTP-креденшелов.

### 10.1. notification_log

Назначение: журнал исходящих email; аудит §13.

| Поле | Тип | Ограничения |
|---|---|---|
| id | bigserial | PK |
| recipient_user_id | bigint | NULL — логическая ссылка на auth.users.id |
| recipient_email | text | NOT NULL |
| template_code | text | NOT NULL |
| event_type | text | NOT NULL |
| source_event_id | text | NULL, UNIQUE — идемпотентность консьюмера |
| status | text | NOT NULL DEFAULT 'queued', CHECK ∈ (queued, sent, failed) |
| provider_message_id | text | NULL |
| error | text | NULL |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| sent_at | timestamptz | NULL |

Индексы: UNIQUE source_event_id; btree recipient_user_id; btree status; btree created_at.
UNIQUE: `source_event_id`.

## 11. Статусы и перечисления (сводный указатель)

Значения `CHECK` определены в таблицах-владельцах; здесь — только указатель, без дублирования.
Доменные правила переходов — в mechanics-документах.

| Домен | Поле | Раздел |
|---|---|---|
| Роль пользователя | auth.users.role | §3.1 |
| Статус пользователя | auth.users.status | §3.1 |
| Назначение email-токена | auth.email_tokens.purpose | §3.2 |
| Тип согласия | auth.consents.consent_type | §3.4 |
| Статус профиля | candidate.profiles.status | §4.1 |
| Источник резюме | candidate.resumes.kind | §4.2 |
| Статус парсинга | candidate.resumes.parse_status | §4.2 |
| Статус связи ФСП | fsp.fsp_links.link_status | §5.1 |
| История ФСП | fsp.fsp_links.history_status | §5.1 |
| Тип задания банка | assessment.item_bank.item_kind | §6.1 |
| Статус задания банка | assessment.item_bank.status | §6.1 |
| Статус сессии | assessment.test_sessions.status | §6.1 |
| Направление смены грейда | assessment.grade_history.direction | §6.1 |
| Область cooldown | assessment.cooldowns.scope | §6.1 |
| Статус промпта | assessment.EmployerTaskPrompt.status | §6.2 |
| Действие промпта | assessment.EmployerTaskPrompt.action | §6.2 |
| Статус задания | assessment.Task.status | §6.2 |
| Формат задания | assessment.TaskVersion.format | §6.2 |
| Статус выдачи | assessment.TaskAssignment.status | §6.2 |
| Вид ответа | assessment.TaskSubmission.kind | §6.2 |
| Результат проверки | assessment.TaskSubmission.result | §6.2 |
| Вид справочника | selection.taxonomy.kind | §7.1 |
| Статус потребности | employer.needs.status | §8.2 |
| Статус приглашения | outreach.invitations.status | §9.1 |
| Тип события приглашения | outreach.invitation_events.event_type | §9.2 |
| Роль актора события | outreach.invitation_events.actor_role | §9.2 |
| Статус вакансии `[ext]` | outreach.vacancies.status | §9.3 |
| Статус отклика `[ext]` | outreach.applications.status | §9.3 |
| Статус уведомления | notification.notification_log.status | §10.1 |

## 12. Видимость данных и согласия (152-ФЗ)

Правила видимости обязательны (`docs/requirements/roles.md`, «Данные и правила видимости»);
структура хранения — на стороне сервиса-владельца.

| # | Правило | Где обеспечивается |
|---|---|---|
| 1 | Зарплата обязательна в вакансии и приглашении (диапазон «от–до», руб.). | outreach §9.1, §9.3 |
| 2 | Контакты кандидата раскрываются работодателю только после принятия приглашения или самостоятельного отклика. | outreach §9.1 (`released_contact`, `contact_released_at`); событие `candidate.contact_released` |
| 3 | Приглашения и отклики имеют статусы; стороны видят статус только по своим записям. | outreach §9.1, §9.3 |
| 4 | Регистрация по email с подтверждением; верификация компании и модерация вакансий не требуются (защита от недобросовестных работодателей — отдельная концепция). | auth §3.1–§3.2 |
| 5 | Согласие на обработку и публикацию данных профиля фиксируется и аудируется. | auth.consents §3.4, §13 |
| 6 | Настройки приватности («что видно») принадлежат кандидату. | candidate.privacy_settings §4.3, public_cards §4.4 |
| 7 | Поисковая проекция не содержит PII; контакты в неё не попадают. | selection.candidate_cards §7.3 |

До раскрытия контакты хранятся только в `candidate.profiles` и не передаются работодателю.
Событие `candidate.card_updated` несёт только согласованные поля, поэтому поисковый индекс
структурно не может утечь PII (`docs/architecture.md §6.2`).

## 13. Аудит и история изменений

История — append-only там, где указано; чувствительные данные в журналах не дублируются.

| Журнал | Таблица | Что фиксирует | Раздел |
|---|---|---|---|
| История грейдов | assessment.grade_history | смены грейда/категории, направление, сессию | §6.1 |
| События приглашений | outreach.invitation_events | переходы статусов, актора, время | §9.2 |
| Согласия | auth.consents | grant/revoke по типам согласий и версии политики | §3.4 |
| Soft-delete аккаунтов | auth.users.deleted_at | пометка удаления аккаунта и отзыв доступа | §3.1 |
| Уведомления | notification.notification_log | факт, статус и ошибки отправки email | §10.1 |

Событийная идемпотентность обеспечивается полем `source_event_id` консьюмеров
(§7.2, §10.1) и уникальными ключами (`docs/architecture.md §4.2`); outbox/схема конверта —
там же. Журналы не содержат паролей, токенов и контактных данных в открытом виде.
