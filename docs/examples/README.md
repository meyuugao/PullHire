# Примеры артефактов

## Назначение документа

Индекс генерируемых артефактов пакета PullHire: что лежит в `docs/examples/`, чем сгенерировано и как воспроизвести. Служит доказательством двух пунктов: API описан через OpenAPI (springdoc) и сопроводительная документация экспортируется в doc/pdf (`docs/requirements/nonfunctional.md`, «Документирование API»; `docs/requirements/acceptance.md`, «Сопроводительная документация»).

Связанные документы: `docs/api.md` (индекс эндпоинтов), `docs/deployment.md` (сборка и запуск), `docs/final_summary.md §4` (проверка и экспорт пакета).

Вне рамок: исходные тексты документации (`docs/*.md`) и их правка; здесь только индекс и способ генерации артефактов.

## 1. Состав каталога

| Путь | Что | Чем генерируется |
|---|---|---|
| `docs/examples/openapi/` | Спецификации OpenAPI 3 по каждому сервису | springdoc-openapi, эндпоинт `/v3/api-docs` каждого сервиса |
| `docs/examples/package/` | Экспорт пакета документации в doc/pdf | конвейер упаковки `docs/*.md` → Markdown-бандл → HTML → PDF |
| `docs/examples/README.md` | Настоящий индекс | вручную |

Каталоги `openapi/` и `package/` зарезервированы под сгенерированные файлы (хранят `.gitkeep`); содержимое генерируется на этапе сборки и в репозиторий не коммитится автоматически.

## 2. OpenAPI (`openapi/`)

- Каждый backend-сервис авто-генерирует спецификацию через springdoc (`docs/api.md`, `docs/stack.md §2`).
- Спецификации собираются per-service в файлы вида `openapi/<service>.json`; агрегатор `docs/api.md §1` служит точкой входа.
- Базовые спецификации коммитятся намеренно и обновляются через CI-гейт `client-regen` (перегенерация клиентов + `git diff --exit-code`); вручную — п. 2 `docs/architecture.md §4`.
- API-шлюз публикует индекс документации по пути `/docs` (`docs/architecture.md §3.2`).
- Ключ оценивания и чувствительные данные в спецификацию не попадают.

## 3. Экспорт пакета (`package/`)

- Пакет `docs/*.md` экспортируется в единый doc/pdf; итоговый файл размещается в `docs/examples/package/`.
- Экспорт закрывает требование «ссылка на сопроводительную документацию в формате doc или pdf» (`docs/requirements/acceptance.md`).
- Состав пакета и карта «тема → документ» — в `docs/final_summary.md §5`.
- Артефакт генерируется из authored-документов; `docs/requirements/` и `docs/source/` в пакет не входят.

### 3.1. Сгенерированные артефакты

| Файл | Что это | Чем генерируется |
|---|---|---|
| `docs/examples/package/PullHire-docs.md` | Единый Markdown-бандл: титул, оглавление, 17 authored-документов в порядке чтения (архитектура → … → backlog → worklog), разделённых `---`; заголовки документов смещены на уровень H2 | `docs/examples/package/build-docs.ps1` (конкатенация UTF-8) |
| `docs/examples/package/PullHire-docs.html` | Печатная HTML-версия бандла: A4, встроенный print CSS, якоря оглавления | `npx --yes marked` |
| `docs/examples/package/PullHire-docs.pdf` | PDF-экспорт пакета (основной артефакт «ссылка на документацию») | headless Microsoft Edge, `--print-to-pdf` |
| `docs/examples/package/build-docs.ps1` | Скрипт сборки (бандл → HTML → PDF) | вручную |

Бандл содержит 17 authored-документов (16 разделителей `---`); `docs/requirements/` и `docs/source/` в него не входят. Сгенерированные файлы — build-артефакты и в репозиторий автоматически не коммитятся (каталог хранит `.gitkeep`).

## 4. Как воспроизвести

1. Поднять стек по `docs/deployment.md §3` (сборка и локальный запуск).
2. Снять спецификации: запросить `/v3/api-docs` у каждого сервиса → сохранить в `docs/examples/openapi/<service>.json`.
3. Собрать экспорт пакета: запустить `powershell -ExecutionPolicy Bypass -File docs\examples\package\build-docs.ps1` (нужны Node/`npx` и Microsoft Edge). Скрипт собирает `PullHire-docs.md`, рендерит `PullHire-docs.html` через `npx --yes marked` и печатает `PullHire-docs.pdf` через `msedge --headless --print-to-pdf`.
4. Сверить состав и перекрёстные ссылки с `docs/final_summary.md §5`; типовые проблемы запуска — `docs/deployment.md §5`.
