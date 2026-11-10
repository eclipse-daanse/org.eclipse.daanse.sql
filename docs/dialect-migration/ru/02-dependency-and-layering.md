<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 02 – Анализ зависимостей и слоистости

Назад к [мастер-плану](README.md).

Этот документ обосновывает, **почему** `api` + `record` должны переехать вместе, и показывает, как
это предотвращает циклическую зависимость между репозиториями.

---

## 1. Текущие зависимости (внутри `jdbc.db`)

Рёбра из pom-файлов и Java-импортов (проверено):

```
dialect.api            → api                       (compile; использует api.schema.*, api.sql.*, api.type.*)
dialect.db.common      → dialect.api               (compile)
dialect.db.<db>        → dialect.db.common, dialect.api   (compile)
dialect.db.<db>        → record                    (compile; record-реализации схемы, напр. h2/mysql/oracle/…)
dialect.db.<db>        → impl                       (TEST; round-trip-тесты, напр. mysql/mariadb/mssql/oracle/postgresql/derby)
test-support           → api, dialect.api          (compile)

impl                   → api, record, dialect.api  (compile)
impl                   → dialect.db.common, dialect.db.h2   (TEST)
importer.csv           → api, record, dialect.api  (compile), → impl (runtime)   [ETL]

record                 → api                        (compile)
```

Меж-диалектные рёбра (compile), важные для порядка:

```
mariadb → mysql        infobright → mysql
greenplum → postgresql   netezza → postgresql   (модуль snowflake: код Redshift) → postgresql
impala → hive          vectorwise → ingres        sqlstream → luciddb
```

Внутри **активного подмножества** существует только `mariadb → mysql`.

---

## 2. Проблема цикличности

Если перенести **только** поддерево `dialect`, оставив `api`/`record` в `jdbc.db`, на **уровне
репозиториев** возникает цикл:

```
sql/dialect.api      → jdbc.db/api           (диалектам нужна модель метаданных/типов)
jdbc.db/impl         → sql/dialect.api        (движку метаданных нужен API диалектов)
jdbc.db/importer.csv → sql/dialect.api        (ETL нужен API диалектов)
```

→ `sql` зависел бы от `jdbc.db` **и** `jdbc.db` от `sql`. Это ломает порядок релизов (ни один из
снапшотов нельзя собрать/опубликовать независимо) и нежелательно.

---

## 3. Решение: `api` + `record` переезжают вместе

Поскольку модель метаданных/типов (`api`) и её record-реализации (`record`) — **единственные**
кирпичики `jdbc.db`, от которых зависит код диалектов, они **переносятся вместе**. После этого:

```
sql/api            → (только сторонние библиотеки / JDK)   # нет ребра к jdbc.db
sql/record         → sql/api
sql/dialect.api    → sql/api
sql/dialect.db.*   → sql/dialect.api, sql/dialect.db.common, sql/record

jdbc.db/impl       → sql/api, sql/record, sql/dialect.api        (compile)   # односторонне
jdbc.db/importer   → sql/api, sql/record, sql/dialect.api        (compile)   # односторонне
```

**Итог:** `sql` самодостаточен (больше нет зависимости от `jdbc.db`); `jdbc.db` зависит только
**односторонне** от `sql`. Чистая слоистость:

```
        ┌─────────────────────────────────────┐
        │  org.eclipse.daanse.jdbc.db          │   (сверху: JDBC-рантайм + ETL)
        │   impl (движок метаданных)           │
        │   importer/csv (ETL)                 │
        └───────────────┬─────────────────────┘
                        │ зависит (односторонне)
        ┌───────────────▼─────────────────────┐
        │  org.eclipse.daanse.sql              │   (снизу: фундамент SQL/диалектов)
        │   dialect.db.* → dialect.api         │
        │   record → api                       │
        │   guard / deparser / statement       │
        └─────────────────────────────────────┘
```

---

## 4. Внешние (сторонние) зависимости, переезжающие вместе

- `org.slf4j:slf4j-api` — compile, в `common` и большинстве диалектов.
- `biz.aQute.bnd:biz.aQute.bndlib` — compile, только `oracle`.
- Пер-диалектные **JDBC-драйверы + Testcontainers** — сплошь **test-scope** (напр., `mysql-connector-j`,
  `ojdbc11`, `testcontainers` mysql/oracle-xe/junit-jupiter, `mockito`, `assertj`). Эти версии нужно
  перенести в `dependencyManagement` SQL-репозитория (см. [06](06-build-and-java21.md)).

Сами `api`/`record` **не** имеют сторонних рантайм-зависимостей, кроме JDK (и, возможно, slf4j).

---

## 5. Последствия для оставшихся модулей `jdbc.db`

- `impl`: продакшн-код (`DatabaseServiceImpl`, `CachingDatabaseService`) **не** импортирует
  диалекты, только `api`/`record` + `dialect.api`. После переезда эти зависимости указывают на
  `sql.*`. Round-trip-**тесты** (`impl/sqlgen`) дополнительно требуют `sql.dialect.db.common/h2` (test).
- `importer/csv` (ETL): compile на `sql.api`/`sql.record`/`sql.dialect.api`, runtime на `impl`.
- Порядок релизов: сначала собрать/опубликовать **`sql`**, затем `jdbc.db`.
