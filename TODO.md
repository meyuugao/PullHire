# PullHire — TODO / Slice Tracker

> Трекер выполнения. Живёт в корне репозитория, обновляется по мере работы.
> Источник требований и архитектуры: [`docs/`](./docs/) — ключевые:
> [mvp_scope.md](docs/mvp_scope.md), [architecture.md](docs/architecture.md),
> [data_model.md](docs/data_model.md), [api.md](docs/api.md),
> [requirements/](docs/requirements/), [assumptions.md](docs/assumptions.md).

## Цель проекта

PullHire — цифровая платформа-агрегатор ИТ-вакансий с верифицированным профилем достижений участника ФСП. Категория кандидата (специализация × грейд) присваивается по обязательному пути «опрос → заявленный грейд → тест», а выход на контакт инициирует работодатель (LHH-механика). Успех MVP — сквозной 7-шаговый демо-сценарий (кандидат → категория → подборка → приглашение → раскрытие контактов) на воспроизводимом локальном стенде.

## Легенда статусов

| Маркер | Значение |
|--------|----------|
| `[ ]`  | не начат |
| `[~]`  | в работе |
| `[x]`  | готов (все exit criteria закрыты) |
| `[-]`  | отменён (с обязательной пометкой причины) |

## Правила ведения трекера

1. **Один слайс за раз.** Ровно один слайс может иметь статус `[~]`; новый слайс не открывается, пока текущий не переведён в `[x]` или `[-]`.
2. Слайс не может стать `[~]`, пока не закрыты все слайсы из его `depends_on`.
3. Слайс становится `[x]` только когда **все** чекбоксы exit criteria `[x]`.
4. Статус меняется прямо в заголовке слайса и в индексе ниже.
5. Расхождения с `docs/` фиксируются в OPEN QUESTIONS, а не правкой `docs/` на ходу.

## Индекс слайсов

### Foundation
- [x] S0 — Bootstrap: репозиторий, Gradle multi-module, docker-compose каркас, CI
- [ ] S1 — auth-service + notification-service: идентичность, JWT, согласия 152-ФЗ, почта
- [ ] S2 — Событийная шина, outbox, идемпотентность (механика), наблюдаемость

### Domain
- [ ] S3 — candidate-service: профиль, резюме/PDF, приватность, карточка
- [ ] S4 — fsp-service: mock-адаптер ФСП, линк ID, кэш достижений
- [ ] S5 — assessment-service: опрос, грейд, адаптивный IRT-lite тест, категория, cooldown
- [ ] S6 — employer-service: компания и потребности
- [ ] S7 — selection-service: таксономия, категории, ранжирование, подборка с объяснимостью
- [ ] S8 — outreach-service: LHH-приглашения, жизненный цикл, раскрытие контактов

### Cross-cutting
- [ ] S9 — Сквозные NFR: протокольный Idempotency-Key, rate-limit, аудит PII, footprint

### UI
- [ ] S10 — Frontend: кабинет кандидата (SPA)
- [ ] S11 — Frontend: кабинет работодателя (SPA)

### Acceptance
- [ ] S12 — Seed, развёртывание, сквозное демо (7 шагов)
- [ ] S13 — Сопроводительная документация, презентация, видео-фолбэк

### Optional `[opt]`
> Не входят в MVP-скоуп. Берутся только после `[x]` на S12, по одному за раз.

- [ ] S14 — [opt] Регулярные задания (periodic tasks → assessment [ext])
- [ ] S15 — [opt] Вакансии и отклики (outreach [ext])
- [ ] S16 — [opt] Мобильная адаптивность
- [ ] S17 — [opt] ML/NLP-парсинг резюме

## OPEN QUESTIONS

### OQ-1 — Числовые параметры тестирования и ранжирования
- Вопрос: конкретные cut-scores, порог SE, min/max число заданий, веса формулы ранжирования и размеры валидационного набора в `docs/` отсутствуют (assumptions ссылается, но значений нет).
- Влияет на: S5, S7, S13.
- Решение: реализуются как конфигурируемые значения; содержательные (не иллюстративные) результаты валидации — после фиксации параметров. (дата: —)

### OQ-2 — Путь чтения freshness работодателем
- Вопрос: sync-GET employer→assessment `[opt]` или проекция `[ext]` — не выбран.
- Влияет на: S14, S11.
- Решение: до решения freshness остаётся display-only. (дата: —)

### OQ-3 — Порядок наполнения employer_contact snapshot (TD-4)
- Вопрос: не детализирован порядок наполнения snapshot `employer_contact`.
- Влияет на: S8.
- Решение: слайс реализуется с типовым snapshot; уточнение при старте S8. (дата: —)

### OQ-4 — PDF/PPTX ТЗ машинно не прочитаны
- Вопрос: возможны требования, отсутствующие в md-дампе (`docs/source/*`).
- Влияет на: S12, S13.
- Решение: ручная сверка до входа в S12/S13. (дата: —)

### OQ-5 — Module count (S0): «9 сервисов» vs 8 модулей
- Вопрос: `TODO.md:139` называет «9 сервисов», тогда как `docs/architecture.md §1.1/§2/§3.1`, `docs/deployment.md §2`, `docs/mvp_scope.md §2.7` определяют 8 бизнес-сервисов + api-gateway (Nginx) + SPA.
- Влияет на: S0.
- Решение: 8 Gradle-модулей (auth/candidate/fsp/assessment/selection/employer/outreach/notification `-service`); gateway и frontend — не Gradle-модули. Приоритет за `docs/`. (дата: 2026-10-06)

### OQ-6 — Gradle version (S0): docs 8.10 vs wrapper 8.14
- Вопрос: `docs/` фиксировали Gradle 8.10, а wrapper использует Gradle 8.14.
- Влияет на: S0.
- Решение: сохранить 8.14 и обновить docs (решение пользователя). Обновлены ссылки на версию: `docs/stack.md §1` (сводная таблица) и §2, `TODO.md` S0 inputs, `docs/worklog.md` (S0 Phase 1), `docs/examples/package/PullHire-docs.md`/`.html`. (дата: 2026-10-06)

### OQ-7 — Временный канал auth→notification (S1)
- Вопрос: exchange/queue/routing-key события `user.registered` выбраны локально в S1; outbox и общая библиотека отсутствуют.
- Влияет на: S1, S2.
- Решение: временный канал до S2 — topic `pullhire.events`, routing key `user.registered`, queue `notification.user-registered`, конверт `docs/architecture.md §4.2`; заменяется библиотекой S2. (дата: 2026-10-06)

### OQ-8 — Риск потери `user.registered` при недоступном брокере
- Вопрос: контракта повторной доставки/ретраев нет; при недоступном RabbitMQ событие теряется.
- Влияет на: S1, S2.
- Решение: принятое ограничение до S2 (outbox/идемпотентность); в `docs/api.md §3.1` ретраев нет. (дата: 2026-10-06)

### OQ-9 — `docs/stack.md §2` не содержит `spring-boot-starter-mail` и `spring-boot-starter-flyway`
- Вопрос: S1 использует эти зависимости, но в `docs/stack.md §2` они отсутствуют.
- Влияет на: docs.
- Решение: docs на ходу не правились (правило трекера); внести в `docs/stack.md §2` при следующем согласованном обновлении docs. (дата: 2026-10-06)

### OQ-10 — `Idempotency-Key` на register отложен до S9
- Вопрос: `docs/api.md §1` декларирует `Idempotency-Key` для небезопасных POST; в S1 не реализован.
- Влияет на: S1, S9.
- Решение: дубль защищён `UNIQUE(email)` → 409 `conflict`; полная идемпотентность — зона S9. (дата: 2026-10-06)

### OQ-11 — TTL токенов не нормированы доками
- Вопрос: `docs/` не задают значения TTL; выбраны access PT15M / refresh P30D / email-токен 24h.
- Влияет на: S1.
- Решение: значения вынесены в env (`JWT_ACCESS_TTL` / `JWT_REFRESH_TTL`), email-токен 24h; уточнить в docs при следующем апдейте. (дата: 2026-10-06)

### OQ-12 — Выбор BCrypt внутри пары `architecture §6.1`
- Вопрос: `docs/architecture.md §6.1` / `docs/stack.md §2` допускают Argon2id или BCrypt; выбор не нормирован.
- Влияет на: S1.
- Решение: выбран BCrypt (`spring-security-crypto`); Argon2id добавил бы Bouncy Castle вне зафиксированного стека. (дата: 2026-10-06)

### OQ-13 — Сырой `verification_token` идёт через RabbitMQ (S1)
- Вопрос: raw одноразовый `verification_token` передаётся в payload события `user.registered` через RabbitMQ; auth письмо не рендерит, ссылку строит notification.
- Влияет на: S1, S2.
- Решение: docs это не запрещают; пересмотреть в контрактной работе S2 (альтернативы: auth рендерит ссылку сам либо внутренний fetch токена). (дата: 2026-10-06)

## Сводная таблица покрытия docs → слайс

| Документ (docs/) | § | Покрывается слайсом(ами) |
|---|---|---|
| architecture.md (сервисы §3.1, TOPOLOGY A, события/outbox) | §1–§8 | S0, S2, S1–S8 |
| data_model.md (8 схем; §12 видимость, §13 конвенции) | §1–§13 | S1, S3, S4, S5, S6, S7, S8; §12–§13 — S9 |
| api.md (соглашения + endpoints) | §1–§2 | S0, S1–S8, S10, S11 |
| testing_model.md | §1–§9 | S5 (реализация), S13 (обоснование) |
| validation.md | §1–§7 | S13; S5 (валидируемая механика) |
| selection.md | §1–§7 | S7, S8, S13 |
| fsp_integration.md | §1–§7 | S4, S13 |
| periodic_tasks.md [opt] | §1–§9 | S14, S15 |
| deployment.md | §1–§6 | S0, S12, S13 |
| requirements/acceptance.md | полностью | S12, S13 |
| requirements/roles.md | полностью | S1, S10, S11 |
| requirements/profiles.md | полностью | S3, S6, S10, S11 |
| requirements/overview.md | полностью | S0, S5, S7 |
| requirements/selection.md | полностью | S7, S8 |
| requirements/testing.md | полностью | S5 |
| requirements/nonfunctional.md | полностью | S0, S9 |
| requirements/evaluation.md | полностью | S13 |
| requirements/user-flow.md | полностью | S10, S11, S12 |
| user_flow.md | §1–§4 | S10, S11, S12 |
| stack.md | §1–§6 | S0, S10, S11 |
| assumptions.md | §2–§11 | S5, S7, S8, S14 |
| mvp_scope.md | §2–§4 | все; [opt] — S14–S17 |
| backlog/closed_questions.md | Q-1..Q-4, TD-1..TD-8 | S5, S7, S8, S9, S13 |

## Вне MVP

> Явно не входит в скоуп и не попал в `[opt]`. Пополняется вместо молчаливого расширения слайсов.

- Реальный API ФСП — не предоставлен (Q-4), только mock-адаптер со сменным интерфейсом (S4).
- Защита от фиктивных вакансий — прямо исключено `mvp_scope.md §4`.
- ATS-интеграция — исключено `mvp_scope.md §4` (концепция post-MVP).
- Свободные ответы в грейд-тесте — Decision D, только автопроверяемые форматы.
- Витрина чужих вакансий / парсинг площадок — исключено `mvp_scope.md §4`.
- Keycloak — только ориентир совместимости (Q-1), не развёртывается.
- MST — только fallback внутри S5, отдельного объёма нет.
- Prometheus/Grafana дашборды — «опционально»; экспозиция /metrics делается (S2/S9), дашборды не входят.

---

# Слайсы

## S0 — Bootstrap: репозиторий, Gradle multi-module, docker-compose каркас, CI

**Статус:** `[x]` · **Фаза:** `foundation`

- **id:** S0
- **depends_on:** —
- **goal:** Поднять скелет всего решения: пустые сервисы-заглушки всех 8 сервисов, инфраструктурный compose, конвенции и CI — чтобы каждый следующий слайс встраивался в работающий каркас.
- **inputs:** deployment.md (команды, порты, 12-factor), architecture.md §3.1 (состав сервисов, TOPOLOGY A), стек (Java 21 / Spring Boot 4.1.1 / Gradle 8.14 / Next.js 15).
- **outputs:** репозиторий; Gradle multi-module (8 сервисов + gateway + frontend-заглушка); docker-compose (postgres:16-alpine, rabbitmq:3.13-management-alpine, nginx:1.27-alpine с self-signed TLS, axllent/mailpit, eclipse-temurin:21, node:22-alpine); заглушки с /healthz; .env.example закоммичен, .env в .gitignore; GitHub Actions: линтер → build+test, diff OpenAPI, совместимость JSON Schema событий, сборка Docker, smoke compose; базовые конвенции (problem+json, X-Api-Version: 1, springdoc OpenAPI как источник истины, /docs, JSON-логи).
- **coverage:** deployment.md §CI/порты/12-factor; architecture.md §3.1 (каркас, TOPOLOGY A); api.md §соглашения (заготовки)

**Exit criteria:**
- [x] `./gradlew build` успешен из чистого клона
- [x] `docker compose up -d` поднимает PG/RabbitMQ/gateway/Mailpit и все заглушки, /healthz каждой = 200
- [x] `https://localhost` отвечает (self-signed), /docs доступен
- [x] все 5 стадий CI зелёные на PR
- [x] старт только из env-переменных

**Примечания / решение OQ:** —

---

## S1 — auth-service + notification-service: идентичность, JWT, согласия 152-ФЗ, почта

**Статус:** `[ ]` · **Фаза:** `foundation`

- **id:** S1
- **depends_on:** S0
- **goal:** Работающая вертикаль «регистрация → подтверждение email → вход»: идентичность, роли, согласия и почтовый канал (Mailpit). notification-service владеется этим слайсом; далее — только добавление шаблонов/триггеров.
- **inputs:** S0; api.md §auth + §notification; data_model.md §auth (users, email_tokens, refresh_tokens, consents append-only) + §notification (notification_log); 4 типа согласий: data_processing, profile_publication, fsp_link, task_participation; стек JWT RS256 (JJWT), Argon2id/BCrypt.
- **outputs:** миграции схем auth + notification; endpoints: POST /auth/register, /auth/verify-email, /auth/login, /auth/refresh, GET /auth/me, GET|PUT /auth/consents, GET /.well-known/jwks.json, GET /notifications; JWKS + проверка RS256 в сервисах/шлюзе; отправка писем через Mailpit; фиксация роли candidate/employer при регистрации.
- **coverage:** api.md §auth/§notification; data_model.md §auth/§notification; requirements/roles; 152-ФЗ (согласия)

**Exit criteria:**
- [ ] полный цикл register → письмо в Mailpit → verify-email → login → refresh → /auth/me
- [ ] роль неизменяема после регистрации
- [ ] 4 типа согласий append-only
- [ ] просроченный/чужой JWT → 401
- [ ] refresh-токены ротируются
- [ ] GET /notifications по JWT возвращает лог

**Примечания / решение OQ:** —

---

## S2 — Событийная шина, outbox, идемпотентность (механика), наблюдаемость

**Статус:** `[ ]` · **Фаза:** `foundation`

- **id:** S2
- **depends_on:** S0
- **goal:** Единая асинхронная механика для всех последующих доменов: конверт, контракты, outbox, идемпотентные консьюмеры + наблюдаемость per-service. Граница с S9: S2 = механика (outbox, дедупликация по event_id, ключи в БД); S9 = протокол (HTTP Idempotency-Key, rate-limit, аудит, footprint).
- **inputs:** S0, S1; architecture.md §асинхронно (topic exchange, at-least-once, конверт {event_id,type,version,occurred_at,producer,correlation_id,payload}); data_model.md §13 (idempotency source_event_id); нефункциональные §наблюдаемость.
- **outputs:** RabbitMQ topic exchange + библиотека publisher/consumer для всех сервисов; транзакционный outbox; каталог контрактов contracts/events/ (JSON Schema) + CI-проверка совместимости; идемпотентность консьюмеров по event_id; ретраи; /healthz, /readyz, /metrics (Prometheus-формат) на каждом сервисе; сквозной X-Request-Id → correlation_id в JSON-логах; фиксация имён событий проекций.
- **coverage:** architecture.md §асинхронно/§outbox; data_model.md §13; нефункциональные §наблюдаемость

**Exit criteria:**
- [ ] тестовый publish→consume: конверт валиден по схеме
- [ ] повторная доставка с тем же event_id не дублирует эффект
- [ ] остановка брокера не теряет outbox-событие
- [ ] несовместимое изменение JSON Schema ломает CI
- [ ] correlation_id виден в логах цепочки из ≥2 сервисов

**Примечания / решение OQ:** —

---

## S3 — candidate-service: профиль, резюме/PDF, приватность, карточка

**Статус:** `[ ]` · **Фаза:** `domain`

- **id:** S3
- **depends_on:** S1, S2
- **goal:** Кабинет кандидата на бэкенде: профиль/резюме с PDF-рендером, владелец настроек приватности, внутренняя карточка и события для поисковой проекции.
- **inputs:** S1, S2; api.md §candidate; data_model.md §candidate (profiles, resumes, privacy_settings, public_cards); приватность (владелец видимости — candidate-service; проекция без PII; лимит 413 на PDF); TD (files→candidate свёрнут сюда).
- **outputs:** миграции candidate; GET|PUT /candidate/profile, POST /candidate/resume, GET /candidate/profile.pdf, GET|PUT /candidate/privacy, GET /internal/candidates/{id}/card; public_cards; публикация событий изменений профиля/приватности в шину; API-механизм раскрытия контактов по событию candidate.contact_released (сквозной сценарий — S8).
- **coverage:** api.md §candidate; data_model.md §candidate; приватность/152-ФЗ §12; requirements/profiles (кандидат)

**Exit criteria:**
- [ ] профиль и резюме сохраняются, PDF рендерится
- [ ] превышение лимита PDF → 413 problem+json
- [ ] смена privacy отражается в public_cards и событии, проекция без PII
- [ ] /internal/* недоступен снаружи шлюза
- [ ] контактные данные закрыты по умолчанию

**Примечания / решение OQ:** детализировать exit criteria под-чекбоксами по экранам рискованно по размеру — при буксовании делить на «профиль+карточка» и «резюме+приватность».

---

## S4 — fsp-service: mock-адаптер ФСП, линк ID, кэш достижений

**Статус:** `[ ]` · **Фаза:** `domain`

- **id:** S4
- **depends_on:** S1, S2, S3
- **goal:** Связывание ФСП ID и кэш достижений на mock-адаптере с корректной обработкой «нет истории».
- **inputs:** S1–S3; fsp_integration.md (плоская модель, сменный адаптер, Keycloak-ориентир: sub, realm_access.roles, JWKS; mock + синтетический seed present/no_history; события fsp.linked, fsp.achievements_updated); data_model.md §fsp (fsp_links, achievements_cache).
- **outputs:** миграции fsp; POST|DELETE /candidate/fsp/link, GET /candidate/fsp/achievements, GET /internal/fsp/{candidateId}; mock-адаптер со сменным интерфейсом; seed-наборы present/no_history; события fsp.linked, fsp.achievements_updated.
- **coverage:** fsp_integration.md (полностью); data_model.md §fsp; api.md §fsp

**Exit criteria:**
- [ ] link/unlink работают через mock, повторный link идемпотентен
- [ ] кэш наполняется из seed обоих наборов
- [ ] «нет истории» → нейтральный результат: не штраф, не блокирует (автотест)
- [ ] оба события публикуются в конверте S2

**Примечания / решение OQ:** —

---

## S5 — assessment-service: опрос, грейд, адаптивный IRT-lite тест, категория, cooldown

**Статус:** `[ ]` · **Фаза:** `domain`

- **id:** S5
- **depends_on:** S1, S2, S3
- **goal:** Полный обязательный путь присвоения категории: опрос → declared_grade → адаптивный тест → θ+SE → cut-score → assessment.category_assigned через outbox.
- **inputs:** S1–S3; testing_model.md (1PL: p=1/(1+exp(−(θ−b))), байесовский шаг, стоп по SE с min/max, MST-fallback, якоря+семейства шаблонов, изоморфы, form_seed/variant_seed, exposure caps, эквайтинг, анти-утечка, прокторинг-флаги без auto-fail, needs_review, грейд не понижается, cooldown=1 мес [Q-3]); Decision D; TD-1 (republish category_assigned), TD-2 (обязательный scope), TD-3 (поля опроса/seed), TD-5 (exposure caps), TD-6; data_model.md §assessment (item_bank, test_sessions, attempts, grade_history, cooldowns).
- **outputs:** миграции assessment; POST /assessment/survey, /assessment/grade-choice, /assessment/sessions/{id}/start, /assessment/sessions/{id}/answers, GET /assessment/history, /assessment/grade-eligibility; генератор/сид item_bank; движок сессий с θ/SE; outbox-публикация category_assigned с republish (TD-1).
- **coverage:** testing_model.md (реализация; обоснование — S13); data_model.md §assessment; api.md §assessment

**Exit criteria:**
- [ ] сквозной тест survey → grade-choice → сессия → категория → событие в outbox
- [ ] сессия останавливается по SE с соблюдением min/max
- [ ] форма детерминирована form_seed/variant_seed, изоморфы взаимозаменяемы, exposure caps соблюдены
- [ ] повторный тест в cooldown → отказ (1 мес), grade_history append-only, понижения грейда нет
- [ ] needs_review на пересечении границы
- [ ] числовые параметры (cut-scores, порог SE, min/max) заданы конфигом — значения по OQ-1

**Примечания / решение OQ:** числовые параметры — OQ-1.

---

## S6 — employer-service: компания и потребности

**Статус:** `[ ]` · **Фаза:** `domain`

- **id:** S6
- **depends_on:** S1, S2
- **goal:** Кабинет работодателя на бэкенде: компания и потребности (needs) с жизненным циклом.
- **inputs:** S1, S2; api.md §employer; data_model.md §employer (companies, needs).
- **outputs:** миграции employer; GET|PUT /employer/company, GET|POST /employer/needs, GET|PUT|DELETE /employer/needs/{id}.
- **coverage:** api.md §employer; data_model.md §employer; requirements/profiles (работодатель)

**Exit criteria:**
- [ ] CRUD компании и needs владельцем
- [ ] доступ к чужой компании/needs → 403
- [ ] удаление needs корректно влияет на связанные сущности без cross-schema FK

**Примечания / решение OQ:** —

---

## S7 — selection-service: таксономия, категории, ранжирование, подборка с объяснимостью

**Статус:** `[ ]` · **Фаза:** `domain`

- **id:** S7
- **depends_on:** S2, S3, S4, S5
- **goal:** Поисковый контур: потребление category_assigned и событий профиля/ФСП → проекции → подборка с объяснимостью и фильтры.
- **inputs:** S2–S5; selection.md (категория = специализация × грейд; невидимость до category_assigned; ранжирование по θ + достижения ФСП, резюме/стек — только фильтр, freshness не влияет; объяснимость включения/позиции); data_model.md §selection (taxonomy, candidate_categories, candidate_cards, rank_signals); TD-1.
- **outputs:** миграции selection; сид таксономии; GET /catalog/specializations, /catalog/grades, /catalog/categories; GET /candidates?filters; POST /selections, GET /selections/{id}; консьюмеры category_assigned + событий профиля (S3) + fsp.achievements_updated (S4); rank_signals (θ + ФСП); блок объяснимости в ответе selections.
- **coverage:** selection.md (реализация; описание — S13); data_model.md §selection; api.md §selection/§catalog

**Exit criteria:**
- [ ] проекция candidate_cards сходится итогово по событиям (в т.ч. после republish TD-1), без PII
- [ ] кандидат без category_assigned не возвращается поиском
- [ ] POST /selections даёт подборку с объяснением включения и позиции
- [ ] резюме/стек работают только как фильтры, freshness на позицию не влияет (тест)
- [ ] веса формулы ранжирования конфигурируемы (значения — OQ-1)

**Примечания / решение OQ:** веса ранжирования — OQ-1.

---

## S8 — outreach-service: LHH-приглашения, жизненный цикл, раскрытие контактов

**Статус:** `[ ]` · **Фаза:** `domain`

- **id:** S8
- **depends_on:** S1, S2, S3, S6, S7
- **goal:** Приглашения конкретному кандидату с обязательной зарплатой «от–до» и раскрытием контактов только после принятия.
- **inputs:** S1–S3, S6, S7; LHH-механика (инициатива у работодателя); selection.md §приглашение (описание, зарплата «от–до» обязательна, компания, способ связи, employer_contact snapshot [TD-4], вакансия не обязательна); data_model.md §outreach (invitations, invitation_events append-only); инварианты (контакты закрыты до принятия/отклика); события жизненного цикла приглашения через outbox.
- **outputs:** миграции outreach; GET|POST /invitations, POST /invitations/{id}/mark-viewed|accept|decline; invitation_events; событие-триггер раскрытия контактов через candidate.contact_released; уведомления кандидату через notification-service; employer_contact snapshot (порядок наполнения — OQ-3).
- **coverage:** api.md §outreach; data_model.md §outreach; selection.md §приглашение; requirements/roles

**Exit criteria:**
- [ ] приглашение без зарплаты «от–до» → 400
- [ ] mark-viewed/accept/decline фиксируются в append-only invitation_events
- [ ] контакты раскрываются только при accept (сквозной тест с candidate-service)
- [ ] кандидат без категории не приглашаем
- [ ] письмо-уведомление уходит, дубликат при ретрае не создаётся

**Примечания / решение OQ:** порядок наполнения snapshot — OQ-3.

---

## S9 — Сквозные NFR: протокольный Idempotency-Key, rate-limit, аудит PII, footprint

**Статус:** `[ ]` · **Фаза:** `cross-cutting`

- **id:** S9
- **depends_on:** S3, S4, S5, S6, S7, S8
- **goal:** Реализовать и проверить кросс-сервисные NFR, не принадлежащие одному домену: принудительный Idempotency-Key, rate-limit, аудит PII/конвенций, сквозная трассировка, footprint. Граница с S2: S2 = механика событий, S9 = протокол и сквозная политика.
- **inputs:** S3–S8; api.md §соглашения (Idempotency-Key, problem+json); лимиты (rate-limit на шлюзе, mem_limit, footprint ≈2.0–2.2 ГБ); data_model.md §12–13; нефункциональные §безопасность/152-ФЗ.
- **outputs:** gateway rate-limit (429); middleware Idempotency-Key для мутирующих endpoints (selections, invitations, [ext] applications) с хранением результата; аудит-скрипты: отсутствие PII в поисковой проекции, конвенции §13 по всем 8 схемам; сквозной тест X-Request-Id→correlation_id; замер footprint с mem_limit.
- **coverage:** api.md §соглашения; data_model.md §12–13; нефункциональные §лимиты/§безопасность/§наблюдаемость (сквозная часть)

**Exit criteria:**
- [ ] повтор POST /selections и /invitations с тем же Idempotency-Key не создаёт дубликат
- [ ] превышение rate-limit → 429
- [ ] аудит PII/§13 зелёный
- [ ] суммарный RAM стека ≤ 2.2 ГБ при mem_limit
- [ ] correlation_id присутствует во всех сервисах цепочки демо-сценария

**Примечания / решение OQ:** —

---

## S10 — Frontend: кабинет кандидата (SPA)

**Статус:** `[ ]` · **Фаза:** `ui`

- **id:** S10
- **depends_on:** S1, S3, S4, S5, S8
- **goal:** Весь кандидатский user flow в Next.js 15 SPA: от регистрации до раскрытия контактов.
- **inputs:** S1, S3, S4, S5, S8; TS-клиенты из OpenAPI; user flow кандидата (регистрация → согласия → профиль/резюме → ФСП ID → опрос → выбор грейда → тест → карточка → приглашение → принятие → контакты); стек Frontend.
- **outputs:** SPA на :3000; экраны: регистрация+согласия, профиль/резюме/PDF-загрузка, privacy, ФСП-линк, опрос, выбор грейда, раннер тест-сессии, карточка категории (после category_assigned), входящие приглашения, раскрытые контакты, уведомления; сгенерированные TS-клиенты из OpenAPI.
- **coverage:** api.md §auth/§candidate/§fsp/§assessment/§outreach/§notification; requirements/roles; 152-ФЗ (UI согласий)

**Exit criteria:**
- [ ] кандидатский фрагмент демо-сценария проходится в браузере без API-инструментов
- [ ] карточка появляется строго после category_assigned
- [ ] контакты видны только после принятия
- [ ] TS-клиенты регенерируются из OpenAPI в CI
- [ ] детализировать под-чекбоксами по экранам

**Примечания / решение OQ:** слайс на верхней границе размера — обязательно детализировать exit criteria под-чекбоксами по экранам.

---

## S11 — Frontend: кабинет работодателя (SPA)

**Статус:** `[ ]` · **Фаза:** `ui`

- **id:** S11
- **depends_on:** S1, S6, S7, S8
- **goal:** Работодательский user flow: компания → потребность → подборка с объяснимостью → поиск → LHH-приглашение.
- **inputs:** S1, S6, S7, S8; user flow работодателя; стек Frontend.
- **outputs:** экраны: компания, CRUD потребностей, каталог (специализации/грейды/категории), поиск кандидатов с фильтрами, POST /selections с отображением объяснимости включения/позиции, создание приглашения (описание, зарплата «от–до», способ связи).
- **coverage:** api.md §employer/§selection/§catalog/§outreach; requirements/roles; requirements/profiles (работодатель)

**Exit criteria:**
- [ ] работодательский фрагмент демо-сценария проходится в браузере
- [ ] объяснимость подборки отображается
- [ ] форма приглашения валидирует обязательность «от–до»
- [ ] кандидат без категории недоступен в поиске
- [ ] детализировать под-чекбоксами по экранам

**Примечания / решение OQ:** слайс на верхней границе размера — детализировать exit criteria под-чекбоксами по экранам.

---

## S12 — Seed, развёртывание, сквозное демо (7 шагов)

**Статус:** `[ ]` · **Фаза:** `acceptance`

- **id:** S12
- **depends_on:** S10, S11
- **goal:** Воспроизводимое развёртывание своими силами и наполнение демо-данными для 7-шагового сценария.
- **inputs:** S10, S11; deployment.md (./gradlew build, docker compose build/up, docker compose run --rm seed; порты); сид-данные из S4–S7 (S12 владеет только CLI-оркестрацией); демо-сценарий 7 шагов.
- **outputs:** команда `docker compose run --rm seed` (таксономия, item_bank, демо-пользователи/компании/needs, ФСП-наборы present/no_history); финальный compose-профиль; smoke-проверка 7 шагов; README с командами запуска.
- **coverage:** deployment.md (полностью); requirements/acceptance §развёртывание/сквозная демонстрация; mvp_scope.md §входит

**Exit criteria:**
- [ ] на чистой машине build → compose build → up -d → seed воспроизводима
- [ ] все 7 шагов демо проходят на локальном стеке
- [ ] порты соответствуют deployment.md (SPA :3000, gateway https://localhost, RabbitMQ :15672, Mailpit :8025)
- [ ] smoke compose зелёный в CI

**Примечания / решение OQ:** —

---

## S13 — Сопроводительная документация, презентация, видео-фолбэк

**Статус:** `[ ]` · **Фаза:** `acceptance`

- **id:** S13
- **depends_on:** S12
- **goal:** Полный обязательный документационный пакет и питч.
- **inputs:** S0–S12 (финальное состояние, применён TD-7); requirements/acceptance.md (состав сопроводительной документации); validation.md (процедура + иллюстративные результаты); TD-8.
- **outputs:** документы: архитектура; механика тестирования + обоснование; механика подбора; процедура валидации + результаты (иллюстративные: полоса 18/20, κ=0.74, θ ρ=0.87, P@5 0.72 vs 0.48, Recall@10 0.83 vs 0.61, + ограничения малых выборок/синтетики); схема ФСП; описание API; библиотеки с версиями; инструкция сборки/запуска; презентация pptx/pdf; запись видео-фолбэка; сборка doc/pdf артефакта (TD-8).
- **coverage:** requirements/acceptance.md (полностью); validation.md; testing_model.md §обоснование; selection.md §описание; fsp_integration.md §схема; requirements/evaluation.md §5–7

**Exit criteria:**
- [ ] все 8 обязательных документов присутствуют и собраны в doc/pdf
- [ ] презентация pptx/pdf готова
- [ ] видео-фолбэк демонстрирует сквозной сценарий
- [ ] ссылка на doc/pdf включена в репозиторий
- [ ] значения валидации подставлены как иллюстративные с явной пометкой (OQ-1/размеры набора не определены)

**Примечания / решение OQ:** иллюстративные значения валидации — OQ-1; ручная сверка PDF/PPTX ТЗ — OQ-4.

---

## S14 — [opt] Регулярные задания (periodic tasks → assessment [ext])

**Статус:** `[ ]` · **Фаза:** `optional` · **opt** — вне MVP-скоупа

- **id:** S14
- **depends_on:** S5, S10
- **goal:** Минимальный срез механики регулярных заданий с жёстким правилом: никогда не меняют грейд/категорию/grade_history — только ProfileFreshness.
- **inputs:** S5, S10; periodic_tasks.md (каденс n=7, дедлайн d=n, глобальные синхронные окна, ежедневный идемпотентный планировщик, промпты {add|update|keep}, TaskVersion immutable, адресная выдача запрещена, подписка opt-in OFF, scope по специализации обязателен, грейд [ext], UQ(candidate_id,cycle_date), freshness display-only, минимальный срез B1–B4, C1–C2, D1–D4, E1–E4, F1/F2); hard rule из дока.
- **outputs:** таблицы assessment: TaskCycleConfig, EmployerTaskPrompt, Task, TaskVersion, TaskAssignment, TaskSubmission, TaskInterestSubscription, ProfileFreshness; планировщик; согласия fsp_link/task_participation активируются в сценарии; display-only freshness.
- **coverage:** periodic_tasks.md (полностью, [opt])

**Exit criteria:**
- [ ] автотест: никакой путь не меняет грейд/категорию/grade_history
- [ ] повторный запуск планировщика за один цикл не дублирует выдачу (UQ)
- [ ] TaskVersion неизменяема
- [ ] подписка по умолчанию OFF, без scope по специализации выдача невозможна
- [ ] freshness отображается, но не влияет на ранжирование
- [ ] реализованы пункты минимального среза B1–B4, C1–C2, D1–D4, E1–E4, F1/F2

**Примечания / решение OQ:** путь чтения freshness — OQ-2.

---

## S15 — [opt] Вакансии и отклики (outreach [ext])

**Статус:** `[ ]` · **Фаза:** `optional` · **opt** — вне MVP-скоупа

- **id:** S15
- **depends_on:** S8, S11
- **goal:** Расширение outreach: вакансии и отклики как альтернативный путь раскрытия контактов.
- **inputs:** S8, S11 (+S14 для retire при закрытии вакансии); api.md §outreach [ext]; data_model.md §outreach [ext] (vacancies, applications); mvp_scope [opt].
- **outputs:** миграции outreach: vacancies, applications; GET /vacancies, POST /vacancies/{id}/applications; раскрытие контактов при отклике (та же механика contact_released); событие закрытия вакансии → retire задач (если S14 выполнен).
- **coverage:** api.md §outreach [ext]; data_model.md §outreach [ext]; periodic_tasks.md §retire (интеграция)

**Exit criteria:**
- [ ] отклик раскрывает контакты по тем же инвариантам, что accept
- [ ] приложение вакансии не обязательно для приглашения (базовая механика не ломается)
- [ ] при выполненном S14 закрытие вакансии уводит связанные задачи в retire

**Примечания / решение OQ:** retire-интеграция зависит от выполнения S14.

---

## S16 — [opt] Мобильная адаптивность

**Статус:** `[ ]` · **Фаза:** `optional` · **opt** — вне MVP-скоупа

- **id:** S16
- **depends_on:** S10, S11
- **goal:** Пригодность обоих кабинетов и тест-раннера на мобильных ширинах.
- **inputs:** S10, S11; mvp_scope [opt] (мобильная адаптивность); стек Tailwind 3.4.
- **outputs:** адаптивные layout ключевых экранов обоих кабинетов; адаптация тест-раннера.
- **coverage:** mvp_scope.md §[opt]; requirements/evaluation.md §3 (удобство)

**Exit criteria:**
- [ ] ключевые экраны без горизонтального скролла на мобильных ширинах
- [ ] тест-сессия проходится на телефоне
- [ ] критический путь демо проходит на мобильной вьюпорте

**Примечания / решение OQ:** —

---

## S17 — [opt] ML/NLP-парсинг резюме

**Статус:** `[ ]` · **Фаза:** `optional` · **opt** — вне MVP-скоупа

- **id:** S17
- **depends_on:** S3, S7
- **goal:** Извлечение структурированных данных из загруженных резюме для усиления фильтров подбора.
- **inputs:** S3, S7; mvp_scope [opt] (ML/NLP-парсинг); свёрнутый модуль files→candidate.
- **outputs:** пайплайн парсинга PDF резюме → структурные поля профиля; интеграция полей в фильтры GET /candidates (резюме/стек остаются только фильтром, не ранжированием).
- **coverage:** mvp_scope.md §[opt]

**Exit criteria:**
- [ ] загрузка резюме извлекает структурные поля
- [ ] поля доступны фильтрам подбора
- [ ] graceful degradation: при отсутствии/отказе парсера загрузка резюме и фильтры работают как в S3/S7

**Примечания / решение OQ:** —
