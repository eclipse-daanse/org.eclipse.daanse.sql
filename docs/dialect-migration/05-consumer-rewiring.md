<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 05 – Konsumenten umverdrahten

Zurück zum [Master-Plan](README.md).

Nach dem Umzug müssen alle Module, die die Dialekte / `api` / `record` nutzen, ihre
Abhängigkeiten und Importe anpassen — im SQL-Repo auf **reaktor-intern**, im jdbc.db-Repo auf
**publizierte `sql.*`-Snapshots**.

---

## 1. SQL-Repo-Konsumenten (reaktor-intern)

Betroffene Module und ihre heutigen externen Deps:

| Modul | Heutige externe Deps (`jdbc.db.*`) | Neu (reaktor-intern `sql.*`) |
|---|---|---|
| `statement/impl` | `dialect.db.common`, `dialect.db.mysql` | `sql.dialect.db.common`, `sql.dialect.db.mysql` |
| `statement/demo` | `dialect.db.common` (`AnsiDialect`), `dialect.db.mysql`, `dialect.db.mssqlserver`, H2 | `sql.dialect.db.common/mysql/mssqlserver` |
| `guard/jsqltranspiler` | `dialect.db.h2`, `dialect.db.common`, `deparser` | `sql.dialect.db.h2`, `sql.dialect.db.common` |
| `deparser/api` | `dialect.api` | `sql.dialect.api` |
| `deparser/jsqlparser` | `dialect.api` | `sql.dialect.api` |
| `statement/api` | `dialect.api` (`generator.KnownFunction`, `generator.StatementHint`), `api.schema.*`, `api.type.BestFitColumnType` | `sql.dialect.api.generator.*`, `sql.api.schema.*`, `sql.api.type.*` |

**Pom-Änderung** (Beispiel `statement/impl`): externe Koordinaten mit fester Version durch
reaktor-interne mit `${project.version}` ersetzen:

```xml
<!-- vorher -->
<dependency>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.jdbc.db.dialect.db.common</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
<!-- nachher -->
<dependency>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.sql.dialect.db.common</artifactId>
  <version>${project.version}</version>
</dependency>
```

**Import-Änderung** (in allen `.java` der Konsumenten): `org.eclipse.daanse.jdbc.db.dialect` →
`org.eclipse.daanse.sql.dialect`, `org.eclipse.daanse.jdbc.db.api` → `org.eclipse.daanse.sql.api`.
Betroffene Klassen laut Analyse u. a.:
`DialectSqlRenderer`, `BasicDialect*DeParser`, `DialectDeparser`, `Expressions`, `SqlExpression`,
`SelectStatement(Builder)`, `TranspilerSqlGuard(Factory)`, `DeparserColumResolver`.

---

## 2. jdbc.db-Repo-Konsumenten (publizierte `sql.*`-Snapshots)

Nach dem Umzug sind `api`, `record`, `dialect.*` **nicht mehr im jdbc.db-Reactor**. Die verbleibenden
Module beziehen sie als externe Artefakte aus dem SQL-Repo.

### 2.1 `impl`

`impl/pom.xml` heute (compile): `…jdbc.db.api`, `…jdbc.db.record`, `…jdbc.db.dialect.api`;
(test): `…dialect.db.h2`, `…dialect.db.common`. → alle auf `…sql.*` (feste `0.0.1-SNAPSHOT`):

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

Imports in `DatabaseServiceImpl`, `CachingDatabaseService` und den Tests entsprechend auf `sql.*`.

### 2.2 `importer/csv` (ETL — bleibt)

`importer/csv/pom.xml` heute (compile): `…jdbc.db.api`, `…jdbc.db.dialect.api`, `…jdbc.db.record`,
`…io.fs.watcher.api`, `de.siegmar:fastcsv`; (runtime): `…jdbc.db.impl`, `…io.fs.watcher.watchservice`.
→ `api`/`record`/`dialect.api` auf `sql.*`; `fastcsv`, `io.fs.watcher.*`, `impl` bleiben unverändert.
Imports in `CsvDataImporter` (nutzt `Dialect`, `DialectFactory`) auf `sql.dialect.api`.

### 2.3 Reactor-Bereinigung

`jdbc.db/pom.xml`: aus `<modules>` entfernen: `api`, `record`, `dialect`. Verbleibend: `impl`,
`importer`. (Die inaktiven Dialekte lagen ohnehin auskommentiert und wandern in einer späteren Welle;
bis dahin bleiben ihre Verzeichnisse in `jdbc.db/dialect/db/`.)

---

## 3. Build-Vorbedingung

`impl`/`importer` bauen erst grün, wenn die `sql.*`-Snapshots verfügbar sind → im SQL-Repo zuerst
`mvn -q install`, danach `jdbc.db` bauen. Siehe [08](08-verification.md).
