<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 05 – Перепривязка потребителей

Назад к [мастер-плану](README.md).

После переезда все модули, использующие диалекты / `api` / `record`, должны обновить свои
зависимости и импорты — в SQL-репозитории на **внутренние реактора**, в jdbc.db — на
**опубликованные снапшоты `sql.*`**.

---

## 1. Потребители в SQL-репозитории (внутри реактора)

Затронутые модули и их сегодняшние внешние зависимости:

| Модуль | Сегодняшние внешние зависимости (`jdbc.db.*`) | Новые (внутри реактора `sql.*`) |
|---|---|---|
| `statement/impl` | `dialect.db.common`, `dialect.db.mysql` | `sql.dialect.db.common`, `sql.dialect.db.mysql` |
| `statement/demo` | `dialect.db.common` (`AnsiDialect`), `dialect.db.mysql`, `dialect.db.mssqlserver`, H2 | `sql.dialect.db.common/mysql/mssqlserver` |
| `guard/jsqltranspiler` | `dialect.db.h2`, `dialect.db.common`, `deparser` | `sql.dialect.db.h2`, `sql.dialect.db.common` |
| `deparser/api` | `dialect.api` | `sql.dialect.api` |
| `deparser/jsqlparser` | `dialect.api` | `sql.dialect.api` |
| `statement/api` | `dialect.api` (`generator.KnownFunction`, `generator.StatementHint`), `api.schema.*`, `api.type.BestFitColumnType` | `sql.dialect.api.generator.*`, `sql.api.schema.*`, `sql.api.type.*` |

**Изменение pom** (пример `statement/impl`): заменить внешние координаты с фиксированной версией на
внутренние реактора с `${project.version}`:

```xml
<!-- было -->
<dependency>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.jdbc.db.dialect.db.common</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
<!-- стало -->
<dependency>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.sql.dialect.db.common</artifactId>
  <version>${project.version}</version>
</dependency>
```

**Изменение импортов** (во всех `.java` потребителей): `org.eclipse.daanse.jdbc.db.dialect` →
`org.eclipse.daanse.sql.dialect`, `org.eclipse.daanse.jdbc.db.api` → `org.eclipse.daanse.sql.api`.
Затронутые классы по результатам анализа, среди прочего:
`DialectSqlRenderer`, `BasicDialect*DeParser`, `DialectDeparser`, `Expressions`, `SqlExpression`,
`SelectStatement(Builder)`, `TranspilerSqlGuard(Factory)`, `DeparserColumResolver`.

---

## 2. Потребители в jdbc.db (опубликованные снапшоты `sql.*`)

После переезда `api`, `record`, `dialect.*` **больше не в реакторе jdbc.db**. Оставшиеся модули
получают их как внешние артефакты из SQL-репозитория.

### 2.1 `impl`

`impl/pom.xml` сегодня (compile): `…jdbc.db.api`, `…jdbc.db.record`, `…jdbc.db.dialect.api`;
(test): `…dialect.db.h2`, `…dialect.db.common`. → все на `…sql.*` (фиксированная `0.0.1-SNAPSHOT`):

```xml
<dependency><groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.sql.api</artifactId><version>0.0.1-SNAPSHOT</version></dependency>
<dependency><groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.sql.record</artifactId><version>0.0.1-SNAPSHOT</version></dependency>
<dependency><groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.sql.dialect.api</artifactId><version>0.0.1-SNAPSHOT</version></dependency>
<!-- test -->
<dependency><groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.sql.dialect.db.h2</artifactId><version>0.0.1-SNAPSHOT</version><scope>test</scope></dependency>
<dependency><groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.sql.dialect.db.common</artifactId><version>0.0.1-SNAPSHOT</version><scope>test</scope></dependency>
```

Импорты в `DatabaseServiceImpl`, `CachingDatabaseService` и тестах соответственно на `sql.*`.

### 2.2 `importer/csv` (ETL — остаётся)

`importer/csv/pom.xml` сегодня (compile): `…jdbc.db.api`, `…jdbc.db.dialect.api`, `…jdbc.db.record`,
`…io.fs.watcher.api`, `de.siegmar:fastcsv`; (runtime): `…jdbc.db.impl`, `…io.fs.watcher.watchservice`.
→ `api`/`record`/`dialect.api` на `sql.*`; `fastcsv`, `io.fs.watcher.*`, `impl` без изменений.
Импорты в `CsvDataImporter` (использует `Dialect`, `DialectFactory`) на `sql.dialect.api`.

### 2.3 Очистка реактора

`jdbc.db/pom.xml`: удалить из `<modules>`: `api`, `record`, `dialect`. Остаются: `impl`, `importer`.
(Неактивные диалекты и так были закомментированы и переедут в более поздней волне; до тех пор их
каталоги остаются в `jdbc.db/dialect/db/`.)

---

## 3. Предусловие сборки

`impl`/`importer` соберутся зелёными только когда доступны снапшоты `sql.*` → в SQL-репозитории
сначала `mvn -q install`, затем собирать `jdbc.db`. См. [08](08-verification.md).
