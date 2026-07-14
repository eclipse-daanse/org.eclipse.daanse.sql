<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 02 – Zielstruktur & API-Split

Zurück zum [Minimal-Split-Plan](README.md).

Nach der Rollen-Trennung ([01](01-role-separation.md)) hängt der Dialekt-Code nur noch an einer
schmalen, geschlossenen Teilmenge von `api`. Dieses Dokument legt fest, **wie `api` gesplittet** wird
und welche Pakete/Module wohin gehören.

---

## 1. Der `api`-Split

`org.eclipse.daanse.jdbc.db.api` wird in zwei Artefakte getrennt:

| Teil | Pakete | Ziel |
|---|---|---|
| **SQL-Modell** | `api.schema.*`, `api.type.*`, `api.sql.*` | → `org.eclipse.daanse.sql.api` |
| **Introspektion (Rest)** | `api.meta.*`, top-level `MetadataProvider`, `DatabaseService`, `MetaDataQueries`, `SnapshotBuilder` | bleibt `org.eclipse.daanse.jdbc.db.api` |

**Warum diese Grenze sauber ist (belegt):**
- `api.schema` importiert **kein** anderes api-Paket → geschlossene `sealed`-Insel, wandert komplett.
- `api.type`/`api.sql` sind reine Blätter (nur Dialekte nutzen sie) → wandern.
- Der Introspektions-Rest importiert `api.schema` (jetzt in SQL) → Kante **`jdbc.db → sql`**, einseitig.
  Konkret: `MetadataProvider`, `MetaDataQueries`, `SnapshotBuilder`, `StructureInfo` referenzieren
  Schema-Typen — nach dem Split zeigen diese Referenzen auf `sql.api.schema` (erlaubt, kein Zyklus).

`record` wird **nicht** gesplittet und **nicht** verschoben: `record.schema`/`record.meta` bleiben
komplett in `jdbc.db` (nur von den `<Db>MetadataProvider`-Begleitern und `impl` genutzt). `record`
implementiert `api.schema`-Interfaces (jetzt in SQL) → wiederum einseitig `jdbc.db → sql`.

---

## 2. Paket- & Artefakt-Rename (nur SQL-Seite)

| Alt | Neu | Bemerkung |
|---|---|---|
| `org.eclipse.daanse.jdbc.db.api.schema` | `org.eclipse.daanse.sql.api.schema` | ganze Insel |
| `org.eclipse.daanse.jdbc.db.api.type` | `org.eclipse.daanse.sql.api.type` | Blatt |
| `org.eclipse.daanse.jdbc.db.api.sql` | `org.eclipse.daanse.sql.api.sql` | Blatt |
| `org.eclipse.daanse.jdbc.db.dialect.api` | `org.eclipse.daanse.sql.dialect.api` | ohne `MetadataProvider`-Vererbung |
| `org.eclipse.daanse.jdbc.db.dialect.db.<db>` | `org.eclipse.daanse.sql.dialect.db.<db>` | ohne Rolle-A-Methoden |

**Bleibt unverändert in `jdbc.db`:** `…api.meta`, `…api` (top-level Introspektion), `…record.*`,
`…impl`, `…importer.*`, und **neu** `…dialect.metadata.<db>` (die Begleiter).

ArtifactIds:
- neu: `org.eclipse.daanse.sql.api` (schema+type+sql), `org.eclipse.daanse.sql.dialect.api`,
  `org.eclipse.daanse.sql.dialect.db.*`.
- bleibt: `org.eclipse.daanse.jdbc.db.api` (jetzt nur meta+Introspektion), `…record`, `…impl`,
  `…importer.csv`, neu `…dialect.metadata` (o. ä. Sammelmodul der Begleiter).

> **Namens-Hinweis:** Das verbleibende `jdbc.db.api` enthält nach dem Split nur noch
> Introspektion. Optional könnte es später zu `jdbc.db.introspection` umbenannt werden — **nicht**
> Teil dieses Plans.

---

## 3. Modul-Layout

**SQL-Repo (neu):**
```
sql/
  api/                       org.eclipse.daanse.sql.api            (schema + type + sql)
  dialect/
    api/                     org.eclipse.daanse.sql.dialect.api
    db/
      common/ test-support/ duckdb/ h2/ mariadb/ mssqlserver/ mysql/ oracle/ postgresql/ sqlite/
  deparser/ guard/ statement/   (bestehend)
```

**jdbc.db-Repo (nach Umbau):**
```
jdbc.db/
  api/                       org.eclipse.daanse.jdbc.db.api        (nur meta + Introspektions-Interfaces)
  record/                    org.eclipse.daanse.jdbc.db.record     (komplett)
  impl/                      org.eclipse.daanse.jdbc.db.impl
  dialect/
    metadata/                org.eclipse.daanse.jdbc.db.dialect.metadata  (NEU: <Db>MetadataProvider)
  importer/csv/              org.eclipse.daanse.jdbc.db.importer.csv
```

---

## 4. Abhängigkeits-Kanten nach dem Umbau (Zielzustand)

```
sql.api               → (JDK / slf4j)                    # keine jdbc.db-Kante
sql.dialect.api       → sql.api
sql.dialect.db.*      → sql.dialect.api, sql.dialect.db.common, sql.api

jdbc.db.api (Rest)    → sql.api.schema                    # Introspektion referenziert Schema-Modell
jdbc.db.record        → sql.api                            # Records implementieren Schema-Interfaces
jdbc.db.dialect.metadata.<db> → jdbc.db.api (MetadataProvider), jdbc.db.record, sql.api
jdbc.db.impl          → jdbc.db.api, jdbc.db.record, (sql.api)
jdbc.db.importer.csv  → jdbc.db.api, jdbc.db.record, sql.dialect.api   (+ impl runtime)  [ETL]
```

Alle `jdbc.db`-Kanten zeigen einseitig auf `sql.*`. `sql.*` hat **keine** `jdbc.db`-Kante → **kein
Zyklus**, `sql` unabhängig baubar/publizierbar.

---

## 5. Minimaler SQL-Typ-Satz (Kontrolle)

Der SQL-Eigencode (guard/deparser/statement) braucht weiterhin nur die 7 Blatt-Typen
(`Datatype`, `BestFitColumnType`, `BitOperation`, `OrderedColumn`, `SchemaReference`, `TableReference`,
`ColumnReference`) — jetzt aus `sql.api`. Die Dialekte fügen `api.schema` (Insel, DDL-Vokabular) hinzu.
**Kein** `record`, **kein** `api.meta` auf der SQL-Seite.
