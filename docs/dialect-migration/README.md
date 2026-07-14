<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# Migration: Dialekte + Dialekt-API von `jdbc.db` nach `sql`

> 🌐 Sprache: **Deutsch** (diese Datei) · [Русский](ru/README.md)

**Status:** Plan / Spezifikation (Code-Umzug noch nicht ausgeführt)
**Betroffene Repos:** `org.eclipse.daanse.sql` (Ziel) · `org.eclipse.daanse.jdbc.db` (Quelle)

Dieser Ordner ist der **Master-Plan** für die Verlagerung des Datenbank-Dialekt-Stacks aus dem
Repository `org.eclipse.daanse.jdbc.db` in das Repository `org.eclipse.daanse.sql`. Er beschreibt
*was* umzieht, *warum*, *in welcher Reihenfolge* und *wie* — als ausführbare Arbeitsanweisung für
die spätere Umsetzung.

---

## 1. Motivation

Das SQL-Repo (`guard`, `deparser`, `statement`) ist bereits **dialekt-bewusst**, definiert die
Dialekt-Abstraktion aber nicht selbst. Es konsumiert sie heute als **externe Maven-Artefakte** aus
`org.eclipse.daanse.jdbc.db`:

- `org.eclipse.daanse.jdbc.db.dialect.api` (`Dialect`, `IdentifierQuotingPolicy`, `generator.*`)
- `org.eclipse.daanse.jdbc.db.dialect.db.common` (`AnsiDialect`)
- `org.eclipse.daanse.jdbc.db.dialect.db.h2` / `.mysql` / `.mssqlserver`
- das Metadaten-/Typ-Modell `…jdbc.db.api.schema.*` und `…api.type.BestFitColumnType`

**Ziel:** Das SQL-Repo soll den **kompletten SQL-Generierungs- und Dialekt-Stack besitzen**. Der
Dialekt-Code, die Dialekt-API und das von ihnen benötigte Metadaten-/Typ-Modell (`api` + `record`)
ziehen nach `sql` um. `jdbc.db` behält nur die reine **Metadaten-Introspektions-Engine** (`impl`)
und die **ETL-Schicht** (`importer/csv`) und wird zum **einseitigen Downstream-Konsumenten** von
`sql`.

**Randbedingung:** Rein ETL-basierte Bestandteile werden *nicht* nach `sql` übernommen.

---

## 2. Getroffene Grundsatzentscheidungen

| Thema | Entscheidung | Detaildokument |
|---|---|---|
| Paket-Namen | Alle mitgezogenen Module werden auf `org.eclipse.daanse.sql.*` umbenannt | [03](03-package-rename-map.md) |
| Geteilte API-Grenze | `api` + `record` ziehen **mit** um → Einweg-Schichtung, kein Repo-Zyklus | [02](02-dependency-and-layering.md) |
| Dialekt-Umfang | Nur die im Reactor **aktive** Untermenge (10 Module) | [01](01-inventory-and-scope.md) |
| Java-Version | SQL-Repo wird von Java **17 → 21** angehoben | [06](06-build-and-java21.md) |

---

## 3. Split-Überblick

### Zieht nach `org.eclipse.daanse.sql` um (+ Paket-Rename)

| Quelle in `jdbc.db` | Ziel-Artefakt in `sql` | Rolle |
|---|---|---|
| `api` | `org.eclipse.daanse.sql.api` | Metadaten-/Schema-/Typ-/SQL-Modell |
| `record` | `org.eclipse.daanse.sql.record` | Record-Implementierungen dazu |
| `dialect/api` | `org.eclipse.daanse.sql.dialect.api` | Dialekt-API (`Dialect`, `DialectFactory`, `generator`, `capability`, `type`) |
| `dialect/db/common` | `org.eclipse.daanse.sql.dialect.db.common` | `AbstractJdbcDialect`, `AnsiDialect`, Jdbc*-Generatoren/Quoter |
| `dialect/db/test-support` | `org.eclipse.daanse.sql.dialect.db.test-support` | Test-Helfer |
| `dialect/db/{duckdb,h2,mariadb,mssqlserver,mysql,oracle,postgresql,sqlite}` | `org.eclipse.daanse.sql.dialect.db.<db>` | 8 aktive konkrete Dialekte |

### Bleibt in `org.eclipse.daanse.jdbc.db` (wird Konsument von `sql`)

| Modul | Rolle | Neue Abhängigkeit |
|---|---|---|
| `impl` | Metadaten-Introspektions-Engine (`DatabaseServiceImpl`, `CachingDatabaseService`) | compile→ `sql.api`, `sql.record`, `sql.dialect.api`; test→ `sql.dialect.db.common/h2` |
| `importer` + `importer/csv` | **ETL** (CSV-Import) | compile→ `sql.api`, `sql.record`, `sql.dialect.api`; runtime→ `impl` |
| `dialect/db/*` (inaktiv, ~27) | vorerst geparkt | spätere Migrationswelle |

---

## 4. Ziel-Reactor-Struktur im SQL-Repo

```
org.eclipse.daanse.sql/
├── pom.xml                     # Root-Aggregator: <modules> um api, record, dialect erweitern; Java 21
├── api/                        # NEU (leaf)
├── record/                     # NEU (leaf)
├── dialect/                    # NEU (Aggregator, packaging=pom)
│   ├── api/                    #   leaf
│   └── db/                     #   Aggregator, packaging=pom
│       ├── common/  test-support/
│       ├── duckdb/  h2/  mariadb/  mssqlserver/
│       └── mysql/  oracle/  postgresql/  sqlite/
├── deparser/                   # bestehend – Dialekt-Deps von extern → reaktor-intern
├── guard/                      # bestehend – dito
└── statement/                  # bestehend – dito
```

---

## 5. Migrationswellen (Reihenfolge)

1. **Welle A – Fundament:** `api` → `record` nach `sql` (Rename, Reactor-Eintrag).
2. **Welle B – Dialekt-Kern:** `dialect/api` → `dialect/db/common` → `test-support`.
3. **Welle C – Konkrete Dialekte:** die 8 aktiven DB-Module (`mysql` vor `mariadb`).
4. **Welle D – SQL-Konsumenten:** `guard`/`deparser`/`statement` von externen Deps auf
   reaktor-interne Module umstellen; Java-21-Anhebung.
5. **Welle E – jdbc.db-Rückbau:** `impl`/`importer` auf publizierte `sql.*`-Snapshots umstellen,
   umgezogene Module aus dem `jdbc.db`-Reactor entfernen.

Jede Welle ist für sich baubar (`mvn -q verify`), bevor die nächste beginnt.

---

## 6. Detaildokumente

| # | Dokument | Inhalt |
|---|---|---|
| 01 | [Inventar & Umfang](01-inventory-and-scope.md) | Vollständige Modulliste, Klassifizierung, aktiv/inaktiv |
| 02 | [Abhängigkeit & Schichtung](02-dependency-and-layering.md) | Graph, Zirkularität + Auflösung, Ziel-Layering |
| 03 | [Paket- & Artefakt-Rename](03-package-rename-map.md) | Vollständiges Old→New-Mapping |
| 04 | [Migrationsprozedur](04-migration-procedure.md) | Schritt-für-Schritt, modulweise |
| 05 | [Konsumenten umverdrahten](05-consumer-rewiring.md) | SQL- und jdbc.db-seitige Pom-/Import-Änderungen |
| 06 | [Build & Java 21](06-build-and-java21.md) | Reactor-Poms, Versionen, Java-Anhebung |
| 07 | [Anomalien & Risiken](07-anomalies-and-risks.md) | redshift/snowflake-Swap, Java, spätere Wellen |
| 08 | [Verifikation & Abnahme](08-verification.md) | Build-Befehle, Testumfang, DoD |
| 09 | [Minimaler Schnitt (2. Analyse)](09-minimal-split-analysis.md) | Alternative zum Wholesale: was `sql` wirklich braucht, Rollen-Split, 4 Optionen |
| — | [**Minimal-Split — vollständiger Plan**](minimal-split/README.md) | Ausführbarer Plan für den Rollen-Split (O1/O2) als Alternative zu 01–08 |

> **Hinweis:** Die Dokumente 01–08 beschreiben den **Wholesale-Umzug** (Plan 1). Dokument **09** ist
> eine ergänzende *zweite Analyse* zum **minimalen** Schnitt; der Ordner
> [`minimal-split/`](minimal-split/README.md) enthält dazu den **vollständigen ausführbaren Plan**
> (Rollen-Split). Die beiden Pläne sind Alternativen — nicht beide umsetzen.

---

## 7. Definition of Done (Dokumentensatz)

- [ ] Alle 9 Dokumente vorhanden, untereinander verlinkt, konsistent.
- [ ] Split-Tabelle deckt **jedes** Modul beider Repos genau einmal ab.
- [ ] Rename-Mapping vollständig (jedes mitgezogene Paket + ArtifactId hat ein Ziel).
- [ ] Migrationsreihenfolge respektiert den Abhängigkeitsgraph (keine Vorwärtsverweise).
- [ ] Auflösung der Zirkularität nachvollziehbar begründet.

**DoD der späteren Code-Umsetzung:** `mvn -q verify` grün in beiden Repos; SQL-Repo baut ohne
externe `jdbc.db.dialect.*`-Deps; `jdbc.db` baut gegen publizierte `sql.*`-Snapshots;
`statement/demo`-Integrationstests (H2/MSSQL) laufen.
