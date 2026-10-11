# PullHire

PullHire — цифровая платформа-агрегатор ИТ-вакансий с верифицированным профилем достижений участника ФСП. Категория кандидата (специализация × грейд) присваивается по обязательному пути «опрос → заявленный грейд → тест», а выход на контакт инициирует работодатель (LHH-механика). Успех MVP — сквозной 7-шаговый демо-сценарий (кандидат → категория → подборка → приглашение → раскрытие контактов) на воспроизводимом локальном стенде (`TODO.md`, «Цель проекта»).

| URL | Назначение |
|---|---|
| `https://localhost` | api-gateway (вход API; `/` → 302 на `/docs/`) |
| `https://localhost/docs/` | индекс документации API на шлюзе |
| `https://localhost/openapi/<сервис>` | OpenAPI-спецификация сервиса через шлюз (`auth`, `candidate`, `fsp`, `assessment`, `selection`, `employer`, `outreach`, `notification`) |
| `http://localhost:3000` | frontend (Next.js SPA) |
| `http://localhost:15672` | RabbitMQ Management |
| `http://localhost:8025` | Mailpit (dev-почта) |

## Статус проекта и дедлайн

| Срез | Статус | Примечание |
|---|---|---|
| S0 — Bootstrap | `[x]` готов | репозиторий, Gradle multi-module, compose-каркас, CI |
| S1 — auth + notification | `[x]` готов | идентичность, JWT, согласия 152-ФЗ, почта |
| S2 — событийная шина | `[x]` готов | outbox, идемпотентность, наблюдаемость |
| S3 — candidate | `[x]` готов | профиль, резюме/PDF, приватность, карточка |
| S4 — fsp | `[x]` готов | mock-адаптер ФСП, линк ID, кэш достижений |
| S5 — assessment | `[x]` готов | опрос, грейд, тест, категория, cooldown |
| S6 — employer | `[x]` готов | компания и потребности |
| S7 — selection | `[x]` готов | таксономия, категории, ранжирование, подборка |
| S8 — outreach | `[x]` готов | LHH-приглашения, жизненный цикл, раскрытие контактов |
| S9 — сквозные NFR | `[x]` готов | Idempotency-Key, rate-limit, аудит PII, footprint |
| S10 — кабинет кандидата (SPA) | `[~]` в работе | текущий слайс |
| S11 — кабинет работодателя (SPA) | `[ ]` не начат | — |
| S12 — seed, развёртывание, демо | `[ ]` не начат | seed-сервис отсутствует |
| S13 — документация, презентация | `[ ]` не начат | — |
| S14–S17 | `[opt]` опционально | регулярные задания, вакансии/отклики, моб. адаптивность, ML/NLP — после S12 |

Источник статусов — `TODO.md`, «Индекс слайсов». Вехи пакета документации (`docs/backlog/closed_questions.md`, итоговая таблица): 2026-10-05 — baseline `docs/requirements`; 2026-10-06 — core/structural docs; 2026-10-07 — S2; 2026-10-08 — S4; 2026-10-10 — S8. Календарный дедлайн в документах не зафиксирован. Критерий оценки после дедлайна — дата последних коммитов и неизменность истории (`docs/final_summary.md §4`).

## Документация

| Документ | Описание |
|---|---|
| `docs/architecture.md` | функциональная и компонентная архитектура, сервисы, топология, безопасность |
| `docs/api.md` | общий индекс HTTP API, конвенции, модель ошибок |
| `docs/data_model.md` | ER-модель: сущности, поля, перечисления по 8 схемам |
| `docs/deployment.md` | сборка, развёртывание, локальный запуск, демо-сценарий |
| `docs/stack.md` | библиотеки, компоненты, версии образов, лицензии |
| `docs/user_flow.md` | сквозные пользовательские сценарии и демо-путь |
| `docs/mvp_scope.md` | границы MVP и опциональные срезы |
| `docs/requirements/*` | verbatim-источники требований (read-only) |

Полный пакет: `docs/testing_model.md`, `docs/selection.md`, `docs/validation.md`, `docs/fsp_integration.md`, `docs/periodic_tasks.md`, `docs/glossary.md`, `docs/assumptions.md`, `docs/final_summary.md`, `docs/backlog/closed_questions.md`, `docs/examples/`.

## Быстрый старт

Требования: Docker Engine 24+, Docker Compose v2, Git (`docs/deployment.md §1`).

```bash
git clone <repo-url>
cd PullHire
cp .env.example .env   # Windows: Copy-Item .env.example .env
docker compose up -d
docker compose run --rm seed
```

Примечание про seed: сервис появится в S12; пока compose выведет `no such service: seed`.

### Чек-лист «что должно подняться»

Проверено локальным прогоном: 13 контейнеров, 8/8 `healthz` + 8/8 `readyz` — 200.

- `postgres` — единый инстанс, 8 схем (`auth`, `candidate`, `fsp`, `assessment`, `selection`, `employer`, `outreach`, `notification`)
- `rabbitmq` (+ Management UI), `mailpit`
- 8 backend-сервисов:

| Сервис | Хост-порт |
|---|---|
| auth-service | 8081 |
| candidate-service | 8082 |
| fsp-service | 8083 |
| assessment-service | 8084 |
| selection-service | 8085 |
| employer-service | 8086 |
| outreach-service | 8087 |
| notification-service | 8088 |

- `api-gateway` — `https://localhost` (plain HTTP `http://localhost` не работает: только HTTPS, `/` → 302; сертификат self-signed, для curl — `-k`)
- `frontend` — `http://localhost:3000` → 200

Swagger / OpenAPI (проверено на живом стенде):

| Что | URL | Статус |
|---|---|---|
| индекс docs на шлюзе | `https://localhost/docs/` | 200 |
| спецификация сервиса через шлюз | `https://localhost/openapi/<сервис>` | 200 (проверено для `auth`) |
| Swagger UI сервиса | `http://localhost:8081/swagger-ui.html` | 302 (редирект на `swagger-ui/index.html` — штатное поведение springdoc) |
| api-docs сервиса | `http://localhost:8081/v3/api-docs` | 200 |

Пути springdoc (`/v3/api-docs`, `/swagger-ui.html`) — из `application.yml` всех 8 сервисов; маршруты `/docs/`, `/openapi/*` — из `api-gateway/conf.d/default.conf`. Swagger UI через шлюз не проксируется — только per-service порты и `/openapi/*` JSON.

### Пересборка после pull

```bash
docker compose up -d --build
docker compose down        # остановить
docker compose down -v     # остановить и удалить тома (сброс БД)
```

## Проверка функциональности (чек-лист)

Основание — `docs/requirements/user-flow.md`, `docs/user_flow.md`, `docs/requirements/roles.md`. Ручные проверки не выполнялись — чекбоксы не отмечены.

### Кандидат

- [ ] регистрация по email с подтверждением адреса (письмо — в Mailpit)
- [ ] согласия на обработку данных (152-ФЗ)
- [ ] заполнение профиля и резюме (форма или загрузка PDF)
- [ ] связь с ID участника ФСП
- [ ] опрос по отрасли и специализации
- [ ] самостоятельный выбор предполагаемого грейда
- [ ] прохождение теста на заявленный грейд
- [ ] присвоение категории (специализация × грейд); карточка видима работодателю
- [ ] просмотр входящих приглашений (с ЗП «от–до»), принятие / отклонение
- [ ] раскрытие контактов после принятия приглашения

### Работодатель

- [ ] регистрация по email с подтверждением адреса
- [ ] профиль компании (описание, направление, контакты)
- [ ] описание потребности (стек, специализация, грейд)
- [ ] подборка кандидатов по категории + объяснимость попадания
- [ ] поиск по банку кандидатов (фильтры: специализация, грейд, категория, стек, достижения ФСП)
- [ ] приглашение конкретному кандидату с обязательной ЗП «от–до», без привязки к вакансии
- [ ] отслеживание статусов приглашений
- [ ] контакты кандидата видны только после принятия приглашения

### Нефункциональное

- [ ] все сервисы `healthy` (`docker compose ps`), `/healthz` + `/readyz` — 200
- [ ] `Idempotency-Key` на протокольных операциях
- [ ] rate-limit отвечает 429 с JSON-обёрткой
- [ ] PII не попадает в логи (audit-скрипт `scripts/audit-pii-conventions.sh`)
- [ ] footprint контейнеров ≈ 2.0–2.2 ГБ RAM

## Роли и демо-аккаунты

Источник — `docs/requirements/roles.md`.

| Роль | Описание | Возможности |
|---|---|---|
| Кандидат | соискатель ИТ-позиции | профиль/резюме, ФСП ID, опрос → грейд → тест, категория, приглашения (принять/отклонить), приватность |
| Работодатель | компания-наниматель | компания, потребности, подборка + поиск, приглашения с ЗП «от–до», статусы; контакты — только после принятия |

Регистрация выполняется по email с подтверждением; верификация компаний и модерация вакансий на этапе MVP не требуются. Демо-аккаунты: запланировано (S12).

## Демо-данные и фикстуры

Запланировано (S12). Принципы зафиксированы в `docs/assumptions.md §7`: синтетические данные, идемпотентный детерминированный seed через одноразовый контейнер `seed`; повторный запуск — no-op; генерация — seed-фикстуры. Внешний вид дампа демо-данных отсутствует.

## Примеры артефактов

- OpenAPI-спецификации — springdoc per service (`/v3/api-docs`; через шлюз — `/openapi/<сервис>`); сборный индекс — `https://localhost/docs/`
- Контракты событий — `contracts/events/`: `envelope.schema.json` + 13 схем (`user.registered`, `fsp.linked`, `fsp.achievements_updated`, `candidate.card_updated`, `candidate.contact_released`, `assessment.grade_changed`, `assessment.category_assigned`, `employer.need_deleted`, `invitation.sent/viewed/accepted/declined`)
- Smoke-скрипты — `scripts/smoke-compose.sh`, `scripts/smoke-correlation.sh`

## Диагностика

Основание — `docs/deployment.md §5` + факты живого прогона.

| Симптом | Решение |
|---|---|
| `http://localhost` не отвечает / SSL-ошибка | работает только HTTPS: `https://localhost` (self-signed → `curl -k`); `/` → 302 на `/docs/` — штатно |
| `docker compose run --rm seed` → `no such service: seed` | ожидаемо до S12 (seed не реализован) |
| `port is already allocated` | освободить порт или изменить маппинг в compose |
| сервис `unhealthy` | дождаться `postgres`/`rabbitmq`; проверить `/readyz` |
| ошибки миграций Flyway | `docker compose down -v` и повторный запуск |
| письма не видны | открыть `http://localhost:8025` (Mailpit) |
| `401` на API | истёк/невалиден JWT — перевыпустить токен, проверить ключи RS256; часть маршрутов требует `Authorization: Bearer` штатно |
| пустой каталог | выполнить `seed` (когда появится в S12) |
| события не доходят | проверить RabbitMQ UI (`:15672`), очереди и outbox |
| сбой сборки Dockerfile сервиса (нет общего модуля) | перед COPY сервисного `src` добавить `COPY event-lib/build.gradle.kts event-lib/build.gradle.kts` и `COPY event-lib/src event-lib/src` (`TODO.md`, `docs/worklog.md`) |
| нехватка памяти | отключить demo-профили (Prometheus/Grafana) |

## Структура репозитория

```text
PullHire/
├── backend/               # 8 Spring Boot сервисов + общие библиотеки
│   ├── auth-service/      # :8081 идентичность, JWT, согласия 152-ФЗ
│   ├── candidate-service/ # :8082 профиль, резюме/PDF, карточка
│   ├── fsp-service/       # :8083 mock-адаптер ФСП
│   ├── assessment-service/# :8084 опрос, грейд, тест, категория
│   ├── selection-service/ # :8085 таксономия, ранжирование, подборка
│   ├── employer-service/  # :8086 компания, потребности
│   ├── outreach-service/  # :8087 приглашения LHH
│   ├── notification-service/ # :8088 почта
│   ├── event-lib/         # общая событийная библиотека
│   └── idempotency-lib/   # идемпотентность
├── api-gateway/           # Nginx: TLS, маршруты, /docs, /openapi/*
├── frontend/              # Next.js SPA (S10/S11)
├── contracts/             # JSON-схемы событий (events/)
├── postgres/              # init-скрипты БД
├── docs/                  # пакет документации + requirements/ + backlog/
├── scripts/               # smoke- и audit-скрипты
├── .github/               # CI
├── docker-compose.yml     # локальный стенд
└── .env.example           # шаблон конфигурации (12-factor)
```

## Известные ограничения

- Модерация вакансий и верификация компаний не требуются на MVP (`docs/requirements/roles.md`, правило 4).
- Защита от фиктивных вакансий и интеграция с ATS — только концепция, вне MVP (`docs/final_summary.md §3`, `docs/mvp_scope.md §4`).
- `/readyz` не проверяет соединения к БД/брокеру (liveness/readiness на уровне процесса).
- Seed-сервис (S12) — в разработке; детерминированный сид и демо-данные отсутствуют.
- Внешний вид дампа демо-данных отсутствует.
