# Журнал закрытых вопросов

## Назначение документа

Единый журнал истории вопросов, поднимавшихся при сборке пакета PullHire, и их закрытия. Дополняет реестр `../assumptions.md` (зафиксированные решения) и не дублирует его: здесь — вопрос, откуда он возник, требуемое действие и итоговый статус. Нумерация сквозная по двум пространствам: `Q-N` (вопросы и ответы) и `TD-N` (технический долг).

Связанные документы: `../assumptions.md`, `../testing_model.md`, `../periodic_tasks.md`, `../fsp_integration.md`, `../mvp_scope.md`, `../worklog.md`.

Источники требований: `docs/requirements/acceptance.md`, `docs/requirements/testing.md`, `docs/requirements/overview.md`, `docs/requirements/profiles.md`, `docs/requirements/nonfunctional.md`.

Вне рамок: сами принятые решения как таковые (они в `../assumptions.md`); здесь — только динамика закрытия.

## 1. Формат записи

Каждая запись — подраздел `### Q-N.` или `### TD-N.` со полями:

- `Что` — суть вопроса;
- `Откуда` — документ, где вопрос возник;
- `Действие` — что требовалось или было сделано;
- `Статус` — один из вердиктов: `решён командой`, `закрыт ответом из требований`, `осознанная граница`, `вне объёма`;
- `Резолюция` — итог со ссылкой на `../x.md` или `docs/requirements/x.md`.

Ссылки из `docs/backlog/` на authored-документы — `../x.md`; на источники требований — `docs/requirements/x.md`.

## 2. Группа Q. Вопросы и ответы (Q-N)

### Q-1. Совместимость с Keycloak

- Что: обязательна ли совместимость авторизации с Keycloak (ФСП ID «в дальнейшем» будет работать на базе Keycloak).
- Откуда: `docs/requirements/nonfunctional.md` («Аутентификация»); `docs/requirements/profiles.md` («Связь с ID участника ФСП»).
- Действие: принять решение о способе аутентификации; при выборе Keycloak-совместимого пути обеспечить форму claim-ов и JWKS.
- Статус: закрыт ответом из требований.
- Резолюция: совместимость — **ориентир, не требование**; выбран JWT RS256 с Keycloak-совместимыми claim-ами (`sub`, `realm_access.roles`) и `/.well-known/jwks.json`; см. `../fsp_integration.md §2`, `../architecture.md §6.1`.

### Q-2. Формат заданий

- Что: каким должен быть формат заданий грейд-теста.
- Откуда: `docs/requirements/testing.md` («Формат заданий»); `docs/requirements/acceptance.md` («Элементы, прорабатываемые самостоятельно»).
- Действие: выбрать форматы и механику проверки.
- Статус: решён командой.
- Резолюция: форматы выбирает команда — закрытые вопросы, короткий ответ, задачи с кодом; свободный ответ вне MVP-грейд-теста (Decision D). См. `../assumptions.md §6`, `§9`; `../testing_model.md §3.1`.

### Q-3. Интервал смены грейда

- Что: с какой частотой разрешать смену грейда.
- Откуда: `docs/requirements/testing.md` («Ограничение смены грейда»); `docs/requirements/overview.md` («Постановка задачи»).
- Действие: задать интервал cooldown.
- Статус: решён командой.
- Резолюция: принят **1 месяц**; см. `../assumptions.md §2`; `../testing_model.md §7.3`.

### Q-4. API и структура ФСП

- Что: какие данные реестра ФСП получать и как отображать.
- Откуда: `docs/requirements/acceptance.md` («Ресурсы организатора»); `docs/requirements/profiles.md`.
- Действие: определить модель и источник данных при отсутствии предоставленного API.
- Статус: закрыт ответом из требований.
- Резолюция: API и структура данных **не предоставлены**; принята собственная модель и mock/синтетика; см. `../assumptions.md §5`, `§7`; `../fsp_integration.md §3–§4`.

### OQ-7. Временный канал auth→notification (S1)

- Что: канал `user.registered` (exchange/queue/routing-key) выбран локально в S1 при отсутствии outbox и общей библиотеки.
- Откуда: `../worklog.md` (S1); `TODO.md` OQ-7.
- Действие: заменить локальную публикацию/потребление общим publisher/consumer из `backend/event-lib` с transactional outbox.
- Статус: решён командой.
- Резолюция: S2 — auth публикует через `OutboxEventPublisher`/`OutboxRelay` в единственный exchange `pullhire.events`, notification потребляет через типизованный консьюмер `event-lib` с `EventIdDedup`. См. `../worklog.md` (S2 foundation, S2 notification lane).

### OQ-8. Риск потери `user.registered` при недоступном брокере

- Что: в S1 прямой publish без outbox/ретраев — при недоступном RabbitMQ событие терялось.
- Откуда: `../worklog.md` (S1); `TODO.md` OQ-8.
- Действие: гарантировать, что недоступность брокера не теряет событие.
- Статус: решён командой.
- Резолюция: transactional outbox (`auth.outbox_events` + `OutboxRelay` с publisher-confirm перед `published_at`) и bounded retry/DLQ у консьюмера; остановка брокера не теряет outbox-событие (`scripts/smoke-compose.sh`). См. `../worklog.md` (S2 foundation).

### OQ-13. Сырой `verification_token` в payload `user.registered`

- Что: одноразовый `verification_token` передавался через RabbitMQ, ссылку строил notification.
- Откуда: `../worklog.md` (S1); `TODO.md` OQ-13.
- Действие: убрать сырой токен из события.
- Статус: решён командой.
- Резолюция: auth рендерит `verification_url` в той же транзакции; payload несёт `verification_url`, поле `verification_token` удалено из `contracts/events/user.registered.schema.json`. См. `../worklog.md` (S2 foundation).

### OQ-3. Snapshot `employer_contact` в приглашении (S8)

- Что: какой типовой snapshot контакта работодателя несёт приглашение без cross-schema join.
- Откуда: `TODO.md:80–83`; `../data_model.md §9.1` (TD-4).
- Действие: зафиксировать источник и форму snapshot на старте S8.
- Статус: решён командой.
- Резолюция: snapshot заполняется из тела POST /invitations, хранится как есть `{"contact_email": string|null, "contact_phone": string|null, "website": string|null}`; минимум 1 непустой канал, иначе 400 validation_error; без sync-вызова employer-service (TD-4 соблюдён, снимок фиксируется на момент создания). См. `../worklog.md` (S8 Phase 2, 2026-10-10).

### OQ-22 residual. Нет release при `show_contacts=false` (S8)

- Что: кандидат с `show_contacts=false` на accept не получит release и повторного триггера.
- Откуда: S3 (OQ-22); `../worklog.md` (S8 Phase 2).
- Действие: зафиксировать остаточную семантику на S8.
- Статус: осознанная граница.
- Резолюция: приглашение для такого кандидата завершается без раскрытия контактов; повторный триггер не предусмотрен. См. `../worklog.md` (S8 Phase 2, 2026-10-10).

## 3. Группа TD. Технический долг (TD-N)

### TD-1. Event-contract: republish `category_assigned`

- Что: обновлять категорию только при первичном прохождении или при любой завершённой попытке.
- Откуда: `../testing_model.md §7`; `../architecture.md §4.2–4.3`.
- Действие: закрепить контракт событий между `assessment-service` и `selection-service`.
- Статус: решён командой.
- Резолюция: любая завершённая попытка (включая up/down и повторы) перепубликует `assessment.category_assigned`; `assessment.grade_changed` — audit/notification only. См. `../testing_model.md §7.1`, `§8`.

### TD-2. Обязательный scope по специализации

- Что: обязателен ли фильтр по специализации в розыгрыше регулярных заданий.
- Откуда: `../periodic_tasks.md §4`, `§5.1`.
- Действие: закрепить обязательность фильтра и отделить его от опционального фильтра по грейду.
- Статус: решён командой.
- Резолюция: совпадение специализации — **обязательный** фильтр выдачи; фильтр по грейду — `[ext]`. См. `../periodic_tasks.md §4` (C3).

### TD-3. Хранение опроса

- Что: где хранить ответы опроса и заявленные измерения.
- Откуда: `../testing_model.md §2.1`; `../data_model.md §6.1`.
- Действие: зафиксировать поля хранения.
- Статус: решён командой.
- Резолюция: `assessment.test_sessions.survey_answers` + `declared_dimensions`; отрасль — `candidate.profiles.industry_code` (`../data_model.md §4.1`); публичная карточка отдаёт industry/специализацию/грейд. Form-level `test_sessions.form_seed`, per-item `attempts.variant_seed`. См. `../data_model.md §4.1` (`industry_code`), `§6.1`.

### TD-4. Snapshot `employer_contact`

- Что: как приглашение получает контакт работодателя без cross-schema join.
- Откуда: `../data_model.md §9.1`; `../architecture.md §5.2`.
- Действие: выбрать способ доставки контакта в схему `outreach`.
- Статус: решён командой.
- Резолюция: приглашение несёт snapshot `employer_contact` (способ связи); `applications` несёт `released_contact` / `contact_released_at`; cross-schema join не используется. См. `../data_model.md §9.1`, `§9.3`.

### TD-5. Exposure caps на уровне семейства / задания

- Что: на каком уровне ограничивать число показов заданий банка.
- Откуда: `../testing_model.md §3.4`, `§9.1`; `../data_model.md §6.1`.
- Действие: определить уровень лимитов и поведение при исчерпании.
- Статус: решён командой.
- Резолюция: caps на уровне `template_family` (NULLable, у якорей отсутствует, с CHECK) и задания; при исчерпании — вывод из ротации. См. `../testing_model.md §3.4`, `§9.1`; `../data_model.md §6.1`.

### TD-6. Отклонение структуры `testing_model §9.5`

- Что: подраздел `### 9.5. Антипаттерны` в `../testing_model.md` не следует шаблону остальных подразделов.
- Откуда: `../worklog.md` (Phase 5).
- Действие: зафиксировать отклонение или привести раздел к шаблону.
- Статус: осознанная граница (sanctioned).
- Резолюция: отклонение **санкционировано** и зафиксировано в `../worklog.md` (Phase 5); раздел оставлен как содержательный перечень антипаттернов.

### TD-7. Пакет правок финального ревью

- Что: результаты финального сквозного ревью пакета документации — набор точечных правок, выявленных проверкой согласованности.
- Откуда: `../worklog.md` (финальное ревью).
- Действие: внести правки в затронутые документы, не меняя структуру и границы MVP.
- Статус: решён командой.
- Резолюция: (1) в `../mvp_scope.md §4` зафиксирован **blocker** — концепт «фиктивная вакансия/ATS»; (2) переформулирован event-contract (`../architecture.md §4.2`); (3) исправлена ссылка на deployment-документ; (4) свежесть профиля добавлена в карточку; (5) закреплено fallback-якорение заданий (`../assumptions.md §11`; `../data_model.md §6.2`); (6) soft-delete аккаунта отражён в схеме через `auth.users.deleted_at` (`../data_model.md §3.1`; `../assumptions.md §10`).

### TD-8. Артефакт экспорта doc/pdf

- Что: в пакете отсутствует артефакт экспорта документации в формате doc/pdf.
- Откуда: `../worklog.md` (финальное ревью).
- Действие: определить источник и способ генерации экспорта.
- Статус: осознанная граница.
- Резолюция: экспорт `doc/pdf` — артефакт сборки, не authored-документ; зафиксирован как техдолг и не блокирует MVP.

### TD-9. Калибровка assessment: автовыдача категории недостижима (S9)

- Что: банк `assessment_seed.sql` — 4 вопроса на специализацию (1 anchor + 3 template families); `GradeBandClassifier` требует SE≤0.3 и CI внутри одного бэнда; IRT-скоринг даёт ≤0.25 информации на вопрос — даже 30 идеальных ответов дают SE≥0.34. Сессия всегда исчерпывает банк (≈4 ответа, SE≈0.78) и уходит в `needs_review` без категории; `selection.candidate_categories` не пополняется, POST /invitations всегда 400.
- Откуда: S9 correlation-прогон (`scripts/smoke-correlation.sh`); `../testing_model.md §5–§6`; OQ-1 (числовые параметры — illustrative placeholders).
- Действие: откалибровать связку сид/пороги (наполнение банка vs `min-items`/`se-threshold`/`ci-confidence`) либо зафиксировать ручной грейд как штатный путь демо.
- Статус: открыт, зона S13/OQ-1.
- Резолюция: нет. Временно: outreach-нога correlation-цепочки пропущена; критерий 5 S9 закрыт на цепочке auth→candidate→assessment→employer→selection→notification.

### D1. Non-service Gradle-модуль `backend/event-lib`

- Что: `../deployment.md §2` описывает backend как «один корневой `backend/build.gradle.kts` и модуль на сервис»; S2 добавляет модуль `backend/event-lib`, не являющийся сервисом.
- Откуда: `../deployment.md §2`; `../worklog.md` (S2).
- Действие: зафиксировать отклонение от буквальной формулировки.
- Статус: осознанная граница (sanctioned).
- Резолюция: добавление `backend/event-lib` санкционировано выходом `TODO.md` S2 «библиотека publisher/consumer для всех сервисов». См. `../worklog.md` (S2 foundation).

### D4. Outbox для всех событий

- Что: `../architecture.md §4.3` применяет transactional outbox только к критичным событиям (`assessment.category_assigned`, жизненный цикл приглашения).
- Откуда: `../architecture.md §4.3`; `../worklog.md` (S2).
- Действие: определить охват outbox.
- Статус: решён командой.
- Резолюция: outbox расширен на **все** события (утверждённое решение S2), а не только на критичные. См. `../worklog.md` (S2 foundation).

### D5. Остаточный риск: претензия невладеемого `fsp_id` (S4)

- Что: привязка `fsp_id` не подтверждает владение им — любой аутентифицированный кандидат может связать ещё никем не занятый `fsp_id` (mock-вход через FSP ID имитируется, боевого Keycloak нет).
- Откуда: `../fsp_integration.md §2` (имитация входа через FSP ID); S4 (`fsp-service`).
- Действие: зафиксировать принятый риск до появления реального identity provider.
- Статус: осознанная граница.
- Резолюция: риск принят для MVP. `UNIQUE (fsp_id)` (`../data_model.md §5.1`) не позволяет двум кандидатам владеть одним `fsp_id`, но первый претендент закрепляет свободный id за собой; демо опирается на синтетический seed (`../fsp_integration.md §4`). Боевой путь (`../fsp_integration.md §7`) заменит mock-вход подтверждением владения через Keycloak/ФСП.

## 4. Хронология закрытия

| Дата | Этап | Закрыто |
|---|---|---|
| 2026-10-05 | docs/requirements | Baseline источников (verbatim-цитаты ТЗ) |
| 2026-10-06 | Phase 5: core docs | Q-3 (cooldown = 1 месяц); TD-1 (event-contract), TD-3 (хранение опроса), TD-5 (exposure caps); TD-6 (санкционированное отклонение `testing_model §9.5`) |
| 2026-10-06 | Phase 6: structural docs + examples/ | Q-1 (Keycloak), Q-2 (форматы заданий), Q-4 (API/структура ФСП); TD-2 (scope по специализации), TD-4 (snapshot `employer_contact`) |
| 2026-10-06 | Финальное ревью | TD-3 (ссылка `data_model §4.1`); TD-7 (пакет правок финального ревью), TD-8 (артефакт экспорта doc/pdf) |
| 2026-10-07 | S2: event-lib + outbox + наблюдаемость | OQ-7, OQ-8 (event-lib publisher/consumer + outbox), OQ-13 (`verification_url`); D1 (модуль `backend/event-lib`), D4 (outbox для всех событий) |
| 2026-10-08 | S4: fsp-service | D5 (принятый остаточный риск претензии невладеемого `fsp_id`) |
| 2026-10-10 | S8: outreach-service | OQ-3 (employer_contact snapshot из тела POST /invitations, ≥1 канал) |
