<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# Vollständiger Plan: Minimaler Schnitt (Rollen-Split)

**Status:** Plan / Spezifikation (Code-Umzug noch nicht ausgeführt)
**Verhältnis:** Umsetzung von **Option O1 (+ optional O2)** aus der 2. Analyse
([../09-minimal-split-analysis.md](../09-minimal-split-analysis.md)). Alternative zum Wholesale-Plan
([../README.md](../README.md)).

Dieser Ordner ist der **ausführbare Master-Plan** für den *minimalen* Schnitt: statt `api`+`record`
komplett zu verschieben (Wholesale, Plan 1), wird die **Metadaten-Ausliefer-Rolle aus den Dialekten
herausgelöst**, sodass der SQL-Kern minimal wird und `record` + `api.meta` + der Introspektions-Motor
in `jdbc.db` bleiben.

---

## 1. Kernidee

Ein konkreter Dialekt trägt heute **zwei Rollen** (Beleg: [../09](../09-minimal-split-analysis.md)):

- **Rolle A – Nativer Metadaten-Provider:** `Dialect extends MetadataProvider`; die 6 großen
  Dialekte überschreiben je 14–21 Methoden (`getAllIndexInfo`, `getAllPrimaryKeys`, …), die
  `information_schema` lesen und `record.schema`-Objekte bauen. → **Introspektion, gehört zu `jdbc.db`.**
- **Rolle B – SQL/DDL-Generierung:** Quoting, Generatoren, Capabilities, DDL/DML aus dem
  `api.schema`-Vokabular. → **gehört zu `sql`.**

**Entscheidender Hebel:** `DatabaseServiceImpl` (der Introspektions-Motor) konsumiert
`MetadataProvider` bereits als **Parameter** (`createMetaInfo(Connection, MetadataProvider)`,
`getCatalogs(Connection, MetadataProvider)` → ruft `provider.getAllIndexInfo(...)`). Er kennt **nicht**
den `Dialect`. Die Rollen sind also faktisch schon getrennt — nur die Vererbung
`Dialect extends MetadataProvider` verschweißt sie.

**Der Plan:** Vererbung auflösen; Rolle-A-Methoden je Dialekt in eine eigenständige Begleiter-Klasse
`<Db>MetadataProvider implements MetadataProvider` verschieben, die in `jdbc.db` bleibt; den
schlanken SQL-Generierungs-Dialekt nach `sql`.

---

## 2. Ergebnis: was zieht um / was bleibt

**Nach `org.eclipse.daanse.sql` (minimaler Kern):**

| Nach SQL | Inhalt |
|---|---|
| `sql.api` (schema/type/sql) | `api.schema.*` (geschlossene `sealed`-Insel), `api.type.*`, `api.sql.*` |
| `sql.dialect.api` | Dialekt-API **ohne** `extends MetadataProvider`; Generatoren, Capabilities, Quoter, TypeMapper |
| `sql.dialect.db.*` | Die 8 aktiven Dialekte **ohne** die Rolle-A-Methoden |

**Bleibt in `org.eclipse.daanse.jdbc.db`:**

| Bleibt | Inhalt |
|---|---|
| `jdbc.db.api` (Rest) | `api.meta.*`, `MetadataProvider`, `DatabaseService`, `MetaDataQueries`, `SnapshotBuilder` |
| `jdbc.db.record` | **komplett** (`record.schema` + `record.meta`) |
| `jdbc.db.impl` | Introspektions-Motor |
| `jdbc.db.dialect.metadata` (**neu**) | `<Db>MetadataProvider`-Begleiter (die herausgelösten Rolle-A-Methoden) |
| `jdbc.db.importer` | ETL |

`api.type`/`api.sql` gehen immer nach SQL; `impl`/`importer` bleiben immer. Ergebnis: **Einweg
`jdbc.db → sql`**, kein Zyklus.

> Vergleich der Größen: Gegenüber Wholesale (Plan 1) bleiben hier **`record` komplett**, **`api.meta`**
> und die Introspektions-Interfaces in `jdbc.db`. Nur `api.schema`+`type`+`sql` + Dialekte wandern.

---

## 3. Zielstruktur im SQL-Repo

```
sql/
  api/                     (neu — leaf: nur schema/type/sql)
  dialect/                 (neu — aggregator)
    api/                   (leaf — ohne MetadataProvider-Vererbung)
    db/                    (aggregator)
      common/ test-support/ duckdb/ h2/ mariadb/ mssqlserver/ mysql/ oracle/ postgresql/ sqlite/
  deparser/ guard/ statement/   (bestehend — auf reaktor-intern umstellen)
```

Neu in `jdbc.db`:
```
jdbc.db/
  dialect/metadata/        (neu — <Db>MetadataProvider-Begleiter, OSGi-Komponenten)
  api/ record/ impl/ importer/   (bleiben; api gesplittet → siehe 02)
```

---

## 4. Phasen

1. **Phase R – Rollen-Trennung** ([01](01-role-separation.md)): `Dialect extends MetadataProvider`
   auflösen; je Dialekt Rolle-A-Methoden in `<Db>MetadataProvider` extrahieren; Aufrufer umstellen.
   *Innerhalb `jdbc.db`, noch kein Repo-Umzug.*
2. **Phase S – API-Split** ([02](02-target-structure.md)): `api` in SQL-Modell (schema/type/sql) und
   Introspektions-Rest (meta/top-level) trennen; Paket-Rename der SQL-Seite.
3. **Phase D – Dialekt-Umzug** ([03](03-migration-procedure.md)): `dialect.api` + aktive `dialect.db.*`
   nach SQL (jetzt schlank, ohne Rolle A).
4. **Phase C – Konsumenten** ([04](04-consumer-rewiring.md)): SQL- und jdbc.db-Konsumenten + OSGi neu.
5. **Phase V – Per-Version-Härtung** (*optional*, [05](05-per-version-hardening.md)): O2.
6. **Phase B – Build/Verifikation** ([06](06-build-and-verification.md)): Java 21, Poms, Tests.

Reihenfolge ist wichtig: **R vor S vor D** — erst entkoppeln (innerhalb `jdbc.db`, grün baubar), dann
splitten, dann verschieben.

---

## 5. Detaildokumente

| # | Dokument | Inhalt |
|---|---|---|
| 01 | [Rollen-Trennung](01-role-separation.md) | Der Kern-Refactor: `Dialect` ↔ `MetadataProvider` trennen, Begleiter-Klassen, Aufrufer |
| 02 | [Zielstruktur & API-Split](02-target-structure.md) | `api`-Split, Paket-/Modul-Layout, Rename-Mapping |
| 03 | [Migrationsprozedur](03-migration-procedure.md) | Schritt-für-Schritt, Wellen, Reihenfolge |
| 04 | [Konsumenten & OSGi](04-consumer-rewiring.md) | SQL-Konsumenten, `impl`/`importer`, OSGi-Verdrahtung |
| 05 | [Per-Version-Härtung (optional)](05-per-version-hardening.md) | O2: statische Pro-Version-Dialekte |
| 06 | [Build & Verifikation](06-build-and-verification.md) | Java 21, Poms, Testumfang, Risiken, DoD |
| ⭐ | [**Umsetzungs-Anleitung (Schritt für Schritt)**](IMPLEMENTATION-GUIDE.md) | Junior-tauglicher linearer Pfad mit Code-Beispielen: Split **und** Dialekt-Versionen, am Ende lauffähig |

---

## 6. Definition of Done

**Refactor (Phase R, in `jdbc.db` allein prüfbar):**
- [ ] `Dialect` erbt nicht mehr von `MetadataProvider`; `mvn -q verify` in `jdbc.db` grün.
- [ ] Für jeden der 6 großen Dialekte existiert `<Db>MetadataProvider` mit den extrahierten Methoden.
- [ ] `DatabaseServiceImpl`-Aufrufer erhalten den Begleiter-Provider (oder `MetadataProvider.EMPTY`).
- [ ] Round-Trip-/MetaInfo-Tests grün gegen die Begleiter.

**Split & Umzug (Phasen S–B):**
- [ ] SQL-Repo baut mit `sql.api` (nur schema/type/sql) + `sql.dialect.*`, **ohne** `record`/`api.meta`.
- [ ] `jdbc.db` baut gegen publizierte `sql.*`-Snapshots; `record`, `api.meta`, `impl`, `importer`,
      `dialect.metadata` bleiben dort.
- [ ] Einweg-Abhängigkeit `jdbc.db → sql` (kein `jdbc.db`-Dep im SQL-Repo).
- [ ] Dialekt-Main im SQL-Repo importiert **kein** `record` und **kein** `api.meta` mehr.
- [ ] `statement/demo`-Integrationstests (H2/MSSQL) grün.
