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
