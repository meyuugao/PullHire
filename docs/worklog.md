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
