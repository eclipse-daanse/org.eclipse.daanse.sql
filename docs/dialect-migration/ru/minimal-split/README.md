<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# Полный план: минимальный срез (разделение ролей)

**Статус:** план / спецификация (перенос кода ещё не выполнен)
**Отношение:** реализация **варианта O1 (+ опционально O2)** из 2-го анализа
([../09-minimal-split-analysis.md](../09-minimal-split-analysis.md)). Альтернатива полному переносу
([../README.md](../README.md)).

> 🌐 Язык: [Deutsch](../../minimal-split/README.md) · **Русский** (этот файл)

Этот каталог — **исполняемый мастер-план** для *минимального* среза: вместо полного переноса
`api`+`record` (План 1) из диалектов **выносится роль выдачи метаданных**, так что ядро SQL
становится минимальным, а `record` + `api.meta` + движок интроспекции остаются в `jdbc.db`.

---

## 1. Основная идея

Конкретный диалект сегодня несёт **две роли** (обоснование:
[../09](../09-minimal-split-analysis.md)):

- **Роль A — нативный провайдер метаданных:** `Dialect extends MetadataProvider`; 6 больших диалектов
  переопределяют по 14–21 метода (`getAllIndexInfo`, `getAllPrimaryKeys`, …), которые читают
  `information_schema` и строят объекты `record.schema`. → **интроспекция, принадлежит `jdbc.db`.**
- **Роль B — генерация SQL/DDL:** кавычки, генераторы, возможности, DDL/DML из словаря `api.schema`.
  → **принадлежит `sql`.**

**Ключевой рычаг:** `DatabaseServiceImpl` (движок интроспекции) уже потребляет `MetadataProvider`
как **параметр** (`createMetaInfo(Connection, MetadataProvider)`, `getCatalogs(Connection,
MetadataProvider)` → вызывает `provider.getAllIndexInfo(...)`). Он **не** знает `Dialect`. Роли
фактически уже разделены — их сваривает лишь наследование `Dialect extends MetadataProvider`.

**План:** убрать наследование; методы роли A каждого диалекта вынести в отдельный класс-компаньон
`<Db>MetadataProvider implements MetadataProvider`, остающийся в `jdbc.db`; лёгкий диалект генерации
SQL — в `sql`.

---

## 2. Результат: что переезжает / что остаётся

**В `org.eclipse.daanse.sql` (минимальное ядро):**

| В SQL | Содержание |
|---|---|
| `sql.api` (schema/type/sql) | `api.schema.*` (замкнутый `sealed`-остров), `api.type.*`, `api.sql.*` |
| `sql.dialect.api` | API диалектов **без** `extends MetadataProvider`; генераторы, возможности, квотеры, TypeMapper |
| `sql.dialect.db.*` | 8 активных диалектов **без** методов роли A |

**Остаётся в `org.eclipse.daanse.jdbc.db`:**

| Остаётся | Содержание |
|---|---|
| `jdbc.db.api` (остаток) | `api.meta.*`, `MetadataProvider`, `DatabaseService`, `MetaDataQueries`, `SnapshotBuilder` |
| `jdbc.db.record` | **целиком** (`record.schema` + `record.meta`) |
| `jdbc.db.impl` | движок интроспекции |
| `jdbc.db.dialect.metadata` (**новое**) | компаньоны `<Db>MetadataProvider` (вынесенные методы роли A) |
| `jdbc.db.importer` | ETL |

`api.type`/`api.sql` всегда уходят в SQL; `impl`/`importer` всегда остаются. Результат:
**одностороннее `jdbc.db → sql`**, без цикла.

> Сравнение объёмов: по сравнению с полным переносом (План 1) здесь остаются в `jdbc.db` **`record`
> целиком**, **`api.meta`** и интерфейсы интроспекции. Переезжают только `api.schema`+`type`+`sql` +
> диалекты.

---

## 3. Целевая структура в SQL-репозитории

```
sql/
  api/                       (новое — leaf: только schema/type/sql)
  dialect/                   (новое — агрегатор)
    api/                     (leaf — без наследования MetadataProvider)
    db/                      (агрегатор)
      common/ test-support/ duckdb/ h2/ mariadb/ mssqlserver/ mysql/ oracle/ postgresql/ sqlite/
  deparser/ guard/ statement/   (существует — перевести на внутренние реактора)
```

Новое в `jdbc.db`:
```
jdbc.db/
  dialect/metadata/        (новое — компаньоны <Db>MetadataProvider, OSGi-компоненты)
  api/ record/ impl/ importer/   (остаются; api разделён → см. 02)
```

---

## 4. Фазы

1. **Фаза R — разделение ролей** ([01](01-role-separation.md)): убрать `Dialect extends
   MetadataProvider`; по каждому диалекту вынести методы роли A в `<Db>MetadataProvider`; перевести
   вызывающих. *Внутри `jdbc.db`, ещё без переноса репозитория.*
2. **Фаза S — разделение API** ([02](02-target-structure.md)): разделить `api` на SQL-модель
   (schema/type/sql) и остаток интроспекции (meta/верхний уровень); переименование пакетов стороны SQL.
3. **Фаза D — перенос диалектов** ([03](03-migration-procedure.md)): `dialect.api` + активные
   `dialect.db.*` в SQL (теперь лёгкие, без роли A).
4. **Фаза C — потребители** ([04](04-consumer-rewiring.md)): потребители SQL и jdbc.db + OSGi заново.
5. **Фаза V — усиление по версиям** (*опционально*, [05](05-per-version-hardening.md)): O2.
6. **Фаза B — сборка/проверка** ([06](06-build-and-verification.md)): Java 21, pom, тесты.

Порядок важен: **R перед S перед D** — сначала развязать (внутри `jdbc.db`, собирается зелёным),
потом разделить, потом переносить.

---

## 5. Детальные документы

| # | Документ | Содержание |
|---|---|---|
| 01 | [Разделение ролей](01-role-separation.md) | Ключевой рефакторинг: разделить `Dialect` ↔ `MetadataProvider`, классы-компаньоны, вызывающие |
| 02 | [Целевая структура и разделение API](02-target-structure.md) | Разделение `api`, раскладка пакетов/модулей, сопоставление переименований |
| 03 | [Процедура миграции](03-migration-procedure.md) | Пошагово, волны, порядок |
| 04 | [Потребители и OSGi](04-consumer-rewiring.md) | Потребители SQL, `impl`/`importer`, связывание OSGi |
| 05 | [Усиление по версиям (опция)](05-per-version-hardening.md) | O2: статические диалекты по версиям |
| 06 | [Сборка и проверка](06-build-and-verification.md) | Java 21, pom, объём тестов, риски, DoD |
| ⭐ | [**Руководство по внедрению (пошагово)**](IMPLEMENTATION-GUIDE.md) | Линейный путь для junior'а с примерами кода: срез **и** версии диалектов, в конце всё работает |

---

## 6. Определение готовности

**Рефакторинг (Фаза R, проверяется в одном `jdbc.db`):**
- [ ] `Dialect` больше не наследует `MetadataProvider`; `mvn -q verify` в `jdbc.db` зелёный.
- [ ] Для каждого из 6 больших диалектов есть `<Db>MetadataProvider` с вынесенными методами.
- [ ] Вызывающие `DatabaseServiceImpl` получают компаньон-провайдер (или `MetadataProvider.EMPTY`).
- [ ] Round-trip / MetaInfo-тесты зелёные против компаньонов.

**Разделение и перенос (Фазы S–B):**
- [ ] SQL-репозиторий собирается с `sql.api` (только schema/type/sql) + `sql.dialect.*`, **без**
      `record`/`api.meta`.
- [ ] `jdbc.db` собирается против опубликованных снапшотов `sql.*`; `record`, `api.meta`, `impl`,
      `importer`, `dialect.metadata` остаются там.
- [ ] Односторонняя зависимость `jdbc.db → sql` (нет зависимости от `jdbc.db` в SQL-репозитории).
- [ ] Main-код диалектов в SQL-репозитории больше не импортирует `record` и `api.meta`.
- [ ] Интеграционные тесты `statement/demo` (H2/MSSQL) зелёные.
