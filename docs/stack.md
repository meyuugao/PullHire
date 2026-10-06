# Стек и лицензии

## Назначение документа

Документ фиксирует используемые библиотеки, компоненты и их версии, а
также лицензионную совместимость и открытость кода. Он закрывает пункт
сопроводительной документации «используемые библиотеки и компоненты с
указанием версий» и требования «Открытый исходный код» и «Качество кода»
(`docs/requirements/acceptance.md`, `docs/requirements/nonfunctional.md`).

Связанные документы:
- `docs/architecture.md` - состав сервисов (§3) и топология контейнеров (§5).
- `docs/deployment.md` - сборка, развёртывание, локальный запуск, образы.
- `docs/assumptions.md` - реестр принятых командой решений, включая выбор стека.
- `docs/api.md` - индекс эндпоинтов и документирование через OpenAPI.

Источники требований: `docs/requirements/acceptance.md`,
`docs/requirements/nonfunctional.md`.

**Вне рамок документа:** архитектурная декомпозиция, схемы данных,
механики тестирования и подбора, пошаговые команды запуска (это зоны
`docs/architecture.md`, `docs/data_model.md`, `docs/testing_model.md`,
`docs/selection.md`, `docs/deployment.md`).

## 1. Сводная таблица компонентов

| Слой | Компонент | Версия | Лицензия |
|---|---|---|---|
| Backend | Java (Temurin) | 21 (LTS) | GPLv2+CE |
| Backend | Spring Boot | 4.1.1 | Apache-2.0 |
| Backend | Gradle (wrapper) | 8.10 | Apache-2.0 |
| Backend | Spring AMQP / RabbitMQ client | из BOM Boot | Apache-2.0 |
| Backend | Flyway | из BOM Boot | Apache-2.0 |
| Backend | springdoc-openapi | 3.1.1 | Apache-2.0 |
| Backend | JJWT | 0.13.0 | Apache-2.0 |
| Backend | Argon2 / BCrypt (Spring Security Crypto) | из BOM Boot | Apache-2.0 |
| Backend | PostgreSQL JDBC | из BOM Boot | BSD-2-Clause |
| Frontend | Next.js | 15.x | MIT |
| Frontend | React | 19.x | MIT |
| Frontend | TypeScript | 5.5.x | Apache-2.0 |
| Frontend | Tailwind CSS | 3.4.x | MIT |
| Frontend | Node.js | 22 (LTS) | MIT |
| Инфра | PostgreSQL | 16 | PostgreSQL License |
| Инфра | RabbitMQ | 3.13 | MPL-2.0 |
| Инфра | Nginx | 1.27 | BSD-2-Clause |
| Инфра | Mailpit (dev) | 1.20 | MIT |

Версии backend-библиотек, помеченные «из BOM Boot», управляются
Spring Boot BOM и меняются вместе с ним; фиксируется только версия
Boot. `Spring Boot`, `Spring AMQP`, `Flyway`, `PostgreSQL JDBC` намеренно
повторяют инструментарий RoboMatch, что упрощает сопровождение и
переиспользование конвенций. Полная карта компонентов и топология - в
`docs/architecture.md §3`, `§5`.

## 2. Backend: библиотеки и версии

Каждый бизнес-сервис - отдельное приложение на Java 21 + Spring Boot,
собираемое Gradle. Общие версии вынесены в корневой
`backend/build.gradle.kts` (platform/BOM), версии сервисов наследуются.

| Библиотека | Версия | Назначение |
|---|---|---|
| Spring Boot | 4.1.1 | Базовая платформа сервисов |
| Java (Temurin JDK/JRE) | 21 | Язык и рантайм |
| Gradle (wrapper) | 8.10 | Сборка, multi-project |
| springdoc-openapi | 3.1.1 | Генерация OpenAPI/Swagger |
| JJWT | 0.13.0 | JWT RS256, JWKS |
| Spring Security Crypto | из BOM Boot | Argon2id / BCrypt |
| Spring AMQP | из BOM Boot | RabbitMQ, события |
| Flyway | из BOM Boot | Миграции в схеме сервиса |
| PostgreSQL JDBC | из BOM Boot | Доступ к БД |
| Jackson | из BOM Boot | JSON / конверт событий |

Решения по backend-стеку:
- JWT подписывается RS256; приватный ключ не хранится в репозитории
  (см. `docs/deployment.md §4`), публичный публикуется как JWKS.
- Пароли хешируются Argon2id (при недоступности - BCrypt); выбор
  алгоритма и параметры - в `docs/assumptions.md`.
- Миграции Flyway применяются per-schema; кросс-схемный доступ запрещён
  конвенцией (`docs/architecture.md §5.2`).
- Документирование API через springdoc-openapi; сводный индекс `/docs`
  собирается на шлюзе. Формат спецификаций - в `docs/api.md`.

## 3. Frontend: библиотеки и версии

Frontend - SPA на React/Next.js для обеих ролей, со стилями Tailwind CSS.
Стек выбран командой; UI-конвенции наследуются от RoboMatch.

| Библиотека | Версия | Назначение |
|---|---|---|
| Next.js | 15.x | SPA-фреймворк, маршрутизация |
| React | 19.x | UI-библиотека |
| TypeScript | 5.5.x | Типизация |
| Tailwind CSS | 3.4.x | Стили |
| Node.js | 22 (LTS) | Сборка и рантайм |
| OpenAPI-клиент | генерируется | Типы клиентов к API |

Клиенты к API строятся из сгенерированных OpenAPI-спецификаций
(`docs/architecture.md §4.4`). Прямых обращений к сервисам минуя
`api-gateway` нет. Сценарии ролей - в `docs/user_flow.md`.

Версии frontend зафиксированы по действующим LTS/стабильным линиям:
Node.js 22 (LTS) вместо выбывшего Node.js 20 (EOL с апреля 2026);
Next.js 15.x и React 19.x выбраны согласованной парой, совместимой с
Node.js 22.

## 4. Инфраструктура: образы и версии

| Компонент | Образ | Версия | Роль |
|---|---|---|---|
| PostgreSQL | `postgres:16-alpine` | 16 | Единый инстанс, схема и роль на сервис |
| RabbitMQ | `rabbitmq:3.13-management-alpine` | 3.13 | Брокер событий + Management UI |
| Nginx | `nginx:1.27-alpine` | 1.27 | Шлюз и отдача SPA |
| Mailpit (dev) | `axllent/mailpit:v1.20` | 1.20 | Перехват писем в разработке |
| Runtime JRE | `eclipse-temurin:21-jre` | 21 | Runtime Spring Boot JAR |
| Build JDK | `eclipse-temurin:21-jdk` | 21 | Сборка backend |
| Build Node | `node:22-alpine` | 22 | Сборка frontend |

Состав контейнеров, healthcheck и лимиты памяти - в
`docs/architecture.md §5`. Команды сборки и запуска, URL и порты - в
`docs/deployment.md §2`–`§3`. Целевой суммарный footprint ≈ 2.0–2.2 ГБ
RAM (`docs/requirements/nonfunctional.md`, «Аппаратные требования»).

## 5. Лицензионная совместимость

Требование: открытое ПО под MIT, Apache-2.0, BSD и аналогичными
лицензиями либо ПО из реестра отечественного ПО
(`docs/requirements/nonfunctional.md`, «Открытый исходный код»).

| Компонент | Лицензия | Совместимость |
|---|---|---|
| Spring Boot / Spring/ AMQP / Security | Apache-2.0 | Да |
| Gradle | Apache-2.0 | Да |
| springdoc-openapi, JJWT | Apache-2.0 | Да |
| Flyway (Community) | Apache-2.0 | Да |
| PostgreSQL | PostgreSQL License (BSD-подобная) | Да |
| PostgreSQL JDBC | BSD-2-Clause | Да |
| Next.js, React, Tailwind, Node.js | MIT | Да |
| Nginx | BSD-2-Clause | Да |
| Mailpit | MIT | Да |
| RabbitMQ | MPL-2.0 | Да (разрешённая) |
| Java (Temurin) | GPLv2+CE | Да (classpath exception) |

Все прямые зависимости распространяются под OSI-совместимыми
разрешительными лицензиями и не конфликтуют с закрытым или открытым
распространением решения. Использование ПО из реестра отечественного ПО
не требуется: требование закрывается открытыми лицензиями. При
добавлении библиотеки проверяется лицензия и фиксируется в этом разделе;
спорные случаи выносятся в `docs/assumptions.md`.

## 6. Открытость кода

Решение поставляется открытым и не зашифрованным:

- исходный код всех собственных компонентов (Java, TypeScript,
  конфигурации) находится в репозитории в виде исходников;
- обфускация не применяется; посторонние изменения исходников
  запрещены;
- стандартная сборка фреймворков (компиляция Java, бандлинг фронтенда
  с минификацией статики) не является обфускацией - исходники остаются
  доступными и воспроизводимо собираются по `docs/deployment.md`;
- сторонние зависимости и образы контейнеров используются как есть;
  их исходный код публичен, лицензии перечислены в §5;
- секреты и токены в репозиторий не попадают (`docs/deployment.md §4`).
