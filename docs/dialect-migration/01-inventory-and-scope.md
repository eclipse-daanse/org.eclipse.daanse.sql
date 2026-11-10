<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 01 – Inventar & Umfang

Zurück zum [Master-Plan](README.md).

Dieses Dokument listet **alle** Module beider Repositories, klassifiziert sie und grenzt den
Umfang der jetzigen Migration exakt ab.

---

## 1. Repository `org.eclipse.daanse.jdbc.db` (Quelle)

- Build: Maven-Multi-Modul, Parent `org.eclipse.daanse:org.eclipse.daanse.pom.parent:0.0.7`
- Group: `org.eclipse.daanse`, Version `${revision}` = `0.0.1-SNAPSHOT`
- **Java: 21**
- OSGi-Metadaten via `bnd-maven-plugin` (vom Parent) + DS-Annotationen (`@Component`); **keine**
  `bnd.bnd`- und **keine** `META-INF/services`-Dateien.

Top-Level-Module (5): `api`, `record`, `impl`, `dialect`, `importer`.

### 1.1 Klassifizierung

| Modul | Paket-Wurzel | Klasse | Umzug? |
|---|---|---|---|
| `api` | `org.eclipse.daanse.jdbc.db.api` (`.meta/.schema/.sql/.type`) | Metadaten-/Schema-/Typ-/SQL-Modell (Interfaces) | **→ sql** |
| `record` | `org.eclipse.daanse.jdbc.db.record` (`.meta/.schema`) | Record-Impls der `api`-Interfaces | **→ sql** |
| `dialect/api` | `org.eclipse.daanse.jdbc.db.dialect.api` (`.capability/.generator/.type`) | Dialekt-API | **→ sql** |
| `dialect/db/common` | `…dialect.db.common` | Dialekt-Basis (`AbstractJdbcDialect`, `AnsiDialect`, Jdbc*) | **→ sql** |
| `dialect/db/test-support` | `…dialect.db.testsupport` | Test-Helfer (`GeneratorTestSupport`, `RoundTripAssertions`) | **→ sql** |
| `dialect/db/<db>` (aktiv) | `…dialect.db.<db>` | Konkrete Dialekte | **→ sql** (Untermenge, s. §2) |
| `dialect/db/<db>` (inaktiv) | `…dialect.db.<db>` | Konkrete Dialekte | bleibt (spätere Welle) |
| `dialect/db/configurable` | `…dialect.db.configurable` | Laufzeit-konfigurierbarer Dialekt | bleibt (inaktiv) |
| `impl` | `org.eclipse.daanse.jdbc.db.impl` | Metadaten-Engine (`DatabaseServiceImpl`) — **nicht ETL** | **bleibt** (wird sql-Konsument) |
| `importer` + `importer/csv` | `…importer.csv.api/.impl` | **ETL** (CSV-Import) | **bleibt** (Randbedingung) |

> **Warum `impl` bleibt, obwohl es kein ETL ist:** `impl` ist die JDBC-`DatabaseMetaData`-Engine und
> bildet die Laufzeit-Introspektion ab. Sie wird nach dem Umzug reiner Downstream-Konsument von
> `sql` (siehe [02](02-dependency-and-layering.md)). Ihr Verbleib erzeugt keinen Zyklus, da `sql`
> nach dem Umzug keine `jdbc.db`-Abhängigkeit mehr hat.

---

## 2. Dialekt-Umfang: aktive Untermenge

Der Reactor `dialect/db/pom.xml` aktiviert derzeit **10** Module; alle übrigen (~27) liegen
auskommentiert auf der Platte.

**Aktiv (migrieren jetzt):**

```
common   test-support   duckdb   h2   mariadb   mssqlserver   mysql   oracle   postgresql   sqlite
```

- `common` und `test-support` sind Infrastruktur und Voraussetzung für alle Dialekte.
- Einzige Inter-Dialekt-Kante innerhalb der Untermenge: **`mariadb → mysql`** (beide aktiv → in sich
  geschlossen). Alle anderen aktiven Dialekte hängen nur an `common` + `api`.
- Die vom SQL-Repo heute konsumierten Dialekte (`common`, `h2`, `mysql`, `mssqlserver`) sind
  vollständig enthalten.

**Inaktiv (bleiben vorerst, spätere Welle):**

```
configurable  access  clickhouse  db2  derby  firebird  googlebigquery  greenplum  hive  hsqldb
impala  infobright  informix  ingres  interbase  luciddb  monetdb  neoview  netezza  nuodb
opensearch  pdidataservice  redshift  snowflake  sqlstream  sybase  teradata  vectorwise  vertica
```

> **Achtung – redshift/snowflake:** Bei diesen beiden Modulen sind Verzeichnisname und
> enthaltenes Paket/Klasse gekreuzt (siehe [07](07-anomalies-and-risks.md)). Beide sind **nicht** in
> der aktiven Untermenge, daher jetzt nicht betroffen; beim Nachziehen zwingend korrigieren.

**Vollständige Dialekt-Liste (alle 37 konkreten DB-Module):** access, clickhouse, db2 (zwei
Dialektklassen: `Db2Dialect`, `Db2OldAs400Dialect`), derby, duckdb, firebird, googlebigquery,
greenplum, h2, hive, hsqldb, impala, infobright, informix, ingres, interbase, luciddb, mariadb,
monetdb, mssqlserver, mysql, neoview, netezza, nuodb, opensearch, oracle, pdidataservice,
postgresql, redshift, snowflake, sqlite, sqlstream, sybase, teradata, vectorwise, vertica. Plus
`AnsiDialect` (in `common`) und `ConfigurableDialect` (in `configurable`).

---

## 3. Repository `org.eclipse.daanse.sql` (Ziel)

- Build: Maven-Multi-Modul, gleicher Parent `pom.parent:0.0.7`, Group `org.eclipse.daanse`,
  `${revision}` = `0.0.1-SNAPSHOT`. **Java: 17** (wird auf 21 angehoben).
- Bestehende Modulgruppen:

| Gruppe | Leaf-Module | Rolle |
|---|---|---|
| `deparser` | `api`, `jsqlparser` | Dialekt-bewusste SQL-Erzeugung aus geparsten Statements |
| `guard` | `api`, `jsqltranspiler` | SQL-Sicherheit/-Validierung |
| `statement` | `api`, `impl`, `demo` | Dialekt-bewusster, domänenfreier Query-Builder |

Diese Gruppen **konsumieren** heute die externen `jdbc.db.dialect.*`- und `jdbc.db.api`-Artefakte;
nach der Migration werden sie reaktor-intern verdrahtet (siehe [05](05-consumer-rewiring.md)).

---

## 4. Umfang-Prüfliste

- [ ] Jedes der 5 `jdbc.db`-Top-Level-Module ist genau einer Kategorie (umziehen | bleibt) zugeordnet.
- [ ] Aktive Dialekt-Untermenge = exakt die 10 im Reactor gelisteten Module.
- [ ] Inaktive Dialekte explizit als spätere Welle markiert.
- [ ] ETL (`importer/csv`) ausdrücklich ausgenommen.
