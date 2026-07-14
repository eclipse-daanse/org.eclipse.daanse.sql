<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# Миграция: диалекты + API диалектов из `jdbc.db` в `sql`

**Статус:** план / спецификация (перенос кода ещё не выполнен)
**Затронутые репозитории:** `org.eclipse.daanse.sql` (цель) · `org.eclipse.daanse.jdbc.db` (источник)

Этот каталог — **мастер-план** переноса стека диалектов баз данных из репозитория
`org.eclipse.daanse.jdbc.db` в репозиторий `org.eclipse.daanse.sql`. Он описывает, *что*
переносится, *почему*, *в каком порядке* и *как* — как исполняемую рабочую инструкцию для
последующей реализации.

> Немецкая версия (первоисточник): [`../README.md`](../README.md).

---

## 1. Мотивация

Репозиторий SQL (`guard`, `deparser`, `statement`) уже **знает о диалектах**, но сам абстракцию
диалекта не определяет. Сегодня он потребляет её как **внешние Maven-артефакты** из
`org.eclipse.daanse.jdbc.db`:

- `org.eclipse.daanse.jdbc.db.dialect.api` (`Dialect`, `IdentifierQuotingPolicy`, `generator.*`)
- `org.eclipse.daanse.jdbc.db.dialect.db.common` (`AnsiDialect`)
- `org.eclipse.daanse.jdbc.db.dialect.db.h2` / `.mysql` / `.mssqlserver`
- модель метаданных/типов `…jdbc.db.api.schema.*` и `…api.type.BestFitColumnType`

**Цель:** репозиторий SQL должен **владеть полным стеком генерации SQL и диалектов**. Код диалектов,
API диалектов и необходимая им модель метаданных/типов (`api` + `record`) переезжают в `sql`.
`jdbc.db` сохраняет только чистый **движок интроспекции метаданных** (`impl`) и **слой ETL**
(`importer/csv`) и становится **односторонним нижестоящим потребителем** `sql`.

**Ограничение:** сугубо ETL-компоненты **не** переносятся в `sql`.

---

## 2. Принятые ключевые решения

| Тема | Решение | Детальный документ |
|---|---|---|
| Имена пакетов | Все переносимые модули переименовываются в `org.eclipse.daanse.sql.*` | [03](03-package-rename-map.md) |
| Общая граница API | `api` + `record` переезжают **вместе** → однонаправленная слоистость, без цикла | [02](02-dependency-and-layering.md) |
| Объём диалектов | Только **активное** подмножество в реакторе (10 модулей) | [01](01-inventory-and-scope.md) |
| Версия Java | Репозиторий SQL повышается с Java **17 → 21** | [06](06-build-and-java21.md) |

---

## 3. Обзор разделения

### Переезжает в `org.eclipse.daanse.sql` (+ переименование пакетов)

| Источник в `jdbc.db` | Целевой артефакт в `sql` | Роль |
|---|---|---|
| `api` | `org.eclipse.daanse.sql.api` | Модель метаданных/схемы/типов/SQL |
| `record` | `org.eclipse.daanse.sql.record` | Record-реализации к ней |
| `dialect/api` | `org.eclipse.daanse.sql.dialect.api` | API диалектов (`Dialect`, `DialectFactory`, `generator`, `capability`, `type`) |
| `dialect/db/common` | `org.eclipse.daanse.sql.dialect.db.common` | `AbstractJdbcDialect`, `AnsiDialect`, генераторы/квотеры Jdbc* |
| `dialect/db/test-support` | `org.eclipse.daanse.sql.dialect.db.test-support` | Тестовые помощники |
| `dialect/db/{duckdb,h2,mariadb,mssqlserver,mysql,oracle,postgresql,sqlite}` | `org.eclipse.daanse.sql.dialect.db.<db>` | 8 активных конкретных диалектов |

### Остаётся в `org.eclipse.daanse.jdbc.db` (становится потребителем `sql`)

| Модуль | Роль | Новая зависимость |
|---|---|---|
| `impl` | Движок интроспекции метаданных (`DatabaseServiceImpl`, `CachingDatabaseService`) | compile→ `sql.api`, `sql.record`, `sql.dialect.api`; test→ `sql.dialect.db.common/h2` |
| `importer` + `importer/csv` | **ETL** (импорт CSV) | compile→ `sql.api`, `sql.record`, `sql.dialect.api`; runtime→ `impl` |
| `dialect/db/*` (неактивные, ~27) | пока припаркованы | более поздняя волна миграции |

---

## 4. Целевая структура реактора в репозитории SQL

```
org.eclipse.daanse.sql/
├── pom.xml                     # корневой агрегатор: расширить <modules> (api, record, dialect); Java 21
├── api/                        # НОВОЕ (leaf)
├── record/                     # НОВОЕ (leaf)
├── dialect/                    # НОВОЕ (агрегатор, packaging=pom)
│   ├── api/                    #   leaf
│   └── db/                     #   агрегатор, packaging=pom
│       ├── common/  test-support/
│       ├── duckdb/  h2/  mariadb/  mssqlserver/
│       └── mysql/  oracle/  postgresql/  sqlite/
├── deparser/                   # существующее — зависимости диалектов извне → внутрь реактора
├── guard/                      # существующее — то же
└── statement/                  # существующее — то же
```

---

## 5. Волны миграции (порядок)

1. **Волна A — фундамент:** `api` → `record` в `sql` (переименование, запись в реактор).
2. **Волна B — ядро диалектов:** `dialect/api` → `dialect/db/common` → `test-support`.
3. **Волна C — конкретные диалекты:** 8 активных модулей БД (`mysql` перед `mariadb`).
4. **Волна D — потребители SQL:** `guard`/`deparser`/`statement` переключить с внешних зависимостей
   на внутренние модули реактора; повышение до Java 21.
5. **Волна E — демонтаж jdbc.db:** `impl`/`importer` переключить на опубликованные снапшоты `sql.*`,
   удалить переехавшие модули из реактора `jdbc.db`.

Каждая волна собирается отдельно (`mvn -q verify`) до начала следующей.

---

## 6. Детальные документы

| # | Документ | Содержание |
|---|---|---|
| 01 | [Инвентаризация и объём](01-inventory-and-scope.md) | Полный список модулей, классификация, активные/неактивные |
| 02 | [Зависимости и слоистость](02-dependency-and-layering.md) | Граф, цикл + его устранение, целевая слоистость |
| 03 | [Переименование пакетов/артефактов](03-package-rename-map.md) | Полное сопоставление старое→новое |
| 04 | [Процедура миграции](04-migration-procedure.md) | Пошагово, помодульно |
| 05 | [Перепривязка потребителей](05-consumer-rewiring.md) | Изменения pom/import в SQL и jdbc.db |
| 06 | [Сборка и Java 21](06-build-and-java21.md) | Pom-файлы реактора, версии, повышение Java |
| 07 | [Аномалии и риски](07-anomalies-and-risks.md) | Путаница redshift/snowflake, Java, поздние волны |
| 08 | [Проверка и приёмка](08-verification.md) | Команды сборки, объём тестов, DoD |
| 09 | [Минимальный срез (2-й анализ)](09-minimal-split-analysis.md) | Альтернатива полному переносу: что реально нужно `sql`, разделение ролей, 4 варианта |
| — | [**Минимальный срез — полный план**](minimal-split/README.md) | Исполняемый план разделения ролей (O1/O2) как альтернатива 01–08 |

> **Примечание:** Документы 01–08 описывают **полный перенос** (План 1). Документ **09** — это
> дополнительный *второй анализ* **минимального** среза; каталог
> [`minimal-split/`](minimal-split/README.md) содержит по нему **полный исполняемый план**
> (разделение ролей). Эти два плана — альтернативы, реализовать оба не нужно.

---

## 7. Определение готовности (набор документов)

- [ ] Все 9 документов присутствуют, взаимно связаны, согласованы.
- [ ] Таблица разделения покрывает **каждый** модуль обоих репозиториев ровно один раз.
- [ ] Сопоставление переименований полное (у каждого переносимого пакета + artifactId есть цель).
- [ ] Порядок миграции соблюдает граф зависимостей (нет опережающих ссылок).
- [ ] Устранение цикличности обосновано.

**DoD последующей реализации кода:** `mvn -q verify` зелёный в обоих репозиториях; SQL-репозиторий
собирается без внешних зависимостей `jdbc.db.dialect.*`; `jdbc.db` собирается против
опубликованных снапшотов `sql.*`; интеграционные тесты `statement/demo` (H2/MSSQL) проходят.
