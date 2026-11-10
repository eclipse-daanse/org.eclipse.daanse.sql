<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 02 – Abhängigkeits- & Schichtungsanalyse

Zurück zum [Master-Plan](README.md).

Dieses Dokument begründet, **warum** `api` + `record` mitziehen müssen, und zeigt, wie dadurch eine
zirkuläre Repo-Abhängigkeit vermieden wird.

---

## 1. Ist-Abhängigkeiten (innerhalb `jdbc.db`)

Kanten aus den Poms und Java-Importen (verifiziert):

```
dialect.api            → api                       (compile; nutzt api.schema.*, api.sql.*, api.type.*)
dialect.db.common      → dialect.api               (compile)
dialect.db.<db>        → dialect.db.common, dialect.api   (compile)
dialect.db.<db>        → record                    (compile; Schema-Record-Impls, z.B. h2/mysql/oracle/…)
dialect.db.<db>        → impl                       (TEST; Round-Trip-Tests, z.B. mysql/mariadb/mssql/oracle/postgresql/derby)
test-support           → api, dialect.api          (compile)

impl                   → api, record, dialect.api  (compile)
impl                   → dialect.db.common, dialect.db.h2   (TEST)
importer.csv           → api, record, dialect.api  (compile), → impl (runtime)   [ETL]

record                 → api                        (compile)
```

Inter-Dialekt-Kanten (compile), relevant für die Reihenfolge:

```
mariadb → mysql        infobright → mysql
greenplum → postgresql   netezza → postgresql   (snowflake-Modul: Redshift-Code) → postgresql
impala → hive          vectorwise → ingres        sqlstream → luciddb
```

Innerhalb der **aktiven Untermenge** existiert nur `mariadb → mysql`.

---

## 2. Das Zirkularitäts-Problem

Zöge man **nur** den `dialect`-Teilbaum um und ließe `api`/`record` in `jdbc.db`, entstünde auf
**Repo-Ebene** ein Zyklus:

```
sql/dialect.api      → jdbc.db/api           (Dialekte brauchen das Metadaten-/Typ-Modell)
jdbc.db/impl         → sql/dialect.api        (Metadaten-Engine braucht die Dialekt-API)
jdbc.db/importer.csv → sql/dialect.api        (ETL braucht die Dialekt-API)
```

→ `sql` hinge an `jdbc.db` **und** `jdbc.db` an `sql`. Das bricht die Release-Reihenfolge
(keine der beiden Snapshots ließe sich unabhängig bauen/publizieren) und ist unerwünscht.

---

## 3. Auflösung: `api` + `record` ziehen mit

Da das Metadaten-/Typ-Modell (`api`) und seine Records (`record`) **die einzigen** `jdbc.db`-Bausteine
sind, an denen der Dialekt-Code hängt, werden sie **mitgezogen**. Danach:

```
sql/api            → (nur Drittanbieter / JDK)          # keine jdbc.db-Kante
sql/record         → sql/api
sql/dialect.api    → sql/api
sql/dialect.db.*   → sql/dialect.api, sql/dialect.db.common, sql/record

jdbc.db/impl       → sql/api, sql/record, sql/dialect.api        (compile)   # einseitig
jdbc.db/importer   → sql/api, sql/record, sql/dialect.api        (compile)   # einseitig
```

**Ergebnis:** `sql` ist self-contained (keine `jdbc.db`-Abhängigkeit mehr); `jdbc.db` hängt nur noch
**einseitig** an `sql`. Saubere Schichtung:

```
        ┌─────────────────────────────────────┐
        │  org.eclipse.daanse.jdbc.db          │   (oben: JDBC-Laufzeit + ETL)
        │   impl (Metadaten-Engine)            │
        │   importer/csv (ETL)                 │
        └───────────────┬─────────────────────┘
                        │ depends on (einseitig)
        ┌───────────────▼─────────────────────┐
        │  org.eclipse.daanse.sql              │   (unten: SQL-/Dialekt-Fundament)
        │   dialect.db.* → dialect.api         │
        │   record → api                       │
        │   guard / deparser / statement       │
        └─────────────────────────────────────┘
```

---

## 4. Externe (Drittanbieter-) Abhängigkeiten, die mitwandern

- `org.slf4j:slf4j-api` — compile, in `common` und den meisten Dialekten.
- `biz.aQute.bnd:biz.aQute.bndlib` — compile, nur `oracle`.
- Pro-Dialekt **JDBC-Treiber + Testcontainers** — durchweg **Test-Scope** (z. B. `mysql-connector-j`,
  `ojdbc11`, `testcontainers` mysql/oracle-xe/junit-jupiter, `mockito`, `assertj`). Diese
  Versionen müssen ins SQL-Repo-`dependencyManagement` übernommen werden (siehe
  [06](06-build-and-java21.md)).

`api`/`record` selbst haben **keine** Laufzeit-Drittabhängigkeit außer dem JDK (und ggf. slf4j).

---

## 5. Konsequenzen für die verbleibenden `jdbc.db`-Module

- `impl`: Produktionscode (`DatabaseServiceImpl`, `CachingDatabaseService`) importiert **keine**
  Dialekte, nur `api`/`record` + `dialect.api`. Nach dem Umzug zeigen diese Deps auf `sql.*`.
  Die Round-Trip-**Tests** (`impl/sqlgen`) brauchen zusätzlich `sql.dialect.db.common/h2` (test).
- `importer/csv` (ETL): compile auf `sql.api`/`sql.record`/`sql.dialect.api`, runtime auf `impl`.
- Release-Reihenfolge: **`sql` zuerst** bauen/publizieren, dann `jdbc.db`.
