# Итоговая сводка

## Назначение документа

Краткая сводка собранного пакета документации PullHire: что поставлено относительно ТЗ, где зафиксированы решения, какие границы и упрощения приняты осознанно, как проверен и экспортирован пакет. Документ опирается на весь authored-пакет `docs/` и **не вводит новых решений**.

Связанные документы: `docs/mvp_scope.md` (границы), `docs/assumptions.md` (реестр решений), `docs/backlog/closed_questions.md` (история закрытия), `docs/examples/README.md` (артефакты), `docs/worklog.md` (журнал работ).

Источники требований: `docs/requirements/acceptance.md`, `docs/requirements/evaluation.md`, `docs/requirements/nonfunctional.md`.

Вне рамок: пересказ содержания профильных документов; здесь только сводка и карта ссылок.

## 1. Поставлено vs ТЗ

| Критерий ТЗ | Что сделано | Где доказательство |
|---|---|---|
| Новая механика подбора (35%) | LHH-механика: категория = специализация × грейд, внутрикатегорийное ранжирование, подборка по потребности с объяснимостью, поиск/фильтры, приглашение с обязательной зарплатой, раскрытие контактов после принятия | `docs/selection.md`, `docs/user_flow.md §3`, `docs/data_model.md §7`, `§9` |
| Механика тестирования, валидация, категоризация (25%) | Обязательный конвейер «опрос → заявленный грейд → тест → категория»; calibrated template pools + IRT-lite (1PL) + якоря + cut-score; анти-утечка; запрет принудительного понижения; cooldown | `docs/testing_model.md`, `docs/validation.md`, `docs/data_model.md §6.1` |
| Удобство и интуитивность интерфейса (10%) | Сквозной путь в обоих кабинетах, роль-скоупная маршрутизация, демонстрационный сценарий | `docs/user_flow.md`, `docs/deployment.md §6` |
| Техническая проработка решения (7%) | Микросервисы (8 сервисов + gateway + SPA), REST + RabbitMQ, TOPOLOGY A и путь A→B, security/152-ФЗ, OpenAPI, CI | `docs/architecture.md`, `docs/stack.md`, `docs/deployment.md` |
| Полнота реализации и сопроводительная документация (6%) | Пакет `docs/` закрывает все пункты сопроводительной документации; обработка «нет истории ФСП» обязательна и нейтральна | `docs/` (весь пакет), `docs/fsp_integration.md §6`, `docs/mvp_scope.md` |
| Дополнительный функционал (7%) | Регулярные задания и классический поток вакансий/откликов `[opt]` — спроектированы полностью (реализация второй очередью); защита от фиктивных вакансий и интеграция с ATS — только концепция (`docs/mvp_scope.md §4`), без реализации | `docs/periodic_tasks.md`, `docs/mvp_scope.md §3`, `§4` |
| Презентация и питч-сессия (10%) | Демо-сценарий, инструкция по локальному запуску, видео-фолбэк, экспортируемый пакет doc/pdf | `docs/user_flow.md §4`, `docs/deployment.md §6`, `docs/examples/` |
| Сопроводительная документация: архитектура | Функциональная и компонентная архитектура, схема сервисов, взаимодействие | `docs/architecture.md` |
| Сопроводительная документация: механики и схема ФСП | Тестирование, подбор, схема интеграции с ФСП, API, библиотеки с версиями, инструкция запуска | `docs/testing_model.md`, `docs/selection.md`, `docs/fsp_integration.md`, `docs/api.md`, `docs/stack.md`, `docs/deployment.md` |
| Ссылка на документацию в формате doc/pdf | Экспорт пакета в единый doc/pdf; артефакт — в `docs/examples/package/` | `docs/examples/README.md §3`, `docs/examples/package/` |

## 2. Карта решений

| Решение | Где зафиксировано |
|---|---|
| Cooldown = 1 месяц | `docs/assumptions.md §2`; `docs/backlog/closed_questions.md` Q-3 |
| Периодические задания: `n = 7`, синхронные окна, opt-in, freshness display-only | `docs/assumptions.md §3`; `docs/backlog/closed_questions.md` Q-2 |
| Каталог специализаций и грейдов | `docs/assumptions.md §4` |
| Состав полей ФСП, Keycloak-совместимость как ориентир | `docs/assumptions.md §5`; `docs/backlog/closed_questions.md` Q-1, Q-4 |
| Форматы заданий грейд-теста (только автопроверяемые) | `docs/assumptions.md §6`; `docs/backlog/closed_questions.md` Q-2 |
| Синтетические данные и идемпотентный seed | `docs/assumptions.md §7`; `docs/backlog/closed_questions.md` Q-4 |
| Топология БД: TOPOLOGY A и путь A → B | `docs/assumptions.md §8`; `docs/architecture.md §5.2`, `§5.4` |
| Decision D: свободные ответы вне MVP-грейд-теста | `docs/assumptions.md §9`; `docs/mvp_scope.md §2.4`, `§4`; `docs/backlog/closed_questions.md` Q-2 |
| Политика удаления аккаунта (soft-delete + анонимизация логов) | `docs/assumptions.md §10` |
| Event-contract: republish `assessment.category_assigned` | `docs/backlog/closed_questions.md` TD-1; `docs/testing_model.md §7.1`, `§8` |
| Обязательный scope по специализации | `docs/backlog/closed_questions.md` TD-2; `docs/periodic_tasks.md §4` |
| Хранение опроса и seed-полей | `docs/backlog/closed_questions.md` TD-3; `docs/data_model.md §6.1` |
| Snapshot `employer_contact` без cross-schema join | `docs/backlog/closed_questions.md` TD-4; `docs/data_model.md §9.1` |
| Exposure caps на уровне семейства / задания | `docs/backlog/closed_questions.md` TD-5; `docs/testing_model.md §9.1` |
| Санкционированное отклонение `testing_model §9.5` | `docs/backlog/closed_questions.md` TD-6; `docs/worklog.md` (Phase 5) |
| Микросервисы, Java 21 + Spring Boot, React/Next.js, RabbitMQ, Flyway, springdoc | `docs/architecture.md §3`, `§5`; `docs/stack.md` |

## 3. Границы и осознанные упрощения

- **Свободный ответ вне MVP-грейд-теста** (Decision D): MVP-форма — только автопроверяемые форматы; «предложить подход» живёт в регулярных заданиях, оценивается работодателем (`docs/assumptions.md §9`).
- **Регулярные задания работодателей** — `[opt]`, optional-MVP: спроектированы полностью, реализуются второй очередью (`docs/periodic_tasks.md`, `docs/mvp_scope.md §3.1`).
- **Классический поток вакансий и откликов** — `[opt]`, свёрнутый модуль в `outreach-service` с документированным путём выделения (`docs/mvp_scope.md §3.2`).
- **ML/NLP-разбор резюме** — rule-based fallback в MVP; ML/NLP-воркер помечен `[ext]` (`docs/mvp_scope.md §3.4`).
- **Мобильная адаптивность** — `[opt]`; целевой минимум — десктопные браузеры (`docs/mvp_scope.md §3.3`).
- **Защита от фиктивных вакансий и ATS** — только концепция и архитектурный подход, без реализации (`docs/mvp_scope.md §4`).
- **Реальный API реестра ФСП** — mock-адаптер и синтетические данные; модель устойчива к неизвестному контракту (`docs/fsp_integration.md §4`, `§7`).
- **Калибровка тестов** — IRT-lite (1PL), сложности провизорны до накопления данных; MST — fallback (`docs/testing_model.md §3.5`, `§5.3`).
- **Топология данных** — одна PostgreSQL (TOPOLOGY A) со схемой и ролью на сервис; переход к физическому разделению — конфигурационный (`docs/architecture.md §5.2`).
- **Инфраструктурные упрощения MVP** — Compose DNS вместо discovery, Nginx вместо BFF, без K8s/Consul/распределённого трейсинга; Prometheus/Grafana опциональны (`docs/architecture.md §4.1`, `§7`).
- **Оценивание LLM** — только облачный LLM `[ext]` по фиксированной рубрике; локальный хостинг исключён требованиями к железу (`docs/assumptions.md §9`).

## 4. Проверка и экспорт пакета

**Проверка пакета.** Каждый authored-документ `docs/` проверяется на соблюдение конвенций: один `#` H1, первый раздел `## Назначение документа`, нумерованные `## N.` / `### N.M.` с точкой, inline-ссылки вида `docs/x.md §N`, бэктик-пути без `[text](path)`-ссылок. Источники `docs/requirements/` и `docs/source/` остаются read-only и в пакет не входят.

**Экспорт.** Пакет `docs/*.md` экспортируется в единый формат doc/pdf; итоговый артефакт размещается в `docs/examples/package/`. Собранный экспорт — `docs/examples/package/PullHire-docs.pdf` (плюс исходные `PullHire-docs.md` и `PullHire-docs.html`), генерируется скриптом `docs/examples/package/build-docs.ps1` (`docs/*.md` → бандл → `npx marked` → headless Edge `--print-to-pdf`). Структура репозитория и требование «ссылка на сопроводительную документацию в формате doc или pdf» закрываются этим артефактом (`docs/requirements/acceptance.md`, «Сопроводительная документация»).

**Артефакты.** `docs/examples/openapi/` — per-service OpenAPI, генерируется springdoc на этапе сборки (резервируется как build-артефакт, не поддерживается вручную); `docs/examples/package/PullHire-docs.pdf` — doc/pdf-экспорт пакета, создаётся при сборке (сгенерированные файлы в репозиторий автоматически не коммитятся). Индекс артефактов, что их генерирует и как воспроизвести — в `docs/examples/README.md`.

## 5. Подробнее

| Тема | Документ |
|---|---|
| Архитектура, сервисы, топология, безопасность | `docs/architecture.md` |
| ER-модель, сущности, поля, перечисления | `docs/data_model.md` |
| Механика грейд-тестирования и присвоение категории | `docs/testing_model.md` |
| Регулярные задания работодателей | `docs/periodic_tasks.md` |
| Категории, ранжирование, подборка, LHH-выход на контакт | `docs/selection.md` |
| Процедура валидации решения и результаты | `docs/validation.md` |
| Схема интеграции с реестром ФСП | `docs/fsp_integration.md` |
| Индекс API и конвенции | `docs/api.md` |
| Библиотеки, компоненты, лицензии, открытость кода | `docs/stack.md` |
| Сборка, развёртывание, локальный запуск | `docs/deployment.md` |
| Границы MVP и опциональные срезы | `docs/mvp_scope.md` |
| Сквозные пользовательские сценарии и демо-путь | `docs/user_flow.md` |
| Термины | `docs/glossary.md` |
| Реестр допущений и решений | `docs/assumptions.md` |
| Итоговая сводка | `docs/final_summary.md` |
| Журнал закрытых вопросов и техдолга | `docs/backlog/closed_questions.md` |
| Примеры артефактов (OpenAPI, экспорт пакета) | `docs/examples/README.md` |
| Журнал сборки пакета | `docs/worklog.md` |
