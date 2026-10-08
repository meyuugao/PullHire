# Архитектура

## Назначение документа

Документ фиксирует функциональную и компонентную архитектуру платформы:
декомпозицию на сервисы, синхронное и асинхронное взаимодействие,
топологию контейнеризации, безопасность и 152-ФЗ, наблюдаемость и
целевые показатели производительности. Реализация отвечает на требование
о «понятном разделении на Frontend, Backend (API-сервисы бизнес-логики) и
слой хранения» (`docs/requirements/nonfunctional.md`, «Архитектура»).

Связанные документы:
- `docs/data_model.md` - сущности, поля, ключи, связи, перечисления.
- `docs/testing_model.md` - механика грейд-тестирования и присвоение категории.
- `docs/selection.md` - категории, ранжирование, формирование подборки.
- `docs/periodic_tasks.md` - регулярные задания работодателей [ext].
- `docs/fsp_integration.md` - схема интеграции с реестром ФСП.
- `docs/api.md` - индекс эндпоинтов по сервисам и модель ошибок.
- `docs/stack.md` - библиотеки и компоненты с версиями.
- `docs/deployment.md` - сборка, развёртывание и локальный запуск.
- `docs/mvp_scope.md` - границы MVP.
- `docs/assumptions.md` - реестр принятых командой решений.
- `docs/glossary.md` - термины.
- `docs/backlog/closed_questions.md` - журнал решений и техдолга.

Источники требований: `docs/requirements/nonfunctional.md`,
`docs/requirements/acceptance.md`, `docs/requirements/overview.md`.

**Вне рамок документа:** схемы сущностей и полей, алгоритмы тестирования,
ранжирования и заданий, конкретные версии библиотек, команды запуска и
флаги MVP (это зоны `docs/data_model.md`, `docs/testing_model.md`,
`docs/selection.md`, `docs/stack.md`, `docs/deployment.md`,
`docs/mvp_scope.md`).

## 1. Обзор системы

### 1.1. Состав сервисов

Платформа построена как микросервисная система: восемь бизнес-сервисов,
API-шлюз и SPA для обеих ролей.

- `auth-service` - идентичность, JWT, роли, согласия 152-ФЗ.
- `candidate-service` - профиль и резюме, настройки приватности.
- `fsp-service` - шов интеграции с реестром ФСП и достижения.
- `assessment-service` - тестирование, грейд, cooldown, категория;
  регулярные задания [ext].
- `selection-service` - каталог, категории, ранжирование, подборка.
- `employer-service` - компания и потребность в найме.
- `outreach-service` - приглашения и выход на контакт; вакансии и
  классический отклик [ext].
- `notification-service` - исходящая электронная почта.
- `api-gateway` (Nginx) - единая входная точка.
- `frontend` (SPA) - веб-приложение для кандидата и работодателя.

Каждый бизнес-сервис - отдельное приложение на Java 21 + Spring Boot,
собираемое Gradle, с Spring AMQP (RabbitMQ), Flyway (миграции в своей
схеме), springdoc-openapi (документирование), JJWT (RS256) и хешированием
паролей Argon2id/BCrypt. Детали и версии - в `docs/stack.md`.

### 1.2. Текстовая схема (диаграмма)

```
Browser SPA -> api-gateway (Nginx) -> {auth, candidate, fsp, assessment,
      selection, employer, outreach, notification}
  sync REST/JSON (JWT RS256, проверка локально по кэшу JWKS)
  async: RabbitMQ topic exchange
    user.registered · user.email_verified · consent.updated ·
    candidate.card_updated · candidate.contact_released ·
    fsp.linked · fsp.achievements_updated ·
    assessment.category_assigned · assessment.grade_changed ·
    invitation.{sent,viewed,accepted,declined}
  storage: PostgreSQL (схема и роль на сервис) + volume файлов
```

### 1.3. Ключевые потоки

- **Онбординг кандидата.** `assessment-service` проводит опрос, выбор
  грейда и тест; событие `assessment.category_assigned` через outbox
  попадает в проекцию категорий `selection-service`, после чего карточка
  становится видимой работодателю. Событие
  `fsp.achievements_updated` запускает переранжирование
  (см. `docs/testing_model.md`, `docs/selection.md`).
- **Подбор (LHH).** Работодатель вызывает `POST /selections` в
  `selection-service`; сервис формирует ранжированную подборку и
  пояснения из собственных проекций. Затем `POST /invitations` в
  `outreach-service` запускает приглашение (см. `docs/selection.md`).
- **Выход на контакт.** Кандидат принимает приглашение; `invitation.accepted`
  → `candidate-service` выдаёт раскрытие контактов → `candidate.contact_released`
  → `outreach-service` сохраняет контакты для работодателя, а
  `notification-service` уведомляет кандидата.

## 2. Микросервисы vs монолит: обоснование

ТЗ приветствует микросервисную архитектуру, но допускает монолит
(`docs/requirements/nonfunctional.md`, «Архитектура»). Выбор сделан в
пользу микросервисов и подкреплён четырьмя неоднородными швами, а не
формальной «галочкой»:

- `fsp-service` - граница внешней интеграции, перепроектируемая после
  хакатона.
- `notification-service` - иной режим отказа и изоляция SMTP-доступа.
- `assessment-service` - второй по весу оцениваемый компонент (25%)
  с собственным движком.
- `selection-service` - событийная read-модель, естественная для
  ранжирования.

Риски компенсированы осознанно: 8 сервисов (не 12), четыре свёрнутых
модуля (§3.4), один PostgreSQL, единый стек, отсутствие mesh,
распределённого трейсинга и K8s, smoke-тест compose как воспроизводимая
проверка. Честная цена решения - примерно +2 недели интеграции против
монолита, окупаемые лучшей технической оценкой и наглядной схемой для
жюри.

**Запасной путь.** При отставании схлопываются `employer-service` →
`outreach-service` и `fsp-service` → `candidate-service`; контракты
событий при этом сохраняются.

## 3. Сервисы

### 3.1. Сводная таблица сервисов

| # | Сервис | Ответственность | Схема | Ключевые API | События pub/cons | ТЗ |
|---|---|---|---|---|---|---|
| 1 | `auth-service` | Идентичность, JWT, роли, согласия | `auth` | `/auth/*`, `/.well-known/jwks.json` | pub `user.registered`, `user.email_verified`, `consent.updated` | nonfunctional (Аутентификация, 152-ФЗ); roles пр.4 |
| 2 | `candidate-service` | Профиль и резюме, приватность | `candidate` | `/candidate/*`, internal card | pub `candidate.card_updated`, `candidate.contact_released`; cons `invitation.accepted` | profiles; roles (кандидат) |
| 3 | `fsp-service` | Шов интеграции ФСП, достижения | `fsp` | `/candidate/fsp/*` | pub `fsp.linked`, `fsp.achievements_updated` | profiles (ФСП); acceptance |
| 4 | `assessment-service` | Опрос, тест, грейд, cooldown, категория; задания [ext] | `assessment` | `/assessment/*` | pub `assessment.category_assigned` (любая завершённая попытка, включая повышение/понижение грейда и повторные, - для обновления проекции категории `selection`), `assessment.grade_changed` (только аудит/уведомления), [ext] `assessment.task_completed` | testing.md; roles (кандидат) |
| 5 | `selection-service` | Каталог, категории, ранжирование, подборка | `selection` | `/catalog/*`, `/candidates`, `/selections` | cons `assessment.category_assigned`, `candidate.card_updated`, `fsp.achievements_updated` | selection.md; overview; roles (работодатель) |
| 6 | `employer-service` | Компания и потребность в найме | `employer` | `/employer/company`, `/employer/needs` | - | profiles (Компания, Потребность) |
| 7 | `outreach-service` | Приглашения, контакт; вакансии/отклик [ext] | `outreach` | `/invitations/*`; [ext] `/vacancies/*` | pub `invitation.{sent,viewed,accepted,declined}`; cons `candidate.contact_released` | roles (Выход); selection (Механика); overview (LHH) |
| 8 | `notification-service` | Исходящая почта | `notification` | `/notifications` | cons `user.registered`, `invitation.sent`, `invitation.accepted`, `candidate.contact_released`, `assessment.grade_changed` | roles пр.4; structural |
| 9 | `api-gateway` (Nginx) | TLS, маршрутизация, rate-limit, `/docs` | конфигурация | `/*` | - | nonfunctional (Архитектура, API) |
| 10 | `frontend` (SPA) | Приложение для обеих ролей | - | - | - | nonfunctional; evaluation #3 |

Данные колонки «Схема» - имя логической схемы PostgreSQL (§5.2). Состав
полей и связей - в `docs/data_model.md`. Полный индекс эндпоинтов - в
`docs/api.md`. Любая завершённая попытка `assessment-service` (включая
повышение/понижение грейда и повторные попытки) публикует
`assessment.category_assigned`, чтобы `selection-service` обновил проекцию
категории; `assessment.grade_changed` служит только аудиту и уведомлениям
(см. `docs/testing_model.md §7–§8`).

### 3.2. api-gateway

Единая точка входа на Nginx:

- терминация TLS (демо - self-signed сертификат);
- маршрутизация запросов к сервисам;
- статический rate-limit;
- сквозной заголовок `X-Request-Id`;
- индекс документации `/docs` со ссылками на OpenAPI сервисов.

Шлюз не выполняет роль BFF: сервисы самостоятельно валидируют JWT
локально по кэшированному JWKS (§6.1). Дополнительный слой агрегации не
вводится (см. §4.1).

### 3.3. frontend (SPA)

Веб-приложение на React/Next.js для обеих ролей с маршрутизацией по роли
(кандидат/работодатель). Прямых обращений к сервисам минуя шлюз нет.
Клиенты к API строятся по сгенерированным OpenAPI-спецификациям (§4.4).
UI-конвенции наследуются от RoboMatch (внешний вид в этом документе не
описывается). Реализация сценариев - в `docs/user_flow.md`.

### 3.4. Свернутые модули (folds) и пути выделения

Чтобы не порождать лишние контексты, четыре модуля свёрнуты в соседние
сервисы. Для каждого документирован путь обратного выделения:

- **taxonomy → `selection-service`.** Справочники специализаций и
  грейдов живут рядом с категориями. Выделение возможно, если каталог
  станет самостоятельным доменом; контракты `assessment.category_assigned`
  и `candidate.card_updated` при этом сохраняются.
- **files → `candidate-service`.** Хранение файлов резюме реализовано
  сменным адаптером; при росте объёмов адаптер заменяется на внешнее
  хранилище без изменения остальных сервисов.
- **periodic tasks → `assessment-service` [ext].** Движок регулярных
  заданий переиспользует планировщик и форматы заданий. Выделяется в
  `tasks-service` при отдельном жизненном цикле; интерфейс жёсткий:
  задания не пишут в грейд/категорию (`docs/periodic_tasks.md`).
- **vacancies/apply → `outreach-service` [ext].** Вакансии и
  классический отклик - модуль внутри LHH-контура. Выделяются в
  `vacancy-service`, если сценарий выйдет из статуса «плюс» в основной;
  при этом сохраняется единая механика контактов.

## 4. Взаимодействие сервисов

### 4.1. Синхронное взаимодействие (REST)

- Только REST/JSON. gRPC отклонён как избыточный для MVP (YAGNI).
- Поток запроса: браузер → `api-gateway` → сервис.
- Внутренние синхронные вызовы сведены к минимуму на горячих путях за
  счёт read-моделей-проекций. Единственная цепочка «по требованию»:
  потребность работодателя → `POST /selections` (сервис читает
  собственные проекции).
- Оговорка: «единственная цепочка по требованию» действует для ядрового
  контура. Опциональная механика регулярных заданий `[opt]` добавляет
  внутренний sync-GET `employer → assessment` для сигнала свежести
  (`docs/periodic_tasks.md §6.4`).
- Обнаружение сервисов не вводится: DNS Docker Compose и статические
  маршруты Nginx; K8s/Consul отклонены для MVP.

### 4.2. События: RabbitMQ, конверт, идемпотентность

- Брокер - RabbitMQ, topic exchange, доставка at-least-once.
- Конверт события:
  `{event_id, type, version, occurred_at, producer, correlation_id, payload}`.
- Идемпотентность потребителей обеспечивается по `event_id`.
- JSON-схемы событий лежат в `contracts/events/` и проверяются в CI.

**Хореография единственного многосервисного сценария (принятие
приглашения):** `invitation.accepted` → `candidate-service` выдаёт
раскрытие контактов → `candidate.contact_released` → `outreach-service`
сохраняет контакты → `notification-service` уведомляет кандидата.
Управляющий оркестратор не вводится; поведение описано в
`docs/selection.md` и `docs/data_model.md §9`.

### 4.3. Transactional outbox и итоговая согласованность

Для событий, потеря которых критична, применяется transactional outbox:

- `assessment.category_assigned`;
- жизненный цикл приглашения (`invitation.sent/viewed/accepted/declined`).

Согласованность проекций `selection-service` - итоговая: кандидат с
неподтверждённым грейдом показывается со статусом «не подтверждён» и опускается
в ранжировании, полное скрытие - только при обоснованном решении
(`docs/testing_model.md` §7.2), а фронтенд гейтит отображение по статусу.
Это принятое следствие выбора read-модели, а не дефект.

### 4.4. Контракты: OpenAPI и JSON Schema событий

- OpenAPI каждого сервиса генерируется springdoc-openapi автоматически;
  на шлюзе собирается индекс `/docs`.
- Из спецификаций строятся TypeScript-клиенты для SPA.
- Событийные контракты - JSON Schema в `contracts/events/`;
  совместимость проверяется в CI (§7.3), изменения фиксируются в
  `docs/backlog/closed_questions.md`.

## 5. Топология контейнеризации (TOPOLOGY A)

### 5.1. Состав контейнеров docker-compose

```
core:
  frontend (nginx) · gateway (nginx) ·
  auth · candidate · fsp · assessment ·
  selection · employer · outreach · notification   (Spring Boot JARs) ·
  postgres  (1 контейнер: схемы auth|candidate|fsp|assessment|
             selection|employer|outreach|notification,
             отдельные роли, без доступа между схемами) ·
  rabbitmq  (Management UI) · mailpit (dev)
optional profiles:
  ml-parser-worker [ext] · prometheus + grafana (демо)
```

- Целевой объём памяти ≈ 2.0–2.2 ГБ RAM.
- Одноразовый контейнер `seed` наполняет каталог идемпотентно.
- Команды сборки и запуска - в `docs/deployment.md`; версии образов -
  в `docs/stack.md`.

### 5.2. PostgreSQL: схема и роль на сервис

Один контейнер PostgreSQL, логическая изоляция:

- у каждого сервиса своя схема: `auth`, `candidate`, `fsp`,
  `assessment`, `selection`, `employer`, `outreach`, `notification`;
- у каждой схемы отдельная роль БД; сервис подключается только своей
  ролью;
- **жёсткая конвенция:** нет кросс-схемных `JOIN` и внешних ключей между
  схемами; доступ к чужим данным - только через API или события.

Данные и связи внутри схем - в `docs/data_model.md`.

### 5.3. Healthcheck, зависимости и лимиты памяти

- На каждом сервисе: `/healthz` (liveness) и `/readyz` (готовность БД и
  брокера).
- В compose используется `depends_on: service_healthy`.
- Для контейнеров задан `mem_limit`.
- Целевой суммарный footprint - около 2 ГБ (§5.1), что согласуется с
  требованием «удерживать память в разумных пределах»
  (`docs/requirements/nonfunctional.md`, «Аппаратные требования»).

### 5.4. Миграция TOPOLOGY A → B

TOPOLOGY B - физический PostgreSQL на сервис. Переход выполняется **без
переписывания кода**:

- жёсткая конвенция отсутствия кросс-схемного доступа (§5.2) делает
  разделение конфигурационным;
- шаги: перенос `PGDATA` соответствующей схемы в отдельный инстанс и
  обновление строк подключения в конфигурации сервиса;
- сервисные и событийные контракты не меняются.

Решение о TOPOLOGY A обосновано бюджетом памяти, единой резервной копией
и логической изоляцией на старте (реестр решений - `docs/assumptions.md`).

## 6. Безопасность и 152-ФЗ

### 6.1. Аутентификация: JWT RS256, JWKS, пароли

- Регистрация по email с подтверждением адреса; модерация работодателей
  на этапе MVP не требуется (`docs/requirements/acceptance.md`).
- Токены: JWT RS256 с коротким TTL и refresh-токеном; публичный ключ
  публикуется как `/.well-known/jwks.json`.
- Форма claim совместима с Keycloak (`sub`, `realm_access.roles`,
  JWKS) - упрощает будущую интеграцию с ФСП ID
  (`docs/requirements/nonfunctional.md`, «Аутентификация»).
- Пароли хранятся как Argon2id/BCrypt.
- Проверка ролей (candidate/employer) выполняется по claim-ам JWT.
- Схема интеграции с ФСП - в `docs/fsp_integration.md`.

### 6.2. Согласия и приватность

- Согласие на обработку данных фиксируется при регистрации
  (`auth-service`), изменения согласий аудируются.
- Область видимости («что видно») принадлежит `candidate-service`;
  событие `candidate.card_updated` несёт только согласованные поля, так
  что поисковый индекс `selection-service` структурно не может утечь
  PII.
- Раскрытие контактов происходит только через событие
  `candidate.contact_released` - то есть после принятия приглашения
  **или самостоятельного отклика кандидата** `[ext]`
  (правила ролей в `docs/data_model.md §12`).
- Хранение и обработка - на инфраструктуре на территории РФ
  (локализация по 152-ФЗ).

### 6.3. Сетевые границы: gateway, TLS, роли БД

- Внешний трафик входит только через `api-gateway`; TLS терминируется
  на нём (демо - self-signed).
- Сервисы валидируют JWT локально по кэшу JWKS, не ходя на `auth-service`
  на каждый запрос.
- Разграничение доступа к данным обеспечено ролями БД на схему (§5.2);
  кросс-схемный доступ запрещён конвенцией.

## 7. Наблюдаемость, конфигурация и CI

### 7.1. Логи, X-Request-Id, health/metrics

- Структурированные JSON-логи.
- Заголовок `X-Request-Id` распространяется как `correlation_id` в
  событиях, обеспечивая сквозную трассировку запроса без отдельного
  трейсера.
- `/healthz` и `/readyz` на каждом сервисе (§5.3).
- `/metrics` в формате Prometheus; Prometheus + Grafana подключаются
  опциональным profile (демо).
- Распределённый трейсинг в MVP не вводится.

### 7.2. Конфигурация и секреты (12-factor)

- Конфигурация через переменные окружения (12-factor).
- `.env.example` коммитится, `.env` - в `.gitignore`.
- Секреты не попадают в репозиторий; в CI встроена проверка.
- Реестр конфигурационных решений - `docs/assumptions.md`.

### 7.3. CI (GitHub Actions)

Конвейер на каждый сервис:

- линтер/форматтер → сборка и тесты (Java: build + test);
- diff снимка OpenAPI (контроль контрактов);
- проверка совместимости JSON Schema событий;
- сборка Docker-образа;
- smoke-тест compose: `up` → зелёные healthcheck → `seed` →
  скриптованный e2e-сценарий.

Smoke-тест одновременно закрывает требование «по запросу жюри команда
демонстрирует запуск и работу кода»
(`docs/requirements/nonfunctional.md`, «Качество кода»). Состав smoke и
e2e детализирован в `docs/deployment.md`; e2e-сценарий покрывает путь
`docs/testing_model.md §1`.

## 8. Производительность и платформенность

Ориентиры MVP (`docs/requirements/nonfunctional.md`, «Производительность»,
«Платформенность»):

- стабильная работа при одновременном использовании десятками
  пользователей;
- основные операции - формирование подборки, открытие профиля, поиск -
  укладываются в приемлемое время; этого достигают read-модели-проекции
  в `selection-service` (§4.1) и отсутствие синхронных внутренних цепочек
  на горячих путях;
- кроссплатформенный веб-сервис для современных десктопных браузеров,
  независимость от ОС при развёртывании (Docker);
- целевой стенд проверки жюри - небольшой сервер: **32–64 ГБ RAM и порядка
  8 CPU, без GPU** (`docs/source/qa-summary-fsp-2026.md`); целевой footprint
  контейнеров (≈2 ГБ, §5.1) укладывается в него с запасом;
- адаптивная вёрстка для мобильных и планшетов - опционально для MVP
  (границы - `docs/mvp_scope.md`).

## 9. Соответствие требованиям ТЗ

| Компонент | Требование ТЗ |
|---|---|
| `auth-service` | nonfunctional (Безопасность/152-ФЗ, Аутентификация); roles пр.4 |
| `candidate-service` | profiles (Профиль/резюме, PDF); roles (кандидат) |
| `fsp-service` | profiles (Связь с ФСП, Достижения); acceptance |
| `assessment-service` | testing.md; roles (кандидат); overview |
| `selection-service` | selection.md; overview; roles (работодатель); evaluation #1 |
| `employer-service` | profiles (Компания, Потребность); roles (работодатель) |
| `outreach-service` | roles (Выход/пр.1-3); selection (Механика); overview (LHH); evaluation #6 |
| `notification-service` | roles пр.4 |
| `api-gateway` | nonfunctional (Архитектура, Документирование API) |
| `frontend` (SPA) | nonfunctional; evaluation #3 |
| compose / CI | nonfunctional (Контейнеризация, Качество кода, Открытый исходный код); acceptance |
| post-MVP концепция | overview/acceptance (фейк-вакансии, ATS) - концепция в `docs/mvp_scope.md §4`; только документ, без кода |

Полное сопоставление требований и статусов реализации - в
`docs/final_summary.md` и `docs/mvp_scope.md`.
