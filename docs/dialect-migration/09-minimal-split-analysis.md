<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 09 – Minimaler Schnitt: was `sql` wirklich aus `jdbc.db` braucht

Zurück zum [Master-Plan](README.md).

> 🌐 Sprache: **Deutsch** (diese Datei) · [Русский](ru/09-minimal-split-analysis.md)

**Zweck.** Dies ist eine *zweite* Analyse, ergänzend zu Plan 1 (Wholesale-Umzug von `api`+`record`,
siehe [README](README.md) und [02](02-dependency-and-layering.md)). Sie beantwortet die Frage: *Wie
lässt sich der **minimale** Teil herauslösen, den `sql` aus `jdbc.db` braucht — wo die Dialekte ja
mitgebraucht werden?* Ausgelöst durch die Idee, Dialekte **statisch pro Version** fest einzubauen,
sodass kein JDBC-`record` zum Setzen der Eigenschaften nötig ist.

Dieses Dokument **legt sich bewusst nicht** auf einen Weg fest. Es stellt die Fakten und vier
Optionen neutral mit Aufwand/Risiko dar.

---

## 1. Ausgangsfrage

Plan 1 verschiebt `api` + `record` **komplett** nach `sql`. Das ist mechanisch einfach, macht `sql`
aber zum Eigentümer des gesamten Metadaten-/Introspektionsmodells. Die Frage hier: geht es **kleiner**
— nur der Teil, den die Dialekte und der SQL-Eigencode tatsächlich benötigen?

---

## 2. Was `sql` selbst braucht (sehr wenig)

Der SQL-**Eigencode** (`guard`, `deparser`, `statement`) berührt aus `jdbc.db.api` nur **7 leichte
Blatt-Typen** — **kein** `record`, **kein** `api.meta`, keine Introspektion:

| Paket | Typen |
|---|---|
| `api.type` | `Datatype`, `BestFitColumnType` |
| `api.sql` | `BitOperation`, `OrderedColumn` |
| `api.schema` (References) | `SchemaReference`, `TableReference`, `ColumnReference` |

Diese kommen heute sogar nur **transitiv** über `dialect.api` herein (kein SQL-Pom deklariert
`jdbc.db.api` direkt). Sie sind eigenständig und geschlossen (`BestFitColumnType`/`Datatype` sind
reine Enums; `TableReference → SchemaReference → CatalogReference → Named`).

**Das Problem ist nicht der SQL-Eigencode — es sind die Dialekte selbst.**

---

## 3. Was die Dialekte mitziehen — die zwei Rollen

Ein konkreter Dialekt vereint heute **zwei unabhängige Verantwortlichkeiten**:

### Rolle A — Nativer Metadaten-Provider (~85–90 % der Kopplung)

`Dialect extends … MetadataProvider`. Die 6 „großen" Dialekte überschreiben je 14–21
`MetadataProvider`-Methoden (`getAllIndexInfo`, `getAllPrimaryKeys`, `getAllImportedKeys`,
`getAllTriggers`, `getAllSequences`, `getUniqueConstraints`, …), die **`information_schema` /
Katalogtabellen gegen eine Live-`Connection`** abfragen und **`record.schema`-Objekte als Rückgabe
konstruieren**.

- `record.schema.*` und `api.meta.IndexInfo`/`IndexInfoItem` werden **ausschließlich** hierfür genutzt.
- Das ist **Introspektion** (Metadaten *lesen*), nicht SQL-Generierung.

| Dialekt | MetadataProvider-Overrides | `record.schema`-Konstruktionen |
|---|---|---|
| oracle | 21 | 19 |
| mssqlserver | 19 | 16 |
| postgresql | 19 | 16 |
| mysql | 15 | 11 |
| mariadb | 15 | 12 |
| h2 | 14 | 11 |
| **duckdb / sqlite / common** | **0** | **0** |

### Rolle B — DDL-Konsument (~10–15 % der Kopplung)

Die `DdlGenerator`-API nimmt `api.schema`-Typen als **Eingabe-Vokabular**:

```java
default String createTable(TableReference table, List<ColumnDefinition> columns,
                           PrimaryKey primaryKey, boolean ifNotExists) { … }
default String alterTableAddColumn(TableReference table, ColumnDefinition column) { … }
default String createTrigger(String name, Trigger.TriggerTiming timing,
                             Trigger.TriggerEvent event, TableReference table, …) { … }
```

- Der Großteil liegt als `default`-Methoden **in der geteilten API** (`DdlGenerator`), nicht in den
  Dialekten. Nur mssqlserver/oracle/mysql überschreiben einige mit `api.schema`-Eingaben.
- Berührt **nie** `record` oder `api.meta` — nur `api.schema` als Parameter.

### Rolle C — Konfiguration/Capabilities: **keine** Metadaten-Kopplung

Weder `record` noch `api.meta` noch die schweren `api.schema`-Definitionstypen werden für
Konfiguration/Fähigkeiten genutzt (siehe §5).

---

## 4. Der `sealed`-Blocker und die geschlossene `api.schema`-Insel

Warum lässt sich `api.schema` nicht auf „ein paar References" verkleinern:

- `SchemaObject` ist `sealed … permits TableDefinition, ViewDefinition, MaterializedView, Sequence,
  Function, Procedure, Trigger, UserDefinedType` (8).
- `Constraint` ist `sealed … permits PrimaryKey, ImportedKey, UniqueConstraint, CheckConstraint` (4).

Sobald ein Generator `Trigger`, `TableDefinition` oder `PrimaryKey` anfasst (Rolle B tut das), zwingt
die versiegelte Hülle **das gesamte `api.schema`-Paket** (39 Typen), gemeinsam zu kompilieren.

**Gute Nachricht:** `api.schema` importiert **kein** anderes api-Paket (nicht `meta`, nicht `sql`,
nicht `type`). Es ist eine **geschlossene Insel** — schwer, aber als Ganzes sauber verschiebbar,
ohne die Introspektion (`api.meta`, top-level) mitzuziehen.

---

## 5. Konfiguration/Versionierung ist bereits entkoppelt

Die Idee „fest pro Version einbauen" trifft auf ein System, das **schon** so gebaut ist:

- **`DialectInitData`** ist ein schlankes `record` (nur Primitive: `productVersion`, Major/Minor,
  Quote-Zeichen, Keywords, ResultSet-Styles). Es hängt **nicht** an `api.schema`/`record`. Es gibt
  `ansiDefaults()` (rein statisch, ohne JDBC) und Wither (`withVersion(maj,min)`, …).
- **Capabilities** sind statische Boolean-Records mit Presets (`DdlCapabilities.full()/.minimal()`,
  `AggregateCapabilities.none()`, …). Nie aus Live-Metadaten zur Query-Zeit abgeleitet.
- **Versions-Verzweigung** ist bereits statisch — zwei etablierte Muster:
  - **Subklasse pro Variante:** `Db2OldAs400Dialect extends Db2Dialect` (überschreibt
    `allowsFromQuery()→false`), eigener `@DialectName("DB2_OLD_AS400")`.
  - **In-Methode:** `dialectVersion.isUnknownOrAtLeast(8,0)` (mysql/oracle/postgresql/mssql/mariadb/sqlite).

Damit ist die **SQL-Generierung schon vollständig von Live-Metadaten entkoppelt** — sie liest nur
Konstanten + einen `int` `dialectVersion`, den man ohne Connection setzen kann. Die einzige
Metadaten-Kopplung ist Rolle A (das *Ausliefern* von Metadaten).

---

## 6. Optionen (neutral, mit Aufwand/Risiko)

### O1 — Rollen-Split: MetadataProvider-Rolle herauslösen

`Dialect extends MetadataProvider` auflösen; die je ~7–21 Lademethoden der 6 großen Dialekte in
Begleiter-Klassen (`<Db>MetadataProvider implements MetadataProvider`) verschieben, die in `jdbc.db`
bei der Introspektion bleiben (OSGi-verdrahtet). Der SQL-seitige Dialekt behält alles andere.

- **Ergebnis:** Dialekte importieren **kein** `record`/`api.meta` mehr. SQL-Kern = Dialekt-SQL-Gen +
  `api.schema` (Insel) + `api.type` + `api.sql`. `record`, `api.meta`, Introspektion, `impl`,
  `importer` bleiben in `jdbc.db`.
- **Aufwand:** mittel — 6 Dialekte anfassen; `Dialect`-SPI-Bruch (die `getAll*`-Defaults wandern in
  die separate `MetadataProvider`-SPI). duckdb/sqlite/common unverändert.
- **Risiko:** OSGi-Verdrahtung Dialekt ↔ MetadataProvider muss neu gefügt werden; Konsumenten von
  `dialect.getAllIndexInfo(...)` müssen auf den separaten Provider umschwenken.

### O2 — Rollen-Split + feste Pro-Version-Subklassen

Wie O1, zusätzlich die `dialectVersion`-Verzweigung durch **feste Pro-Version-Klassen** ersetzen
(Muster `Db2OldAs400Dialect`), Eigenschaften vollständig einkompiliert — **kein**
`DialectInitData`-Snapshot mehr nötig.

- **Ergebnis:** maximale Entkopplung; Dialekt komplett statisch, ohne jede Laufzeit-Metadaten.
- **Aufwand:** hoch — pro DB mehrere Versionsklassen; Auswahl-/Factory-Logik nach Version.
- **Risiko:** Klassen-Explosion; Versions-Auswahl muss robust sein (Produktname/Version → Klasse).

### O3 — Closure-Schnitt ohne Refactor

Kein API-Bruch. Dialekte + ihre transitive Hülle nach SQL: `dialect.*`, `api.type`, `api.sql`, das
ganze `api.schema`, `api.meta.IndexInfo(+Item)`, `MetadataProvider`, `record.schema`. In `jdbc.db`
bleibt der reine Introspektions-Motor (`DatabaseService`, `MetaDataQueries`, `SnapshotBuilder`,
`meta` minus IndexInfo, `record.meta`, `impl`, `importer`).

- **Ergebnis:** kleiner als Wholesale (der Motor bleibt), aber `record.schema` + `MetadataProvider`
  wandern mit. Einweg `jdbc.db → sql`, kein Zyklus, keine API-Änderung.
- **Aufwand:** mittel — `api`/`record` müssen **gesplittet** werden (schema/type/sql/IndexInfo → SQL;
  Rest bleibt).
- **Risiko:** Paket `api`/`record` über zwei Repos geteilt; Split-Grenze sauber ziehen.

### O4 — Wholesale (= Plan 1)

`api` + `record` komplett nach SQL. Siehe [README](README.md)/[02](02-dependency-and-layering.md).

- **Ergebnis:** einfachste Mechanik; SQL besitzt das gesamte Metadaten-/Introspektionsmodell.
- **Aufwand:** gering–mittel; **Risiko:** gering; aber **nicht** „minimal".

---

## 7. Vergleich

| | O1 Rollen-Split | O2 + Versionsklassen | O3 Closure-Schnitt | O4 Wholesale |
|---|---|---|---|---|
| SQL bekommt `record` | nein | nein | `record.schema` | ganz |
| SQL bekommt `api.meta` | nein | nein | nur IndexInfo | ganz |
| SQL bekommt `api.schema` | ganz (Insel) | ganz (Insel) | ganz (Insel) | ganz |
| `Dialect extends MetadataProvider` | **aufgelöst** | aufgelöst | bleibt | bleibt |
| API-Bruch | ja (SPI) | ja (SPI) | nein | nein |
| Betroffene Dialekte | 6 | 6 + Versionsklassen | 0 (nur Deps) | 0 |
| `api`/`record` gesplittet | ja | ja | ja | nein |
| SQL-Kerngröße | **minimal** | **minimal** | mittel | groß |
| Aufwand | mittel | hoch | mittel | gering–mittel |

**Gemeinsam allen Optionen:** `api.type` + `api.sql` gehen immer nach SQL (reine Blätter, nur von
Dialekten genutzt); `impl` + `importer/csv` (ETL) bleiben immer in `jdbc.db`; Ergebnis ist stets
Einweg `jdbc.db → sql` ohne Zyklus.

---

## 8. Bezug zu Plan 1

Plan 1 ([README](README.md)) entspricht **O4**. Diese Analyse zeigt, dass **O1/O2** einen echt
minimalen SQL-Kern liefern (der Idee „fest pro Version, kein JDBC-record" folgend), zum Preis eines
`Dialect`-SPI-Bruchs und Anfassens der 6 großen Dialekte; **O3** ist der Mittelweg ohne API-Bruch.
Die Wahl ist bewusst offen gelassen.

> **Vollständiger Plan für O1/O2:** siehe [`minimal-split/`](minimal-split/README.md) — der
> ausführbare Schritt-für-Schritt-Plan für den Rollen-Split (Rolle A aus den Dialekten herauslösen).
