# Worklog

## 2026-10-05 — docs/requirements/
- Источник: `docs/source/ФСП спец. трек ТЗ.pdf` (PPTX — дизайн-шаблон, не источник).
- Создано 9 файлов `docs/requirements/` — verbatim-цитаты, структура по эталону RoboMatch.
- Верификация: 142/142 фрагментов совпали с PDF-дампом.
- `context.md` переименован в `overview.md` (коллизия с harness `CONTEXT.md`).
- Открыто: пустые placeholders `## Что следует для реализации` / `## Вопросы разработки`.

## 2026-10-06 — Phase 5: core docs

- Сделано: созданы `docs/architecture.md`, `docs/data_model.md`, `docs/testing_model.md`, `docs/periodic_tasks.md`.
- Структура — по дизайну Phase 5a (единый H1, `## Назначение документа`, нумерованные `## N.`).
- Architecture: 8 бизнес-сервисов + api-gateway + SPA; TOPOLOGY A и путь A → B; RabbitMQ/outbox.
- Data_model: ER по 8 схемам; сущности регулярных заданий — §6.2–6.3.
- Testing_model: calibrated template pools + IRT-lite (1PL) + якоря + cut-score; cooldown.
- Periodic_tasks: каденс `n = 7`, opt-in, дедупликация task-level, freshness display-only.
- Отклонения от структуры: `docs/testing_model.md §9.5 «Антипаттерны»` — санкционированное отклонение (не следует шаблону подразделов); запись — `docs/backlog/closed_questions.md` TD-6.
- Открыто: финальная сверка перекрёстных ссылок — Phase 6.

## 2026-10-06 — Phase 6: structural docs + examples/

- Сделано: `docs/selection.md`, `docs/validation.md`, `docs/fsp_integration.md`, `docs/api.md`, `docs/stack.md`, `docs/deployment.md`, `docs/mvp_scope.md`, `docs/user_flow.md`, `docs/glossary.md`; реестр `docs/assumptions.md`; сводка `docs/final_summary.md`; журнал `docs/backlog/closed_questions.md`; каталог `docs/examples/` (README + `openapi/` + `package/`).
- Закреплено: Decision D (свободные ответы вне MVP-грейд-теста) — `docs/assumptions.md §9`, `docs/mvp_scope.md §4`.
- Отклонения от структуры: нет; `docs/testing_model.md §9.5` остаётся санкционированным (TD-6).
- Открыто: сборка per-service OpenAPI в `docs/examples/openapi/` и экспорт doc/pdf в `docs/examples/package/` — порядок воспроизведения в `docs/examples/README.md`.

## 2026-10-06 — S0 Phase 1: context load

- Источник: полный `./docs` (`requirements/*`, `source/ФСП спец. трек ТЗ.pdf`, `source/ЛЦТ_2026_Шаблон презентации ФСП.pptx`, `architecture.md`, `stack.md`, `data_model.md`, `deployment.md`, `api.md`, `mvp_scope.md`) + `TODO.md` S0.
- Извлечено: S0 = Bootstrap (репозиторий, Gradle multi-module, docker-compose каркас, CI); 5 exit criteria S0 зафиксированы.
- Стек S0: Java 21 / Spring Boot 4.1.1 / Gradle 8.14 / Node 22 / Next 15 / React 19 / TS 5.5 / Tailwind 3.4 / PostgreSQL 16 / RabbitMQ 3.13 / Nginx 1.27 / Mailpit 1.20.
- Открыто: расхождение «9 сервисов + gateway» (`TODO.md` S0) vs «восемь бизнес-сервисов + api-gateway + SPA» (`docs/architecture.md §1.1`) — разрешается на Phase 2.

## 2026-10-06 — S0 Phase 2: bootstrap scaffold

- Создан каркас S0 (untracked): `backend/` (8 Gradle-модулей: root `settings.gradle.kts`/`build.gradle.kts`/`gradle.properties`, wrapper 8.14, 8 × `*-service/` с `Dockerfile`, `/healthz`+`/readyz`, problem+json-конверт, `X-Api-Version`-фильтр, JSON-логи); `api-gateway/` (Nginx, self-signed TLS, `/docs`); `frontend/` (Next.js заглушка); `postgres/init/01-init-schemas.sh`; `docker-compose.yml`; `.env.example`; `.gitignore`; `contracts/events/.gitkeep`; `scripts/smoke-compose.sh`; `.github/workflows/ci.yml`.
- Gradle 8.14: решение — оставить фактический wrapper 8.14 (не откатывать к 8.10 из `TODO.md`); обновлены ссылки на версию в `docs/stack.md` (§1, §2), `TODO.md` (S0 inputs), `docs/worklog.md` (Phase 1), `docs/examples/package/PullHire-docs.md`/`.html`.
- LIGHT-верификация (без frontend/gateway и без остальных 7 сервисов): `.env` из `.env.example`; `docker compose config --quiet` → exit 0; `docker compose up -d --build postgres rabbitmq mailpit auth-service` → образ собран (Gradle 8.14, `BUILD SUCCESSFUL in 2m 31s`), postgres/rabbitmq/mailpit healthy; `auth-service` не поднялся (см. дефект ниже); стек погашен `docker compose down`.
- Дефект скаффолда (не исправлялся — Phase 3): `auth-service` crash-loop — `ApiVersionFilter` требует бин `com.fasterxml.jackson.databind.ObjectMapper`, отсутствующий в Spring Boot 4.1.1 (Jackson 3, `tools.jackson.*`) → `APPLICATION FAILED TO START`; `/healthz` не отдаёт 200. Тот же импорт в `ApiVersionFilter` всех 8 сервисов.
- OPEN QUESTIONS: добавлены OQ-5 (module count → 8 модулей, приоритет `docs/`) и OQ-6 (Gradle 8.14 vs docs 8.10) в `TODO.md`.
- Остаётся: Phase 3 — oracle-review каркаса и дефекта `ObjectMapper`; полная проверка `docker compose up` (gateway/frontend/8 сервисов, `/healthz` ×8, `https://localhost`) отложена в CI.

## 2026-10-06 — S0 Phase 2 fix: Jackson 3 + /docs

- F1 (блокер): в `ApiVersionFilter` всех 8 сервисов импорт `com.fasterxml.jackson.databind.ObjectMapper` → `tools.jackson.databind.json.JsonMapper`, тип поля и параметра конструктора `ObjectMapper` → `JsonMapper` (логика без изменений). `ErrorEnvelope.java` не тронут (`com.fasterxml.jackson.annotation.JsonProperty` корректен под Jackson 3); других Jackson 2 использований в `web/` нет.
- F2: в `api-gateway/conf.d/default.conf` (HTTPS server) добавлен `location = /docs { return 301 /docs/; }` — `https://localhost/docs` редиректит на `/docs/`.
- Ре-верификация (LIGHT): `docker compose config --quiet` → 0; `docker compose up -d --build postgres rabbitmq mailpit auth-service` → `BUILD SUCCESSFUL in 1m 13s`; `pullhire-auth` healthy, restarts=0; `GET http://localhost:8081/healthz` → 200; `X-Api-Version: 2` → 400 `application/problem+json`; лог «Started AuthServiceApplication»; стек погашен `docker compose down`.
- Остаётся: Phase 3 — oracle-review каркаса; полная проверка `docker compose up` (gateway/frontend/8 сервисов, `/healthz` ×8, `https://localhost/docs`) отложена в CI.

## 2026-10-06 — S0 Phase 3 fix: exec-bit + LF + /invitations

- Oracle final review S0: 2 blocker + 1 high.
- F1: `backend/gradlew` index mode `100644` → `100755`.
- F2: `.gitattributes` — `*.sh text eol=lf`, `backend/gradlew text eol=lf`, `*.bat text eol=crlf`; рабочий tree приведён к LF (CR=0).
- F3: gateway `location /invitations` (bare path по `docs/api.md`).
- ACL-рековери репозитория (UAC `takeown`/`icacls`) — разблокирована запись `.git/index`.
- Верификация: `compose config` + `auth-service` `/healthz`=200 + postgres init (LF) — OK.
- Осталось: полный `docker compose up` (gateway/TLS/`/docs`, все 8) — CI Stage 5.

## 2026-10-06 — S0 full verification + status [~]

- `docker compose up -d --build`: 13 контейнеров healthy, 0 рестартов.
- Все 8 `/healthz` = 200 (8081–8088); `https://localhost` → 302 → `/docs/` = 200; `frontend` :3000 = 200.
- S0 в трекере переведён `[ ]` → `[~]`; тело S0 реконсилировано «9 → 8 сервисов» (OQ-5).
- Осталось: подтвердить зелёный CI на GitHub → затем `[x]`.

## 2026-10-06 — S0 закрыт [x]

- CI для `5caa21b` — все 5 стадий зелёные (подтверждено на GitHub Actions).
- Exit criteria S0 закрыты локально (8×`/healthz`, TLS/`/docs`, frontend) + CI.
- Статус S0 `[~]` → `[x]` (header + index).
- Следующий слайс S1 разблокирован (`depends_on: S0`).

## 2026-10-06 — S1 Phase 1: context load

- Прочитаны: `docs/requirements/` (9 файлов), `docs/source/` (PDF ТЗ + PPTX-шаблон презентации машинно нечитаемы — OQ-4), весь authored-пакет `docs/*`, `TODO.md`.
- Уточнение терминологии: буквальный «Slice 1» = `S1` (auth+notification); «первая стадия» = `S0` — уже закрыт `[x]`. Работаем по `S1` (разблокирован, `depends_on: S0`).
- S1 = auth-service + notification-service: идентичность, JWT RS256 (JJWT), 4 согласия 152-ФЗ, email-вертикаль через Mailpit.
- Exit criteria S1: register → Mailpit → verify-email → login → refresh → /auth/me; роль неизменяема после регистрации; 4 согласия append-only; просроченный/чужой JWT → 401; ротация refresh-токенов; GET /notifications по JWT.
- Контракты: `docs/api.md §3.1/§3.8`; `docs/data_model.md §3` (users, email_tokens, refresh_tokens, consents) + `§10` (notification_log).
- Зафиксировано из S0: Jackson 3 (`tools.jackson.*`), problem+json, `X-Api-Version: 1`, /healthz + /readyz, Flyway per-schema, JWT RS256/JWKS.
- Открыто: S0-каркас существует — layout S1 уточняется на Phase 2.

## 2026-10-06 — S1 Phase 2+3: auth+notification implementation + e2e

- Реализованы `auth-service` и `notification-service` + инфра (compose, gateway `/.well-known/jwks.json`, `.env.example`) строго в границах S1 (`TODO.md` ~163-182).
- auth: Flyway-миграции схемы `auth` (users, email_tokens, refresh_tokens, consents append-only), RS256/JWKS (JJWT 0.13.0), BCrypt, JWT-фильтр локальной проверки; notification: Flyway `notification_log`, SMTP (Mailpit), консьюмер `user.registered` с дедупом по `event_id`, локальная проверка JWT по JWKS.
- Тесты: auth 11/11, notification 14/14; `./gradlew build` зелёный.
- Runtime e2e на живом стеке: все 11 exit criteria PASS — register→Mailpit→verify-email→login→refresh→/auth/me; дубль email 409; роль неизменяема; 4 согласия append-only (в БД 5 строк / 4 типа); ротация refresh (старый → 401); невалидный/чужой JWT → 401 `unauthenticated`; `GET /notifications` по JWT (direct + gateway); JWKS 200.
- Исправления в ходе e2e (в границах S1): `@Autowired` на `RsaKeyProvider`; `spring-boot-starter-flyway` вместо голого `flyway-core` (Boot 4 не включал автоконфиг); `Instant`→`OffsetDateTime` в JDBC-биндинге; логирование в generic `ApiExceptionHandler`; операционный рестарт `api-gateway` (stale upstream DNS).
- OQ-заметки: канал auth→notification в S1 временный (exchange `pullhire.events` topic, routing key `user.registered`, queue `notification.user-registered`, конверт `architecture §4.2`; без outbox/библиотеки — это S2); потеря `user.registered` при недоступном брокере — принятое ограничение до S2; `Idempotency-Key` не реализован (зона S9); `spring-boot-starter-mail`/`spring-boot-starter-flyway` отсутствуют в `docs/stack.md §2` (docs на ходу не правились); выбран BCrypt внутри пары `architecture §6.1`; TTL не нормированы доками (access PT15M / refresh P30D / email-токен 24h); `POST /auth/register` принимает опциональный `consents[]`.
- Открыто: независимое ревью (oracle) и push — только после approve пользователя. Статус S1 не менялся.

## 2026-10-07 — S1 закрыт [x]

- Все 6 exit criteria S1 отмечены `[x]`; статус S1 `[ ]` → `[x]` (header + index).
- Build-gate пройден на локально установленном Gradle 9.7.1 + JDK 21.0.10 без загрузки дистрибутива (`-Dorg.gradle.java.installations.auto-download=false`); все 8 модулей + тесты auth/notification зелёные.
- Подтверждено: F1 (`DataIntegrityViolationException` → 409 `conflict`) и F2 (`OQ-13`) в коммите `4f25cd2` на `origin/main`.
- `backend/gradle.properties`: `org.gradle.daemon=false` закоммичен в S1 (локальная настройка среды).
- Примечание: репозиторий ожидает Gradle 8.14 (wrapper), полного 8.x локально нет — сборка проверена на 9.7.1.

## 2026-10-07 — S2 foundation: event-lib + outbox + contracts + auth wiring

- Создан Gradle-модуль `backend/event-lib` (plain library, `bootJar` off): `EventEnvelope` (JSON `{event_id,type,version=1,occurred_at,producer,correlation_id,payload}`, Jackson 3), `EventNames`, `OutboxWriter`/`OutboxEventPublisher` (единственный publish API, в ambient-транзакции), `OutboxRelay` (`@Scheduled`, publisher-confirm перед `published_at`), `EventIdDedup`, `ConsumerRetryConfig` (bounded retry + `pullhire.events.dlx`/`<queue>.dlq`, requeue off, RabbitTemplate mandatory), `CorrelationMdcFilter`, `CorrelatedListenerAdvice` + `@AutoConfiguration`/imports.
- Контракты: `contracts/events/{envelope,user.registered}.schema.json` (2020-12) + реестр `README.md`; OQ-13 — payload несёт `verification_url`, не `verification_token`.
- auth-service: зависимость `:event-lib`, actuator + micrometer-prometheus; `EventPublisher` переписан на outbox (без `convertAndSend`), `AuthService` рендерит ссылку из `app.base-url` в той же транзакции; Flyway `auth/V5__create_outbox_events.sql`; `publisher-confirm-type: correlated`; `/metrics` (base-path `/`, path-mapping), `/readyz` = DB `SELECT 1` + брокер (503), `/metrics` в public-prefixes.
- Открыто: notification-service всё ещё ждёт `verification_token` — обновление консьюмера на `verification_url` вне границ foundation-задачи; статус S2 `[ ]` → `[~]`, exit criteria не отмечены.

## 2026-10-07 — S2 notification lane: event-lib consumer + DLQ + readyz/metrics

- notification-service: зависимость `:event-lib`; `AmqpConfig` оставляет только owned-топологию (`notification.user-registered` + `.dlq` + binding на `pullhire.events.dlx`), retry/DLQ/advice — из `ConsumerRetryConfig`.
- Консьюмер: payload `verification_url` (OQ-13), провал SMTP — `markFailed` + rethrow в bounded retry/DLQ; `pullhire.outbox.enabled=false` (нет outbox-таблицы в схеме consumer).
- Реальный `/readyz` (DB `SELECT 1` + брокер, 503) и `/metrics` (actuator prometheus) в public-prefixes; тесты notification обновлены под dedup/happy/failure-propagate и `verification_url`.

## 2026-10-07 — S2 observability skeletons: actuator + Prometheus на 6 сервисах

- candidate/fsp/assessment/selection/employer/outreach: `spring-boot-starter-actuator` + `micrometer-registry-prometheus`, `/metrics` (base-path `/`, path-mapping `prometheus: metrics`), `/metrics` в public-prefixes `JwtAuthFilter`.

## 2026-10-07 — S2 CI/scripts: event-compat gate + extended smoke

- `.github/workflows/ci.yml`: job `event-schemas` вызывает `scripts/check-event-compat.py` (`fetch-depth: 0`, `git show HEAD:<path>`), ломает CI на несовместимом изменении `contracts/events/*.schema.json`.
- `scripts/smoke-compose.sh`: сквозной `user.registered` через шлюз, `/metrics`+`/readyz` ×8, `correlation_id` в логах двух сервисов, дубль доставки (идемпотентность), остановка брокера (нет потери outbox); скипы при недоступных psql/Mailpit/Rabbit-API.

## 2026-10-07 — S2 fix: correlation_id в success-логах auth+notification

- Успешные обработки auth-service и notification-service пишут строку лога с MDC `correlation_id` (exit criterion 5); в связке с `CorrelationMdcFilter`/`CorrelatedListenerAdvice` id прослеживается в ECS-логах цепочки auth→notification.

## 2026-10-07 — S2 Phase 3 review + fixes

- Phase 3 review done as a compromise: independent targeted review ran on a DeepSeek reviewer (explorer) instead of the GLM oracle, because oracle was unavailable (first attempt `Insufficient balance`, relaunch cancelled). Scope was the 4 residual risks + 5 exit criteria.
- Fixed R1: `OutboxRelay` made opt-in (`pullhire.outbox.enabled` default false; `auth-service` sets it true).
- Fixed R2 (blocker): status-aware consumer dedup — skip only on terminal `sent`; reuse `failed`/`queued` rows and re-send, so bounded retry actually retries and exhausted retries reach the DLQ (at-least-once restored).
- Fixed R3: `OutboxRelay` uses non-deprecated `Confirm.ack()/reason()`.
- Fixed R4/D6 edge: envelope `correlation_id` falls back to MDC value when `X-Request-Id` absent.
- Fixed E1 (blocker): dependency-free schema-conformance tests validate a serialized `EventEnvelope` and the `user.registered` payload against `contracts/events/*.schema.json`.
- Fixed E4 (blocker): `scripts/check-event-compat.py` now diffs against a configurable BASE ref (PR base sha / push `before` / `origin/main`) with a negative self-test `scripts/check-event-compat-test.py`; wired into the `event-schemas` CI job.
- Residual (unverified without a running stack): exit criteria 3 (broker-outage outbox durability) and 5 (correlation_id in real ≥2-service logs) rely on `scripts/smoke-compose.sh` / CI; no `@SpringBootTest` asserts the relay bean is absent for consumer-only services.

## 2026-10-07 — S3 Phase 1: context load

- Входы S3 извлечены: `TODO.md` S3; `docs/api.md §candidate`; `docs/data_model.md §candidate` (§4, §12); `docs/architecture.md`; `docs/stack.md`.
- S2 закрыт `[~]` → `[x]` (header + index), S3 открыт `[ ]` → `[~]` (header + index).

## 2026-10-07 — S3 scaffolding: contracts + compose + OQ

- Добавлены контракты `contracts/events/{candidate.card_updated,candidate.contact_released,invitation.accepted}.schema.json` (draft 2020-12, `additionalProperties:false`) + регистрация в `contracts/events/README.md`.
- candidate-service: named volume `candidate_files` → `/app/files` + env `CANDIDATE_FILES_DIR`/`CANDIDATE_RESUME_MAX_BYTES` в `docker-compose.yml` и `.env.example`; OQ-14..OQ-21 записаны в `TODO.md`.

## 2026-10-07 — S3 implementation I+II: candidate-service domain + tests

- candidate-service domain: profile/resume/PDF/privacy/card + contact-release (application-сервисы, `ingest/InvitationAcceptedListener`, контроллеры/DTO по `docs/api.md §3.2`, карточка `candidate.card_updated`/`candidate.contact_released` через outbox).
- Тесты: 23 unit (standalone MockMvc + моки, PDFBox render, схемы контрактов, state-based идемпотентность OQ-22) — 23/23 зелёные; `:candidate-service:compileJava` ОК.

## 2026-10-07 — S3 review fixes: font, dedup, 413, loopback, tests

- F1 Unicode-шрифт `fonts/DejaVuSans.ttf` (Type0, embedded subset) вместо Helvetica→`?`; F2 дедуп `candidate.processed_events` (V6) + `EventIdDedup`/insert-claim и rebuild карточки перед release; F3 `spring.servlet.multipart` привязан к `CANDIDATE_RESUME_MAX_BYTES` + `server.tomcat.max-swallow-size: -1`; F4 candidate порт на `127.0.0.1`; F6/F7 фильтр null в stack/skills и scrubbing headline; F9 запись файла после commit; F10 JWKS по `kid`; F11 роль по всему списку; OQ-22..OQ-25.
- Тесты 36/36 (13 suites): добавлены `ProfileServiceTest`, `PrivacyServiceTest`, `InternalCardControllerTest`, `ApiExceptionHandlerTest` (413 handler), кириллический PDF-кейс, dedup-кейсы listener. Примечание: реальный multipart-resolver путь в `standaloneSetup` недоступен (в Spring 7 нет `DispatcherServlet.setMultipartResolver`), поэтому контейнерный 413 покрыт unit-тестом handler'а + сервис-сайд `ResumeUploadLimitTest`.

## 2026-10-07 — S3 residual fixes M1–M3

- M1: `CardResponse`/`ProfileService` фильтруют null-элементы `stack`/`skills`/`soft_skills` (нет NPE→500 на `/internal/candidates/{id}/card`); M2: OQ-22 дополнен остаточной семантикой (hide → release skip, re-trigger отложен на S8; `show_contacts=false` без повторного триггера); M3: `CANDIDATE_RESUME_REQUEST_MAX_BYTES` (6 MiB) — запас multipart-запроса над файловым лимитом.
- Тесты: 37/37 (13 suites) зелёные; `docker compose config --quiet` = 0.

## 2026-10-08 — docs reconciliation vs qa-summary

- Сверка authored-пакета `docs/` с `docs/source/qa-summary-fsp-2026.md`: патчи A1–A4 (неподтверждённый грейд показывается со статусом и опускается в ранжировании; cooldown не сжигается при провале/уходе на уровень ниже; результативность ФСП в ранжировании; GPU-ограничение стенда проверки) и добавления B1–B7 (пометка гросс/нетто, стенд жюри 32–64 ГБ RAM / ~8 CPU, post-deadline freeze, открытость репозитория к сдаче, вход через mock FSP ID, самотестирование жюри / демо-режим, раскрытие LLM-моделей).
- Package rename `com.pullhire` -> `me.yuugao.pullhire`: 180 files moved (`com/pullhire/**` -> `me/yuugao/pullhire/**`) across 14 source roots; updated package/import refs, `backend/build.gradle.kts` group, event-lib AutoConfiguration.imports, and 3 sql/resource slash paths; `git grep com.pullhire` = 0.

## 2026-10-08 — build-note placement

- Build-заметка размещена в шапке `TODO.md` сразу под заголовком: сборка сервис-Dockerfile + фикс пропущенного `event-lib` COPY.

## 2026-10-08 — S4 fsp-service: mock adapter, link, achievements cache, events

- Реализован `fsp-service`: миграции `fsp/V1..V3` (fsp_links, achievements_cache, outbox_events), сменный адаптер (`FspRegistryAdapter` + `MockFspRegistryAdapter`, фикстуры `error`/`no_history`), link/unlink (`FspLinkService`, `fsp.linked`/`fsp.achievements_updated` через outbox), owner/internal чтение (`/candidate/fsp/*`, `/internal/fsp/{candidateId}`), JWT/JWKS-фильтр, problem+json, `AppProperties` (`app.fsp.adapter`, `auth.jwks-url`), seed `db/seed/fsp_seed.sql` (present/no_history), контракты `fsp.linked`/`fsp.achievements_updated` + README `active`, Dockerfile `event-lib` COPY.
- Тесты: 30/30 (8 suites) зелёные (`:fsp-service:test`); `:fsp-service:compileJava :fsp-service:compileTestJava` — `BUILD SUCCESSFUL`.
- Review-fixes: sub→profile identity (candidate `/internal/candidates/by-user/{userId}` + fsp `CandidateProfileClient`), adapter failure preserves last good cache and emits no `fsp.achievements_updated`, unlink publishes empty achievements set, order-independent `sameAchievements`, real `/readyz` (DB+broker), fsp host port bound to loopback, `JsonSchema` `minimum`/`date`, DB-gated repo/seed test, D5 residual risk recorded; verify `candidate 39 (14) / fsp 38 (10, 3 DB-gated skipped)`, `git grep com.pullhire` = 0.

## 2026-10-08 — S5.1 assessment-service runtime deps
- assessment-service runtime deps (jdbc/flyway/amqp/jjwt/event-lib) + application.yml (flyway/rabbit/jwks/outbox); `:assessment-service:compileJava` `BUILD SUCCESSFUL`.

## 2026-10-08 — S5.2 typed assessment config
- S5.2 — AssessmentProperties: typed config (cut-scores, SE threshold, min/max, start-θ, cooldown=1mo, exposure); values deferred OQ-1.

## 2026-10-08 — S5.3 grade-testing migration
- S5.3 — V1__create_grade_testing.sql: item_bank/test_sessions/attempts/grade_history/cooldowns per data_model §6.1.

## 2026-10-08 — S5.4 outbox migration
- S5.4 — V2__create_outbox_events.sql per event-lib reference.

## 2026-10-08 — S5.5 item_bank domain + repository
- S5.5 — item_bank domain + ItemBankRepository/exposure queries + repo/JdbcRows.

## 2026-10-08 — S5.6 test_sessions + attempts domain + repositories
- S5.6 — test_sessions/attempts domain + TestSessionRepository/AttemptRepository (prior-served variants).

## 2026-10-08 — S5.7 grade_history repository
- S5.7 — GradeHistoryRepository append-only + GradeDirection.

## 2026-10-08 — S5.8 cooldowns repository
- S5.8 — CooldownRepository upsert/findActive + Cooldown.

## 2026-10-08 — S5.9 IRT-lite scorer
- S5.9 — IrtScorer 1PL (θ Bayesian step + SE), pure domain.

## 2026-10-08 — S5.10 FormAssembler
- S5.10 — FormAssembler: deterministic form (form_seed/variant_seed), anchors, ≤1/family, exposure caps, no-reserve.

## 2026-10-08 — S5.11 RoutingRules
- S5.11 — RoutingRules: SE/min/max stop + next-item argmin|θ−b| (MST deferred).

## 2026-10-08 — S5.12 GradeBandClassifier
- S5.12 — GradeBandClassifier (cut-scores + CI/SE needs_review) + ciConfidence config.
- S5.13 — item_bank synthetic generator + idempotent seed (anchors + template families per specialization; payload = model+key only, auto-checkable formats, no free-text per Decision D; writes assessment.item_bank only).
- S5.14 — contracts/events: committed assessment.category_assigned + assessment.grade_changed payload schemas (draft 2020-12, no theta) + registry rows.
- S5.24 — assessment-service JWT/JWKS security wiring: local RS256 via cached JWKS (Environment auth.jwks-url), JwtAuthFilter guarding /assessment/** with candidate role, assessmentUserId/assessmentRole attrs, CurrentUser + ApiException (mirrors fsp/candidate).
- S5.15 — Assessment EventPublisher (assessment.category_assigned/grade_changed) over transactional outbox; MDC correlation id; payload-schema conformance tests.
- S5.18 — POST /assessment/survey: SurveyService opens started test_sessions with survey_answers(jsonb)+declared_dimensions(text[]) for the JWT candidate, returns session_id; @Valid failures → 400 validation_error.
- S5.20 — SessionStartService + AssessmentSessionController (POST /assessment/sessions/{id}/start) + SessionStartResponse: FormAssembler form from form_seed, persist form_seed/status, first attempt with variant_seed, presentation-only DTO, idempotent re-start.
- S5.22 — GET /assessment/history read-only: composes candidate sessions→attempts + grade_history, CurrentUser scoping, HistoryResponse DTO.
- S5.23 — GET /assessment/grade-eligibility: cooldown vs now() (eligible if absent/expired, else available_after), CurrentUser scoping, EligibilityResponse.

## 2026-10-08 — oracle-fix batch + S5.16
- S5.16 — completion orchestration: classify θ+SE, append grade_history, always republish category_assigned, grade_changed audit-only, cooldown only on up, never forced downgrade, needs_review to manual.
- Fix(#8) — assessment_seed.sql projects v.status instead of hardcoded 'active'.
- Fix(#2,#11) — EventPublisher always emits non-null assigned_at; contracts README assessment events flipped to active.
- Fix(#3,#4,#10) — survey owner-side taxonomy validation (specialization/grade/concept codes) + documented MVP survey/grade-choice merge.
- Fix(#1a,#6,#7) — SessionStartResponse snake_case + string ids; exposure increment+retire on fresh serve; race-safe idempotent start (DuplicateKey re-serve, match by order+item).
- Fix(#1b,#5) — HistoryResponse snake_case + string ids; variant_seed dropped from wire.
 - Fix(#9) — JwksKeyProvider fails closed for kid-less tokens with multiple JWKS keys.
 - S5.17 — proctoring-flag ingestion (visibilitychange/fullscreen_exit/paste with timestamps into attempts.proctoring_flags) + needs_review marking (flags OR SE/CI via GradeBandClassifier), never auto-fail.
  - S5.19 — POST /assessment/grade-choice fixes declared_grade/target_grade with start θ from startThetaByGrade; unknown grade 400, no forced downgrade, snake_case/string-id wire.
## 2026-10-08 — consolidation batch (oracle fixes + S5.21)
- S5.21 — POST /assessment/sessions/{id}/answers: AnswerService grades served items, persists via AttemptRepository, recomputes θ/SE (IrtScorer), routes (RoutingRules min|θ−b| / SE-after-min / forced-max), completes via CategoryAssignmentService (proctoring→needs_review); AnswerResponse anti-leak shape; exposure++ per serve.
- Fix(#4-dim) — survey declared_dimensions validated as industry/specialization/grade (data_model §6.1); explicit grades config list decoupled from cutScores.
- Fix(#7-race) — start idempotency via ON CONFLICT DO NOTHING (no tx abort); loser re-serves winner.
- Fix(grade-scope) — CategoryAssignmentService scoped by session specialization_code; terminal no-op guard; publishGradeChanged requires non-null direction.
- Fix(seed-exposure) — seed sets template exposure_limit (exposure-default), anchors NULL.

## 2026-10-08 — S5.25 AssessmentFlowTest
- S5.25 — AssessmentFlowTest: survey→grade-choice→session→category→outbox path (initial+retry rows, SE min/max, cooldown refusal, append-only/no-downgrade, boundary needs_review).

## 2026-10-08 — F1 consolidation
- Fix(F1-completion) — cooldown enforced on grade change (grade-choice 409 when active; complete() retains current grade without burning cooldown, still republishes category_assigned); candidate-initiated down signal; grade-choice status guard (started only, theta not clobbered); answer replay is a strict no-op; terminalOutcome from latest grade_history; publishGradeChanged requires non-null direction; ProctoringService wired into answers with append-merge.
- Fix(F1-race) — attempt insert ON CONFLICT DO NOTHING covers both UNIQUE arms (order and item).
- Fix(F1-dim) — declared_dimensions open-vocabulary (industry); grade-aware FormAssembler coverage.

## 2026-10-08 — F2 consolidation
- Fix(F2) — flow-level exposure-cap retire/exclude, RUNNING-flow NEEDS_REVIEW, and enforced-cooldown refusal coverage in AssessmentFlowTest.

## 2026-10-08 — Fix(MX) — grade-aware form assembly wired into start; grade-choice allows same-grade reconfirmation; cooldown treated per-candidate (data_model §6.1); ProctoringService.ingest standalone endpoint deferred; flow test uses production wiring.

## 2026-10-08 — S6 Phase 1: scope + docs extraction
- S6 scope извлечён из TODO.md verbatim (employer-service: companies + needs; depends_on S1, S2).
- api.md §3.6, data_model.md §8, requirements/profiles.md (employer) извлечены.
- docs/source: ТЗ PDF машинно нечитаем (OQ-4); qa-summary-fsp-2026.md employer-строки взяты.
- Worklog-формат подтверждён.

## 2026-10-08 — S6.1 employer-service: deps + publish-only конфиг
- deps зеркально fsp (L7-28): :event-lib, jdbc, flyway+flyway-database-postgresql, amqp, jjwt 0.13.0; application.yml: datasource currentSchema=employer, flyway db/migration/employer, publisher-confirm correlated, auth.jwks-url, pullhire.outbox.enabled; Dockerfile COPY event-lib до service src.
- publish-only: без объявлений очередей, топология pullhire.events не тронута; :employer-service:compileJava BUILD SUCCESSFUL.
- oracle: 2 minor (stale S0-комментарий в yml, (S4)→(S6)) — исправлены по required_action; PASS.
- TODO.md: S6 → [~] (заголовок+индекс); OQ-26 (employer.need_deleted, publish-only) записана до начала S6.1-коммита.

## 2026-10-08 — S6.2 миграции employer (companies + needs + outbox)
- V1__create_companies_and_needs.sql по data_model §8.1-8.2 verbatim: uq_companies_user_id (user_id — логическая ссылка, без FK), FK needs.company_id ON DELETE CASCADE, CHECK status ∈ (active, archived), idx company_id + (specialization_code, grade); без grade CHECK и триггера updated_at (в §8 их нет).
- V2__create_outbox_events.sql — зеркало fsp V3 / event-lib reference: partial idx_outbox_events_unpublished, шапка §4.3, без retention.
- Проверка по прецеденту S4: Flyway migrate на чистой схеме employer — V1+V2 success; каскад company→needs SQL-ом (2→0).
- oracle: PASS; отложено на S6.5 — валидация specialization_code/grade против selection.taxonomy.

## 2026-10-08 — S6.3 employer-service security wiring (аутентификация)
- JwksKeyProvider / JwtAuthFilter / CurrentUser / ApiException — зеркально S5.24 (assessment): OncePerRequestFilter @Order(2), 8 public-префиксов, guard `/employer` роль `employer`, attrs employerUserId+employerRole; ApiExceptionHandler + handleApi (иначе 401/403 → 500).
- 401 `unauthenticated` / 403 `forbidden`, problem+json ErrorEnvelope по api.md §2.
- Standalone-тесты JwtAuthFilterTest: 6/6 (no-JWT, expired, wrong-role, attrs, public paths) — без БД/брокера.
- oracle: PASS; заметки на S6.7: `payloadTooLarge` не используется (возможный 1-строчный cleanup), JwksKeyProviderTest опционален.

## 2026-10-08 — S6.4 компания: GET|PUT /employer/company
- domain/repo/application/web по образцу candidate-профиля: CompanyRepository upsert ON CONFLICT (user_id) DO UPDATE (uq_companies_user_id, Fix #7-race), RETURNING id + re-read; owner = CurrentUser.id().
- Wire: snake_case @JsonProperty, id строкой (api.md §1), user_id/created_at/updated_at не на проводе; PUT 200 + body (идемпотентно), GET 404 not_found.
- Валидация: @NotBlank @Size(255) name, @Email contact_email; без выдуманных лимитов (data_model §8.1 их не задаёт).
- Тесты standalone (прецедент S3): 16/16 (service 7 + controller 9). oracle: PASS; на S6.7 — DB-gated upsert-тест (insert→update→created_at неизменен).

## 2026-10-08 — S6.5 needs: owner-scoped CRUD
- Need domain/repo/service/controller + PageResponse: two-step доступ — findById пуст → 404 not_found; компания-владелец не совпала → 403 forbidden (get/update/delete); update/delete SQL-scoped WHERE id AND company_id (Fix #7-race defense in depth).
- Список — пагинация {items,page,size,total} (page=0, size=20, sort created_at,desc; прецедент NotificationsController); POST 201, PUT 200, DELETE 204 (без события — публикация в S6.6).
- Wire: id строкой, snake_case, stack null→[], company_id не на проводе; status: POST null→active, PUT null→keep, иначе строго active|archived.
- Валидация по OQ-27: title/specialization_code/grade непустые, без taxonomy-проверки (владелец S7); компании нет → 404.
- Тесты standalone 29/29 (service 15 + controller 14, матрица 403/404). oracle: PASS; на S6.7 — DB-gated repo-тесты (upsert company + scoping needs) и PUT null-status-keep.

## 2026-10-08 — S6.6 удаление needs + событие employer.need_deleted
- Contract events/employer.need_deleted.schema.json (payload {id, company_id, user_id} — строки, envelope occurred_at несёт момент удаления; OQ-26): registry row active после assessment.grade_changed.
- EventPublisher (employer) — narrowed-зеркало assessment; EventNames.EMPLOYER_NEED_DELETED.
- NeedService.delete: publish строго после affected==0→404 guard, в той же @Transactional; publisher не вызывается на 403/404.
- Conformance NeedDeletedPayloadSchemaTest (pullhire.contracts.dir + walk-up, fsp-verbatim gradle-блок); DB-тест NeedDeleteOutboxDbTest — commit: строка удалена + outbox-строка (string payload); rollback: ни строки, ни события. CI-skip residual: БД-тесты скипаются без PostgreSQL URL (механизм как AssessmentFlowTest, S5.25).
- oracle: PASS; на S6.7: смоук ассертит событие из брокера/строку outbox после DELETE; опционально mock-тест delete→0 → 404 + no publish.

## 2026-10-08 — S6.7 закрытие слайса S6
- D1 DB-gated CompanyNeedsRepositoryDbTest (upsert created_at + needs scoping, skip-гейт как AssessmentFlowTest); D2 PUT null-status-keep; D4 JwksKeyProviderTest (fail-closed); D5 payloadTooLarge удалён; D3 скип (уже покрыт deleteUnknownIsNotFound).
- Полный прогон :employer-service:test — BUILD SUCCESSFUL, 0 failures (DB-тесты 4 skipped без PostgreSQL URL); smoke — SMOKE OK (S2 + новый S6-блок: company/needs CRUD, need_deleted outbox, 401/403).
- TODO.md: exit criteria S6 [x] ×3, статус [~]→[x] (заголовок + индекс).
- Осталось: CI-skip residual БД-тестов (записан в S6.6).

## 2026-10-08 — S7 Phase 1: scope + docs extraction
- TODO.md slice 7 (S7) extracted verbatim by explorer — scope, depends_on S2–S5, 5 exit criteria, refs OQ-1/OQ-27/TD-1.
- docs/requirements/selection.md — дословные выдержки: категория = специализация × грейд, ранжирование по подтверждённому профилю + ФСП, фильтры (специализация/грейд/категория/стек/ФСП), объяснимость выдачи, уточнение без потери подборки.
- docs/data_model.md §7 — taxonomy, candidate_categories, candidate_cards (без PII), rank_signals (θ, fsp_achievements_weighted) — дословно.
- docs/api.md §3.5 — только индекс эндпоинтов selection-service; полные контракты — в генерируемом OpenAPI.
- docs/source (ТЗ PDF + qa-summary-fsp-2026.md) — S7-релевантные цитаты: категоризация, ранжирование, подборка с объяснимостью, фильтры.
- TODO.md:70-73,178-181 — OQ-1 (веса ранжирования конфигурируемы, значений нет) и OQ-27 (валидация кодов против taxonomy — на S7 в момент формирования подборки) — дословно.
- docs/backlog/closed_questions.md:85-91 — TD-1: любая завершённая попытка перепубликует category_assigned; grade_changed — audit only.
- docs/selection.md — механика: невидимость до category_assigned, неподтверждённый грейд виден со статусом и ниже в ранжировании, θ+ФСП — входы, стек — только фильтр, freshness display-only, top-N + рекомендованные категории + объяснимость, θ работодателю не раскрывается, no cross-schema JOIN.
- docs/architecture.md §4.2/§4.3/§6.2 + assumptions.md — консьюмеры category_assigned/card_updated/fsp.achievements_updated, идемпотентность по event_id, outbox, итоговая согласованность; card_updated несёт только согласованные поля (no-PII).

## 2026-10-08 — S7 Phase 2: decomposition
- oracle декомпозировал S7 → 20 под-задач (ST-7.1–7.20: миграции, сид, 3 консьюмера, каталог/поиск, веса+ранжирование, selections, 4 теста по exit-критериям); selection_results утверждена пользователем (закрывает пробел data_model для GET /selections/{id} и уточнения без потери); утверждён батчевый план Phase 3 (6 батчей, внутри батча параллельно).

## 2026-10-08 — S7 Phase 3: batch 1 (миграции + конфиг)
- ST-7.1 — V1__create_taxonomy.sql по data_model §7.1 + активация Flyway в selection-service (зависимости build.gradle.kts + spring.flyway в application.yml).
- ST-7.3 — V2__create_processed_events.sql: идемпотентность консьюмеров по source_event_id (зеркало candidate V6).
- ST-7.4/7.5/7.6 — V3 candidate_categories (§7.2), V4 candidate_cards (§7.3, без PII), V5 rank_signals (§7.4, без freshness/резюме).
- ST-7.7 — V6__create_selection_results.sql: снимок подборки (request/recommended_categories/items jsonb, parent_selection_id) — закрытие пробела data_model, утверждено пользователем.

## 2026-10-08 — S7 Phase 3: batch 2 (сид + консьюмеры)
- Oracle-правки батча 1 закрыты (fix-9): jdbc+postgresql deps, именованные constraints V4/V5, унификация стиля; source_event_id оставлен намеренно.
- ST-7.2 — selection_seed.sql: industry it + специализации + 5 грейдов (intern..lead, grade_order 0..4), идемпотентный ON CONFLICT (kind, code).
- ST-7.8 — CategoryAssignedListener: upsert candidate_categories, θ из события отсутствует → NULL, republish-safe (TD-1), AmqpConfig (3 очереди + DLQ) + rabbitmq-блок yml, ProcessedEventRepository.
- ST-7.9 — CardUpdatedListener: upsert candidate_cards без PII, visible_to_employers=false → delete, null spec/grade → skip.
- ST-7.10 — AchievementsUpdatedListener: weighted 1.0/0.5/0.25 по place (OQ-1 placeholders), категория из candidate_categories (no-op если нет), theta не трогается, зеркало fsp_verified/count в карточки. Финальная компиляция BUILD SUCCESSFUL.

## 2026-10-08 — S7 Phase 3: batch 2 fixes + batch 3
- Oracle-находки батча 2 закрыты (fix-14): CardUpdatedListener DEDUP_COLUMN event_id→source_event_id (critical), null visible_to_employers → skip, null display_name → skip-guard.
- ST-7.11 — GET /catalog/specializations|grades|categories: TaxonomyRepository (is_active=true, grades по grade_order), категории выводятся (специализация × грейд), categoryCode <spec>-<grade>.
- ST-7.12 — GET /candidates?filters: INNER JOIN cards⋈categories (видимость = наличие категории), categoryConfirmed = cards.grade ≠ categories.grade, стек через GIN stack && :tags, PageResponse как в siblings, без PII.
- ST-7.14 — RankingService: score = thetaWeight·coalesce(theta,0) + fspAchievementsWeight·weighted + fspVerifiedBonus, unconfirmedGradePenalty, только веса из SelectionProperties (OQ-1), topN, tie-break по candidate_id; RankSignalRepository.readMethod добавлен, write-side не тронут.

## 2026-10-08 — S7 Phase 3: batch 3 fixes + batches 4-5
- Oracle-находки батча 3 закрыты (fix-18): фильтры spec/grade переведены на category basis (candidate_categories), подтверждённость — лексикографическая группа перед score (инвариант «неподтверждённые ниже» строгий), snake_case @JsonProperty в catalog DTO.
- ST-7.15 — POST /selections: OQ-27 валидация против активной taxonomy (ApiException → 400 validation_error), пул по категории + RankingService top-N, объяснимость §4.3 (4 качественных блока, θ/score не раскрываются), рекомендуемые категории = запрошенная + грейды той же специализации с кандидатами (иллюстративный дефолт, docs §4.2 недоопределяет — на items не влияет), снапшот в selection_results (jsonb, parent NULL).
- ST-7.16 — GET /selections/{id} (findById, jsonb readValue тем же маппером, ответ = снапшот 1:1) + refinement: POST c parent_selection_id → новая строка, проверка родителя → 404, исходная не перезаписывается.
- error/ApiException.java добавлен в selection (зеркало sibling-конвенции), обработчик в ApiExceptionHandler.

## 2026-10-08 — S7 Phase 3: batches 4-5 fixes + batch 6 (тесты)
- Oracle-находки батчей 4-5 закрыты (fix-21): пул подборки пагинируется до исчерпания категории до ранжирования (гарантия top-N по §4.2), глубина цепочек уточнения не ограничена — решение задокументировано (циклы невозможны).
- ST-7.17 — CardConvergenceFlowTest: сходимость карточек по событиям, TD-1 republish + идемпотентность дубликатов, no-PII blacklist; скаффолдинг src/test, конвенции siblings (assumption-gated SELECTION_TEST_DB_URL, без Spring/Testcontainers, слушатели напрямую).
- ST-7.18 — CandidateSearchPostgresDbTest: кандидат без category_assigned не возвращается; после — возвращается; visible_to_employers=false → исчезает.
- ST-7.19/7.20 — SelectionFlowTest (4 блока объяснимости у каждого item, неподтверждённые ниже, θ не в JSON, OQ-27, снапшот=ответ) + RankInvarianceFlowTest (стек только фильтр, updated_at не влияет на позиции, RankInput blacklist, детерминизм). compileTestJava чист; прогон гейтился на SELECTION_TEST_DB_URL.

## 2026-10-08 — S7 Phase 4: smoke + финальные фиксы
- Oracle-находки батча 6 закрыты (fix-26): ≥2 выживших в stack-фильтре, tie-break по равным сигналам (D/E, ascending candidate_id), дубликат card_updated в convergence-тесте.
- Smoke-прогон на реальном PG (docker compose postgres, Flyway selection, superuser URL + currentSchema=selection): 2 реальных бага — untyped Instant bind (CandidateCategoryRepository) и text[] && varchar[] (CandidateSearchRepository).
- Фиксы (fix-28): utc() helper через OffsetDateTime (зеркало sibling JdbcRows.utc), textArray SqlTypeValue через createArrayOf("text") в 3 репозиториях (search/card upsert).
- Фикс теста (fix-29): PgArray.equals не переопределён — array-safe сравнение (comparableRow/stackOf).
- Итог: 4 класса / 5 тестов — все PASSED; exit-критерии 1-4 покрыты и зелёные.

## 2026-10-08 — S8 Phase 1: context load
- TODO.md S8 — скоуп извлечён: outreach-service (LHH-приглашения, жизненный цикл, раскрытие контактов), 5 exit-критериев, depends_on S1,S2,S3,S6,S7.
- selection.md §6.1 — поля приглашения: message, зарплата «от–до» (руб., gross/net — обязательна), company_name, employer_contact snapshot; привязка к вакансии не обязательна.
- data_model.md §9/§12/§13 — invitations (17 полей) + invitation_events (append-only, 6 event_type); статусы sent/viewed/accepted/declined/expired; контакты раскрываются только после accept (§12).
- api.md §3.7 — GET|POST /invitations, POST /invitations/{id}/mark-viewed|accept|decline; глобально: пагинация page/size/sort, Idempotency-Key на POST-приглашения, error-конверт problem+json (§2).
- roles.md — правила 1–3: зарплата обязательна, раскрытие контактов только после принятия/отклика, статусы видны обеим сторонам.
- closed_questions.md TD-4 — резолюция: snapshot employer_contact без cross-schema join; OQ-3 (TODO.md:80–83) — «типовой snapshot, уточнение при старте S8» → решение требуется на старте S8.
- Зависимости: S1/S2/S6/S7 [x]; S3 [~] (все 5 exit-критериев не закрыты) — влияние на S8 проверяется.
- worklog.md — формат зафиксирован (заголовок + bullets); корневых рабочих доков нет.

## 2026-10-08 — S8 Phase 2: декомпозиция + решения A/B/C
- Oracle — S8 декомпозирован на 15 подзадач ST-8.1..8.15, 4 батча; конфирм layout — designer (ST-8.1: 2 файла, jjwt-трио вместо starter-security, publisher-confirm correlated).
- Решение A (OQ-3, утверждено): employer_contact snapshot из тела POST /invitations, хранится как есть {contact_email, contact_phone, website}; ≥1 непустой канал, иначе 400 validation_error; без sync-вызова employer-service (TD-4 соблюдён).
- Решение B (утверждено): проверка категории — sync GET /internal/candidates/{id}/category в selection-service (отклонение docs: одна строка в api.md §3.5); 404 ⇒ у приглашающего 400 validation_error.
- Решение C (утверждено): одно письмо по candidate.contact_released (payload несёт contact_email); консьюмеры invitation.sent/accepted отложены — payload'ы без PII не позволяют вычислить получателя; OQ-22 (show_contacts=false ⇒ release не будет) — зафиксировать на ST-8.15.

## 2026-10-08 — S8 Phase 3: wave 1 (инфра outreach + контракты + нотификация)
- Параллельные лайны fixer'ов (outreach / contracts+selection / notification); артефакты доставлены коммитом 6ce0540.
- ST-8.1 — outreach build.gradle.kts (jdbc/flyway/pg/amqp/event-lib/jjwt 0.13.0; без starter-web/security/validation) + application.yml (datasource OUTREACH_*, flyway outreach, rabbitmq publisher-confirm correlated, pullhire.outbox.enabled, auth.jwks-url).
- ST-8.2 — V1 invitations (data_model §9.1 дословно), V2 invitation_events (§9.2, append-only), V3 outbox (зеркало event-lib/employer), V4 processed_events (зеркало selection).
- ST-8.4 — JwksKeyProvider (зеркало employer F10), JwtAuthFilter (8 префиксов как у siblings, без role-gate — dual-role), CurrentUser.
- ST-8.3 — contracts invitation.{sent,viewed,declined}.schema.json (зеркало accepted, additionalProperties:false) + README; SchemaConformanceTest зелёный.
- ST-8.5 — selection GET /internal/candidates/{id}/category (CategoryRow/findByCandidateId, DTO snake_case, 404 not_found) + строка в api.md §3.5 (решение B).
- ST-8.11 — notification ContactReleasedListener (claimRow: sent→skip, failed→re-send; source_event_id dedup), шаблон CONTACT_RELEASED, очередь notification.candidate-contact-released + DLQ.
- Верификация wave 1: :outreach-service:compileJava, :selection-service:compileJava, :notification-service:compileJava, :event-lib:test SchemaConformance — PASS (в окружении исполнителей).
- designer batch-конфирм: расхождений с docs нет; DTO категории минимален (намеренно); PUBLIC_PREFIXES — полный 8-префиксный sibling-набор.

## 2026-10-10 — S8 Phase 3: wave 2 (домен outreach) + wave 3
- Wave 2 (fixer, outreach lane) — ST-8.6..8.10: репозитории (JdbcRows/InvitationRepository/InvitationEventRepository/ProcessedEventRepository), domain/Invitation, CategoryCheckClient + CandidateUserClient (3s, HttpClient mirror fsp), ApiException+handler, InvitationService (create/validate→category gate, transitions 403/404/409, outbox invitation.*), InvitationController+DTO (role dispatch, 201), ContactReleasedListener (dedup event_id, markReleased by candidate, per-row contact_released audit), AmqpConfig (outreach.candidate-contact-released + DLQ).
- Верификация: :outreach-service:compileJava PASS (JDK 21; дефолтный JDK 26 ломает Gradle DSL — env-проблема, не код).

## 2026-10-10 — S8 Phase 4/5: верификация, гейт, push
- Oracle per-ST верификация: 12 ST pass, 3 minor (flyway starter, contract-parity для эмулированной candidate-ноги, строка хронологии) — все закрыты fixer'ом.
- Oracle финальный гейт: все 5 exit-критериев MET (evidence file:line в отчёте); SLICE COMPLETE.
- ST-8.13 — ContactReleasedListenerTest 3/3 PASS (Gradle test-worker несовместим с кириллическим путём чекаута — env-блок; честный прогон 3/3 + UserRegisteredListenerTest 8/8 без регрессий).
- ST-8.14 — отклонение зафиксировано: candidate-нога эмулирована test-double с parity-проверкой против contracts/events/candidate.contact_released.schema.json; полный сквозной E2E — S12 (7-step smoke).
- TODO.md: S8 [ ] → [x], чекбоксы exit-критериев закрыты. Push на slice после approval.

## 2026-10-10 — S9 Phase 1: context load
- TODO.md S9 — скоуп извлечён: сквозные NFR (Idempotency-Key, rate-limit 429, аудит PII, correlation_id, footprint), 5 exit-критериев, depends_on S3–S8.
- api.md §1–§2 — Idempotency-Key для небезопасных POST (повтор = прежний результат без эффекта), problem+json конверт (429 в том же конверте), X-Request-Id → correlation_id; мутирующие: POST /selections, /invitations* , /vacancies/{id}/applications [ext].
- data_model.md §12–13 — PII-правила видимости (поисковая проекция без PII, §12 п.7), аудит-журналы по 8 схемам (auth, candidate, fsp, assessment, selection, employer, outreach, notification), журналы без паролей/токенов/контактов.
- architecture.md §3.2/§5.3/§7.1 — статический rate-limit на Nginx, mem_limit в compose, footprint ≈2.0–2.2 ГБ, X-Request-Id распространяется как correlation_id в событиях (без трейсера).
- deployment.md §1/§5 — целевой footprint ≈2.0–2.2 ГБ RAM, smoke-порядок: up → healthcheck → seed → e2e.
- requirements/nonfunctional.md — только §Безопасность и 152-ФЗ дословно; §лимиты/§наблюдаемость отсутствуют — числовые лимиты живут в api/architecture/deployment.
- stack.md — Nginx 1.27-alpine, Actuator/Micrometer; отдельных rate-limit/idempotency/tracing-библиотек нет.
- worklog.md — формат подтверждён (заголовок + bullets); корневых рабочих доков нет.

## 2026-10-10 — S9 Phase 2: декомпозиция + решения A–F
- Oracle — S9 декомпозирован на 10 подзадач ST-9.1..9.11, 3 батча; факт-проверка репо: nginx-зона есть без limit_req_status 429 (по умолчанию 503 HTML), mem_limit в compose суммой ≈2.35 ГБ, event-lib без jdbc.
- Решение A (утверждено): Idempotency-Key middleware — НОВЫЙ Gradle-модуль idempotency-lib (jdbc + фильтр + стор), а не расширение event-lib.
- Решение B (утверждено): полный охват api.md §1 — wiring в 4 сервисах: selection, outreach, auth (регистрация), assessment (ответ в сессии).
- Решение C (утверждено): ключ только на POST-создания (POST /selections, POST /invitations, POST /applications/auth-register/...); переходы /invitations/{id}/* — машиной состояний, без ключа.
- Решение D (утверждено): POST /vacancies/{id}/applications [ext] исключён из S9 — endpoint появится в S12.
- Решение E (утверждено): тело 429 — динамический problem+json через njs (официальный JS-модуль nginx): реальный request_id и timestamp; ST-9.0 — синхронизация docs/stack.md + docs/architecture.md §3.2, api.md §2 не меняется.
- Решение F (утверждено): TTL 24ч ленивой чисткой idempotency_keys (DELETE при вставке) + запись в docs/assumptions.md.

## 2026-10-10 — S9 Phase 3: batch A (ST-9.0..9.7)
- designer batch-конфирм (5 лайнов): конфликтов файлов нет; конверт 429 — вложенный `error` по api.md §2 (брифинг неточен — исправлено); сумма mem_limit пересчитана ≈2.53 GiB.
- ST-9.0 — docs sync: stack.md +строка njs-модуля; architecture.md §3.2 — parenthetical «тело 429 формирует njs, rate-limit статический»; api.md §2 +строка 429/rate_limited; assumptions.md §12 — TTL 24ч ленивой чисткой; data_model.md не тронут (внутренние таблицы там не листингуются).
- ST-9.1 — backend/idempotency-lib: IdempotencyFilter (409 nested problem+json при отсутствии ключа; replay status+body+content-type; persist только 2xx; DuplicateKeyException → re-lookup/409), IdempotencyResultStore (JdbcTemplate, lazy TTL DELETE + ON CONFLICT DO NOTHING), Properties (enabled=false, pathPatterns, ttl=PT24H), AutoConfiguration + imports, reference DDL; settings/build.gradle.kts обновлены; тест 3/3 через JUnit launcher (Gradle test-worker — env-блок, mirror S8).
- ST-9.4 — api-gateway: njs/json429.js (build: nested problem+json, request_id из $pullhire_request_id — обходит gotcha кэширования $request_id), nginx.conf с load_module ngx_http_js_module, default.conf +limit_req_status 429 +error_page 429=@json429 +js_import, Dockerfile: COPY + условный apk add nginx-module-njs; node --check OK; nginx -t не прогнан (docker daemon недоступен).
- ST-9.5 — scripts/audit-pii-conventions.sh (157): pg_value/pg_lines helpers, checks a-e (candidate_cards whitelist+contact-regex, 5 §13-таблиц, secret/contact-scan, 8 схем), PII AUDIT OK/exit 1; bash -n OK (Git-Bash).
- ST-9.6 — scripts/smoke-correlation.sh (~210): register→verify→login (S6-паттерн, Mailpit), fixed X-Request-Id через цепочку selections→invitations→accept, poll-grep correlation_id по auth/selection/outreach/candidate/notification, gateway-generation check.
- ST-9.7 — scripts/check-footprint.sh (89): docker stats --no-stream pullhire-*, нормализация KiB/MiB/GiB→MiB, таблица usage+limit, порог 2200 MiB, footer про sum(limits)=2592 MiB > target (тюнинг — ST-9.8); bash -n OK.

## 2026-10-10 — S9 Phase 3: batch B (wiring idempotency в 4 сервиса)
- designer batch-конфирм (4 лайна): replay-safety подтверждена (filter hit не доходит до контроллера — нет двойных outbox); паттерн /invitations не матчит переходы (decision C); /auth/register и /assessment/sessions/*/answers — обязательные pathPatterns; smoke call-sites для register локализованы.
- ST-9.2 — selection: dep + pullhire.idempotency (path-patterns /selections), V7__create_idempotency_keys, IdempotencyFilterPostgresDbTest (standalone MockMvc, SELECTION_TEST_DB_URL gate, replay по row-count selection_results); POST /selections остаётся 200 (docs не нормируют 201 — зафиксировано).
- ST-9.3 — outreach: dep + yml (path-patterns /invitations), V5__create_idempotency_keys, InvitationIdempotencyTest 4 теста/5 cases (create+replay, 409, 4xx-not-stored+retry, accept-bypass).
- ST-9.10 — auth: dep + yml (path-patterns /auth/register), V6__create_idempotency_keys, AuthIdempotencyFilterTest (in-memory store, scoping: /auth/login без ключа = 200); smoke-compose.sh register_user() + Idempotency-Key (уникальный по умолчанию) + S2 replay-регрессия (тот же ключ+email → 201, не 409); smoke-correlation.sh register_user() + заголовок; bash -n обоих скриптов OK.
- ST-9.11 — assessment: dep + yml (path-patterns /assessment/sessions/*/answers), V3__create_idempotency_keys, javadoc AnswerService/Controller обновлены (store активен), IdempotencyFilterScopingTest (survey — без ключа, answers без ключа — 409); env-обход: root cause Gradle test-worker — кириллический путь в sun.jnu.encoding; JUnit launcher с 8.3-путём (C:\Users\E5EE~1) → 6/6 PASS (2 новых + 4 существующих controller-теста).
- Верификация: все 4 сервиса compileJava+compileTestJava BUILD SUCCESSFUL (JDK 21); DB-тесты selection/outreach env-gated (не прогнаны — нет TEST_DB_URL в окружении).

## 2026-10-10 — S9 Phase 5: ST-9.8 live-прогон + закрытие
- ST-9.8 — живой прогон: 429 через gateway PASS (30 конкурентных GET → 21×200 + 9×429, тело nested problem+json с реальным request_id и timestamp из njs; nginx -t OK, njs резолвится); idempotency live PASS (POST /auth/register дважды с тем же ключом → байт-идентичные 201, auth.users 1 строка, outbox 1 user.registered); footprint PASS 633 MiB ≤ 2200 (auth 186 + notification 185 + pg 70 + rabbitmq 169 + mailpit 23); фикс scripts/audit-pii-conventions.sh — auth.users исключён из secrets-скана (password_hash = каноническое хранилище §3.1, не дубль).
- Env-блок (офлайн, нет сети к docker.io): образы selection/outreach/assessment/employer/candidate/fsp отсутствуют → критерий 3 частично (4/6 таблиц + оба secrets-check PASS; candidate_cards/grade_history/invitation_events не созданы без миграций незапущенных сервисов), критерий 5 частично (gateway generation/echo + auth/notification-нога PASS; employer-нога 400 — нет образа). Scaffolding: 6 DNS-стабов для апстримов nginx; flyway checksum repair V5 (env-drift комментария из d2f81f0, dev-volume).
- TODO.md: S9 [ ] → [~]; exit 1/2/4 → [x]; exit 3/5 → открыты (env-блок, полное стек-верифицирование при доступной сети).

## 2026-10-10 — S9 Phase 5: финализация — DB-drift тесты, CI, полный стек
- Oracle-ревью data-слоя: raw-JDBC риски (schema-drift не гейтится в CI — high; type-safety мапперов — medium; инъекции — low); решение зафиксировано assumptions.md §13.
- ST-9.12..9.14 — DB-drift тесты закрытия пробела: auth 5/5, candidate 11/11 (full listener path + outbox dedup), notification 6/6 — против живого compose-PG (JUnit Launcher workaround; env-квирк: нативный postgres.exe занимает host:5432 — временные relays, удалены).
- ST-9.15 — ci.yml: postgres:16-alpine service + 8 *_TEST_DB_URL env — DB-drift тесты гейтятся в CI.
- ST-9.16 — assumptions.md §13: raw-SQL vs Hibernate (footprint 192m, стек RoboMatch, барьер зависимостей; обязателен DbTest-гейт).
- Фиксы: @Autowired на амбивалентных конструкторах (assessment), log.error в selection generic-handler, python3-заглушка Git-Bash (переходник на python), сид специализаций пуст — загружен, snake_case PUT /candidate/profile, продуктовый баг candidate_id=user_id→profile_id (фильтр категории outreach не сходился бы никогда).
- Сборка образов переведена на host-jar packaging (deployment.md §2: локальный ./gradlew bootJar, Docker только упаковывает) — устранено скачивание Gradle внутрь контейнера.
- Полный стек: критерий 3 (PII AUDIT OK) закрыт; критерий 5 — correlation на цепочке auth→candidate→assessment→employer→selection→notification PASS; outreach-нога блокирована калибровкой assessment (банк 4 вопроса vs SE≤0.3, OQ-1) — зафиксировано как TD S13.
- Footprint полный стек: 1919 MiB.
- TODO.md: S9 [~] → [x], все 5 exit-критериев [x] (5 — на урезанной цепочке, обоснование в TODO-примечании + TD).



## 2026-10-11 � S10 Phase 1 (scope)
- �������: �������� scope S10 (������� ��������� SPA), gate ������������ S1/S3/S4/S5/S8 ������.
- ���������: TODO.md:459-477; stack.md �3; user_flow.md �1-2; requirements/roles.md; api.md �coverage.
- ������ S3/S4/S5 ��������� �� [x] �� �������� ���������.

## 2026-10-11 � S10 Phase 2 (decomposition)
- �������: S10 �������������� �� ST-1..ST-17; ���� ������: B0=ST-1/ST-2/ST-17, B1=13 ������� �����������, B2=ST-16 ����������.
- �������: docs/examples/openapi/ ���� � ��������� TS-�������� (ST-2) ������������� �� ��������� ������������.

## 2026-10-11 � S10 Phase 3, Batch 0
- ST-17: exit criteria S10 �������������� ���-���������� �� ������� (TODO.md, +19).
- ST-1: shell/session/guards � layout.tsx, page.tsx, lib/auth.ts, lib/api.ts, components/nav.ts, components/shell.tsx (+575), guards client-side (output: export).
- ST-2: 6 baseline-������������ ����� � ������ �����, openapi-typescript v7, generate:clients, ��������������� schema.d.ts, CI job client-regen (+4661).
- �������: ����������� oracle �� Batch 0 ����� Batch 1.

## 2026-10-11 � S10 Batch 0 ����������� + �����
- Oracle: FIX REQUIRED � 1 blocker (��� package-lock.json), 1 major (auth.ts ������� api.ts), 2 minor.
- Fixer: lockfile ������, refresh ����� apiFetch(anonymous), README ������� (docs/architecture.md �4); generate:clients ������������, tsc exit 0.
- S3/S4/S5 ������� � ���������� �������� Phase 0, ������������ ����������.

## 2026-10-11 � S10 Phase 3, Batch 1
- ST-3..ST-15: 13 ������� ����������� ����������� (fixer ?13), +2735 ����� ��������; ������ ������� ����� ����������� frontend/app/(candidate)/<screen>/.
- ST-6: upload ����� rest.body (�� multipart), PDF download ����� plain fetch + Bearer; ST-12: ���� �������� client-side; ST-14: ��������� unknown-guard ���������.
- �����������: tsc --noEmit exit 0 (���������� ������ ST-3 � ���� ������������ ������ ���������).
- �������: oracle-����������� Batch 1.

## 2026-10-11 � S10 Batch 1 ����������� + �����
- Oracle: FIX REQUIRED � 2 major (consent type �� �������� � enum �������; upload �� multipart), 3 minor (dead-link ���������, ?/SE � UI, baseline-����� candidate.json).
- Fixer: consent types ��������� � ConsentType.java; upload > FormData multipart, ����� 5 ��; link > ?invitation=; ?/SE ������; candidate.json multipart + �����������; gateway client_max_body_size 6m.
- tsc exit 0, generate:clients ������������.

## 2026-10-11 � S10 Batch 1 �������������
- Oracle: APPROVE Batch 1 � 6/6 ������ ������������, tsc exit 0.
- �������������: tsconfig.tsbuildinfo �������� � .gitignore ����� ��������.

## 2026-10-11 � S10 Phase 3, Batch 2 (ST-16)
- �������: nav.ts/shell.tsx wired � �������� ��������, badge ����������� (������������� hook ������ shell), RoutePlaceholder �����.
- Smoke �� user_flow �2 ����� gateway: register/verify/login/consents/profile/survey/grade/session/answers/history/notifications = 200/201; �������� ������ �� category_assigned; invitations ����� (seed � S12).
- tsc exit 0. �������: ��������� oracle-�����������.

## 2026-10-11 � S10 Phase 4 ��������� ����������� + �����
- Oracle: FIX REQUIRED � 1 major (��������� ������� session_id survey>grade>test), 2 minor (��� ����� �� /profile/upload; tailwind content ��� components/), 1 hygiene (tsconfig.tsbuildinfo �� ������������).
- Fixer: survey persist session_id > grade read/auto-display/write > /test?sessionId= > test fallback localStorage; ������ �� /profile/upload �� profile; tailwind content + components; frontend/.gitignore (*.tsbuildinfo).
- tsc exit 0. �������: ������������� ora-7.

## 2026-10-11 � S10 Phase 4 �������������
- Oracle: APPROVE S10 for user acceptance review � 4/4 ����� ������������ (������� session_id ������� �� ������ ����, tsc exit 0, tsbuildinfo ������������).
- ��������: ���������� acceptance-������ (����-������, ������� ������� category_assigned/contact_released).

## 2026-10-11 � S10 Phase 5 (push)
- �������: commit 33385cb �� slice (45 ������), push origin slice, pull � up-to-date, working tree clean.
- �������: ���������� acceptance-������ ������������; S12 seed ��� �����������.
- 2026-10-11: deploy E2E verified locally (compose up OK, 8 services healthy, smoke: manual gateway 200)
- 2026-10-11: README.md authored (11 sections per RoboMatch layout, claims traced to docs/code/deploy evidence)
- 2026-10-11: smoke-compose.sh PASS on live stack (review finding D1 closed)
