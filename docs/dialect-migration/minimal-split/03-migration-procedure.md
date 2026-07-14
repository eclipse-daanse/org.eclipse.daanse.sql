<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 03 – Migrationsprozedur (Schritt für Schritt)

Zurück zum [Minimal-Split-Plan](README.md).

Reihenfolge: **erst entkoppeln (in `jdbc.db`), dann splitten, dann verschieben.** Jede Welle endet mit
grünem Build.

---

## Welle R — Rollen-Trennung (in `jdbc.db`)

Vollständig in [01](01-role-separation.md) beschrieben. Kurzfassung:

1. Pro großem Dialekt (h2, mariadb, mssqlserver, mysql, oracle, postgresql): Rolle-A-Methoden +
   private Helfer in `dialect/metadata/<db>/.../<Db>MetadataProvider.java` verschieben.
2. `Dialect.java`: `extends MetadataProvider` entfernen, Import streichen.
3. Begleiter als OSGi-Komponenten bereitstellen; Aufrufer/Tests auf den Begleiter umstellen
   ([04](04-consumer-rewiring.md)).
4. **Gate:** `mvn -q verify` in `jdbc.db` grün; Dialekt-Main frei von `record`/`api.meta`.

> Nach Welle R ist der Refactor abgeschlossen und für sich nutzbar — der Repo-Umzug (Wellen S–D) kann
> unabhängig terminiert werden.

---

## Welle S — API-Split (Vorbereitung des SQL-Modells)

1. Neues Modul `sql/api` anlegen (Root-Reactor-Eintrag); Parent = SQL-Root.
2. Pakete `api.schema`, `api.type`, `api.sql` aus `jdbc.db/api` **herauslösen**:
   - `git mv` der drei Paketbäume nach `sql/api/src/main/java/org/eclipse/daanse/sql/api/{schema,type,sql}`.
   - Rename `org.eclipse.daanse.jdbc.db.api.{schema,type,sql}` → `org.eclipse.daanse.sql.api.{…}` in
     allen mitgezogenen Dateien.
3. Im verbleibenden `jdbc.db/api` (nur `api.meta` + top-level Introspektion): Importe der drei
   ausgelagerten Pakete auf `org.eclipse.daanse.sql.api.*` umstellen; Pom-Dep auf `sql.api` (extern,
   `0.0.1-SNAPSHOT`) ergänzen.
4. `record` und `impl`: Importe von `…jdbc.db.api.{schema,type,sql}` → `…sql.api.*`; Pom-Dep auf
   `sql.api` ergänzen.
5. **Gate:** `sql/api` baut isoliert (`mvn -q -pl org.eclipse.daanse.sql.api -am verify`); `jdbc.db`
   baut gegen den `sql.api`-Snapshot.

> `record` wird **nicht** verschoben — nur seine Importe auf `sql.api` umgebogen.

---

## Welle D — Dialekt-Umzug nach SQL

Reihenfolge: `sql.dialect.api` → `sql.dialect.db.common` → `test-support` → konkrete Dialekte
(`mysql` vor `mariadb`).

1. `git mv jdbc.db/dialect/api sql/dialect/api`; Rename `…jdbc.db.dialect.api` → `…sql.dialect.api`;
   Pom-Dep auf `sql.api` (reaktor-intern). `Dialect` ist bereits ohne `MetadataProvider` (Welle R).
2. `git mv jdbc.db/dialect/db/common sql/dialect/db/common`; Rename; Dep → `sql.dialect.api`.
3. `test-support` analog.
4. Aktive Dialekte (`duckdb, h2, mariadb, mssqlserver, mysql, oracle, postgresql, sqlite`):
   `git mv` nach `sql/dialect/db/<db>`; Rename `…dialect.db.<db>` → `…sql.dialect.db.<db>`; Pom-Deps
   → `sql.dialect.api`, `sql.dialect.db.common`, `sql.api` (reaktor-intern). **Kein** `record`-Dep,
   **kein** `api.meta`-Dep mehr (dank Welle R).
5. Aggregatoren `sql/dialect/pom.xml`, `sql/dialect/db/pom.xml` + Root-`<modules>` ([06](06-build-and-verification.md)).
6. **Gate:** SQL-Reactor baut; Dialekt-Module haben keine `jdbc.db`-Deps.

> Die `<Db>MetadataProvider`-Begleiter bleiben in `jdbc.db/dialect/metadata` — sie ziehen **nicht** mit.

---

## Welle C — Konsumenten & jdbc.db-Rückbau

Siehe [04](04-consumer-rewiring.md): SQL-Konsumenten (guard/deparser/statement) auf reaktor-intern;
`jdbc.db`-Konsumenten (`impl`, `importer`, `dialect.metadata`) auf publizierte `sql.*`-Snapshots;
`dialect/api`+`dialect/db/aktiv` aus dem `jdbc.db`-Reactor entfernen (Begleiter-Modul bleibt).

---

## Welle V — Per-Version-Härtung (optional)

Siehe [05](05-per-version-hardening.md). Kann jederzeit nach Welle D erfolgen und ist unabhängig.

---

## Reihenfolge-Kurzform

```
R: Rollen-Trennung (jdbc.db)        → Gate: jdbc.db grün, Dialekt-Main ohne record/api.meta
S: api-Split (schema/type/sql → sql.api)
D: dialect.api + aktive dialect.db.* → sql
C: Konsumenten + jdbc.db-Rückbau
V: (optional) Per-Version-Härtung
B: Build/Java21/Verifikation (durchgehend)
```
