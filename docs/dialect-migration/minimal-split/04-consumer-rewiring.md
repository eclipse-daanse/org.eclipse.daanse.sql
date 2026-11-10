<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 04 – Konsumenten umverdrahten & OSGi

Zurück zum [Minimal-Split-Plan](README.md).

Drei Konsumenten-Gruppen sind betroffen: (1) SQL-Eigencode, (2) `jdbc.db`-Module, (3) die
Bereitstellung/Verdrahtung der neuen `MetadataProvider`-Begleiter.

---

## 1. SQL-Repo-Konsumenten (reaktor-intern)

Wie beim Wholesale-Plan ([../05-consumer-rewiring.md](../05-consumer-rewiring.md) §1), aber die
Blatt-Typen kommen jetzt aus `sql.api` statt `jdbc.db.api`:

| Modul | Neu (reaktor-intern) |
|---|---|
| `statement/api` | `sql.dialect.api.generator.*`, `sql.api.schema.{Schema,Table,Column}Reference`, `sql.api.type.*`, `sql.api.sql.*` |
| `statement/impl` | `sql.dialect.db.common`, `sql.dialect.db.mysql`, `sql.api.*` |
| `statement/demo` | `sql.dialect.db.common/mysql/mssqlserver`, `sql.api.*` |
| `guard/jsqltranspiler` | `sql.dialect.db.h2`, `sql.dialect.db.common` |
| `deparser/api`, `deparser/jsqlparser` | `sql.dialect.api` |

Import-Ersetzung: `org.eclipse.daanse.jdbc.db.dialect` → `org.eclipse.daanse.sql.dialect`;
`org.eclipse.daanse.jdbc.db.api.{schema,type,sql}` → `org.eclipse.daanse.sql.api.{…}`. Der SQL-Code
berührt **nie** `record`/`api.meta` → keine weiteren Änderungen.

---

## 2. `jdbc.db`-Konsumenten (publizierte `sql.*`-Snapshots)

### 2.1 `impl` (Introspektions-Motor)

- Importe `…jdbc.db.api.{schema,type,sql}` → `…sql.api.*` (Welle S).
- Behält `…jdbc.db.api` (meta + `MetadataProvider`/`DatabaseService`) **lokal** und `…record` **lokal**.
- Pom: Dep auf `sql.api` (extern). **Kein** `dialect.*`-Dep mehr im Produktionscode (Motor kennt nur
  `MetadataProvider`).
- Tests, die bisher einen `Dialect` als Provider durchreichten, nutzen jetzt den passenden
  `<Db>MetadataProvider` (aus `jdbc.db.dialect.metadata`) oder `MetadataProvider.EMPTY`.

### 2.2 `jdbc.db.dialect.metadata.<db>` (neu — die Begleiter)

- Pom: compile-Dep auf `jdbc.db.api` (MetadataProvider/meta), `jdbc.db.record`, und `sql.api` (Schema-
  Interfaces, die die Records implementieren). Runtime/DS nach Bedarf.
- Enthält je `<Db>MetadataProvider` (aus Welle R).

### 2.3 `importer/csv` (ETL)

- Importe `…jdbc.db.api.{schema,type}` → `…sql.api.*`; `…dialect.api.Dialect`/`DialectFactory` →
  `…sql.dialect.api.*`. `record`/`impl`/`fastcsv`/`io.fs.watcher.*` unverändert.
- **Hinweis:** `CsvDataImporter` nutzt `Dialect` nur für Quoting/DDL (Rolle B) — nach dem Split
  weiterhin gültig. Falls es zusätzlich Metadaten *liest*, dafür künftig einen `MetadataProvider`
  verwenden.

### 2.4 Reactor-Bereinigung `jdbc.db/pom.xml`

- Aus `<modules>` entfernen: `dialect` (die aktive `api`+`db`-Untermenge, jetzt in SQL).
- Ergänzen: `dialect/metadata` (Begleiter-Sammelmodul).
- `api`, `record`, `impl`, `importer` bleiben.

---

## 3. OSGi-Verdrahtung der Provider

**Ist-Zustand:** Jede `DialectFactory` ist `@Component(service = DialectFactory.class)`, getaggt per
`@DialectName("MYSQL")`. `DatabaseServiceImpl` (`@Component(service = DatabaseService.class)`) nimmt
`MetadataProvider` als **Methoden-Parameter** — der Provider wird also am Aufrufort beschafft, nicht
injiziert.

**Ziel-Zustand:** Der `MetadataProvider`-Begleiter wird als eigener Service veröffentlicht und
auflösbar gemacht:

- Variante A (empfohlen): `<Db>MetadataProvider` als `@Component(service = MetadataProvider.class)`
  mit `@DialectName("MYSQL")`. Ein Aufrufer, der Metadaten für ein Produkt braucht, filtert die
  `MetadataProvider`-Services nach `@DialectName`/Produktname; Fallback `MetadataProvider.EMPTY`.
- Variante B: eine `MetadataProviderFactory` analog zur `DialectFactory`
  (`MetadataProvider create(DialectInitData)`), parallel registriert.

Wichtig: `Dialect` und `MetadataProvider` sind jetzt **getrennte Services**. Wer bisher einen
`Dialect` sowohl zur SQL-Generierung als auch als Metadaten-Provider nutzte, hält künftig **zwei**
Referenzen (den Dialekt aus `sql`, den Provider aus `jdbc.db`), beide über denselben
`@DialectName`/Produktnamen auflösbar.

---

## 4. Build-Vorbedingung

`jdbc.db` (inkl. Begleiter) baut erst grün, wenn `sql.api` + `sql.dialect.*`-Snapshots verfügbar sind:
im SQL-Repo zuerst `mvn -q install`, dann `jdbc.db`. Siehe [06](06-build-and-verification.md).
