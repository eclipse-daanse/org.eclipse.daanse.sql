<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 02 – Целевая структура и разделение API

Назад к [плану минимального среза](README.md).

После разделения ролей ([01](01-role-separation.md)) код диалектов зависит лишь от узкого замкнутого
подмножества `api`. Этот документ фиксирует, **как разделяется `api`** и какие пакеты/модули куда идут.

---

## 1. Разделение `api`

`org.eclipse.daanse.jdbc.db.api` разделяется на два артефакта:

| Часть | Пакеты | Цель |
|---|---|---|
| **Модель SQL** | `api.schema.*`, `api.type.*`, `api.sql.*` | → `org.eclipse.daanse.sql.api` |
| **Интроспекция (остаток)** | `api.meta.*`, верхний уровень `MetadataProvider`, `DatabaseService`, `MetaDataQueries`, `SnapshotBuilder` | остаётся `org.eclipse.daanse.jdbc.db.api` |

**Почему эта граница чиста (обосновано):**
- `api.schema` **не** импортирует другие пакеты api → замкнутый `sealed`-остров, переезжает целиком.
- `api.type`/`api.sql` — чистые листья (используют только диалекты) → переезжают.
- Остаток интроспекции импортирует `api.schema` (теперь в SQL) → ребро **`jdbc.db → sql`**,
  одностороннее. Конкретно: `MetadataProvider`, `MetaDataQueries`, `SnapshotBuilder`, `StructureInfo`
  ссылаются на типы схемы — после разделения эти ссылки указывают на `sql.api.schema` (разрешено, без
  цикла).

`record` **не** разделяется и **не** переносится: `record.schema`/`record.meta` остаются целиком в
`jdbc.db` (используются только компаньонами `<Db>MetadataProvider` и `impl`). `record` реализует
интерфейсы `api.schema` (теперь в SQL) → снова одностороннее `jdbc.db → sql`.

---

## 2. Переименование пакетов/артефактов (только сторона SQL)

| Старое | Новое | Примечание |
|---|---|---|
| `org.eclipse.daanse.jdbc.db.api.schema` | `org.eclipse.daanse.sql.api.schema` | весь остров |
| `org.eclipse.daanse.jdbc.db.api.type` | `org.eclipse.daanse.sql.api.type` | лист |
| `org.eclipse.daanse.jdbc.db.api.sql` | `org.eclipse.daanse.sql.api.sql` | лист |
| `org.eclipse.daanse.jdbc.db.dialect.api` | `org.eclipse.daanse.sql.dialect.api` | без наследования `MetadataProvider` |
| `org.eclipse.daanse.jdbc.db.dialect.db.<db>` | `org.eclipse.daanse.sql.dialect.db.<db>` | без методов роли A |

**Остаётся без изменений в `jdbc.db`:** `…api.meta`, `…api` (верхний уровень интроспекции),
`…record.*`, `…impl`, `…importer.*`, и **новое** `…dialect.metadata.<db>` (компаньоны).

ArtifactId:
- новые: `org.eclipse.daanse.sql.api` (schema+type+sql), `org.eclipse.daanse.sql.dialect.api`,
  `org.eclipse.daanse.sql.dialect.db.*`.
- остаются: `org.eclipse.daanse.jdbc.db.api` (теперь только meta+интроспекция), `…record`, `…impl`,
  `…importer.csv`, новое `…dialect.metadata` (сборный модуль компаньонов).

> **О наименовании:** оставшийся `jdbc.db.api` после разделения содержит только интроспекцию.
> Опционально позже его можно переименовать в `jdbc.db.introspection` — **не** часть этого плана.

---

## 3. Раскладка модулей

**SQL-репозиторий (новое):**
```
sql/
  api/                       org.eclipse.daanse.sql.api            (schema + type + sql)
  dialect/
    api/                     org.eclipse.daanse.sql.dialect.api
    db/
      common/ test-support/ duckdb/ h2/ mariadb/ mssqlserver/ mysql/ oracle/ postgresql/ sqlite/
  deparser/ guard/ statement/   (существует)
```

**Репозиторий jdbc.db (после переустройства):**
```
jdbc.db/
  api/                       org.eclipse.daanse.jdbc.db.api        (только meta + интерфейсы интроспекции)
  record/                    org.eclipse.daanse.jdbc.db.record     (целиком)
  impl/                      org.eclipse.daanse.jdbc.db.impl
  dialect/
    metadata/                org.eclipse.daanse.jdbc.db.dialect.metadata  (НОВОЕ: <Db>MetadataProvider)
  importer/csv/              org.eclipse.daanse.jdbc.db.importer.csv
```

---

## 4. Рёбра зависимостей после переустройства (целевое состояние)

```
sql.api               → (JDK / slf4j)                    # нет ребра к jdbc.db
sql.dialect.api       → sql.api
sql.dialect.db.*      → sql.dialect.api, sql.dialect.db.common, sql.api

jdbc.db.api (остаток) → sql.api.schema                    # интроспекция ссылается на модель схемы
jdbc.db.record        → sql.api                            # записи реализуют интерфейсы схемы
jdbc.db.dialect.metadata.<db> → jdbc.db.api (MetadataProvider), jdbc.db.record, sql.api
jdbc.db.impl          → jdbc.db.api, jdbc.db.record, (sql.api)
jdbc.db.importer.csv  → jdbc.db.api, jdbc.db.record, sql.dialect.api   (+ impl runtime)  [ETL]
```

Все рёбра `jdbc.db` указывают односторонне на `sql.*`. У `sql.*` **нет** ребра к `jdbc.db` → **нет
цикла**, `sql` собирается/публикуется независимо.

---

## 5. Минимальный набор типов SQL (проверка)

Собственный код SQL (guard/deparser/statement) по-прежнему нуждается только в 7 листовых типах
(`Datatype`, `BestFitColumnType`, `BitOperation`, `OrderedColumn`, `SchemaReference`, `TableReference`,
`ColumnReference`) — теперь из `sql.api`. Диалекты добавляют `api.schema` (остров, словарь DDL).
**Никакого** `record`, **никакого** `api.meta` на стороне SQL.
