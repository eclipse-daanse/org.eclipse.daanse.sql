<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 04 – Migrationsprozedur (Schritt für Schritt)

Zurück zum [Master-Plan](README.md).

Modulweiser Umzug in **Abhängigkeitsreihenfolge**. Jeder Schritt endet mit einem grünen Build,
bevor der nächste beginnt.

---

## 0. Vorbereitung

- Arbeitszweig in **beiden** Repos anlegen (z. B. `feat/dialect-migration`).
- Beide Repos liegen als Geschwister unter `…/git/daanse/`.
- Für Historie-Erhaltung `git mv` verwenden; alternativ `git filter-repo`, falls die volle
  Datei-Historie über Repo-Grenzen gewünscht ist (optional, siehe [07](07-anomalies-and-risks.md)).

---

## 1. Welle A – Fundament (`api`, `record`)

Für **`api`** (dann analog `record`):

1. `git mv jdbc.db/api sql/api` (Verzeichnis in den SQL-Reactor).
2. Paketpfade `src/main/java/org/eclipse/daanse/jdbc/db/api/…` → `…/sql/api/…` verschieben.
3. In allen Dateien `org.eclipse.daanse.jdbc.db.api` → `org.eclipse.daanse.sql.api` ersetzen
   (`package`, `import`, `package-info`).
4. `api/pom.xml`: Parent auf den SQL-Root setzen, ArtifactId → `org.eclipse.daanse.sql.api`.
5. Root `sql/pom.xml`: `<module>api</module>` ergänzen.
6. `record` genauso; `record/pom.xml`-Dep `…jdbc.db.api` → `…sql.api` (reaktor-intern,
   `${project.version}`).
7. `mvn -q -pl api,record -am verify`.

---

## 2. Welle B – Dialekt-Kern

Reihenfolge: **`dialect/api` → `dialect/db/common` → `dialect/db/test-support`.**

1. Neue Aggregatoren anlegen: `sql/dialect/pom.xml` (packaging=pom, Parent=SQL-Root) und
   `sql/dialect/db/pom.xml` (packaging=pom, Parent=`sql.dialect`).
2. `git mv jdbc.db/dialect/api sql/dialect/api`; Paket-Rename `…jdbc.db.dialect.api` →
   `…sql.dialect.api`; Pom-Dep `…jdbc.db.api` → reaktor-intern `…sql.api`.
3. `git mv jdbc.db/dialect/db/common sql/dialect/db/common`; Rename; Dep → `sql.dialect.api`.
4. `git mv jdbc.db/dialect/db/test-support sql/dialect/db/test-support`; Rename; Deps → `sql.api`,
   `sql.dialect.api`.
5. `sql.dialect` in Root-`<modules>` eintragen; `common`/`test-support` in `sql/dialect/db/pom.xml`.
6. `mvn -q -pl org.eclipse.daanse.sql.dialect.api,…common,…test-support -am verify`.

---

## 3. Welle C – Konkrete Dialekte (aktive Untermenge)

Für jedes von `duckdb, h2, mariadb, mssqlserver, mysql, oracle, postgresql, sqlite`
(**`mysql` vor `mariadb`**, da `mariadb → mysql`):

1. `git mv jdbc.db/dialect/db/<db> sql/dialect/db/<db>`.
2. Paket-Rename `…jdbc.db.dialect.db.<db>` → `…sql.dialect.db.<db>`.
3. `pom.xml`-Deps umstellen:
   - compile: `…dialect.db.common` → `sql.dialect.db.common`, `…dialect.api` → `sql.dialect.api`,
     `…record` → `sql.record`, `…api` → `sql.api` (alle reaktor-intern, `${project.version}`).
   - test: `…jdbc.db.impl` → siehe unten (Cross-Repo!), `…dialect.db.test-support` →
     `sql.dialect.db.test-support`; JDBC-Treiber/Testcontainers-Versionen aus dem Quell-Pom
     übernehmen.
4. `<module><db></module>` in `sql/dialect/db/pom.xml` eintragen.
5. `mvn -q -pl org.eclipse.daanse.sql.dialect.db.<db> -am verify`.

> **Test-Kante `dialect.db.<db> → jdbc.db.impl`:** Einige Dialekt-Tests (mysql, mariadb, mssqlserver,
> oracle, postgresql, derby) nutzen `DatabaseServiceImpl`. Da `impl` in `jdbc.db` bleibt, würde dies
> eine **Cross-Repo-Test-Abhängigkeit** `sql → jdbc.db` erzeugen und den in
> [02](02-dependency-and-layering.md) aufgelösten Zyklus über die Hintertür wieder einführen.
> **Vorgabe:** Diese Round-Trip-Tests wandern mit `impl` **nicht** mit; sie werden entweder
> (a) auf `sql`-eigene Test-Fixtures umgestellt, oder (b) temporär als `@Disabled` markiert und in
> `jdbc.db` (wo `impl` liegt) als Integrationstests weitergeführt. Entscheidung pro Dialekt in
> [08](08-verification.md) dokumentieren. In der aktiven Untermenge betrifft das mysql, mariadb,
> mssqlserver, oracle, postgresql.

---

## 4. Welle D – SQL-Konsumenten umstellen

Siehe [05](05-consumer-rewiring.md) §1: `guard`, `deparser`, `statement`, `statement/demo` von den
externen `jdbc.db.*`-Deps auf die neuen reaktor-internen `sql.*`-Module umstellen, Imports anpassen.
Java-21-Anhebung des SQL-Roots (siehe [06](06-build-and-java21.md)). Danach `mvn -q verify` im
gesamten SQL-Reactor.

---

## 5. Welle E – `jdbc.db`-Rückbau

Siehe [05](05-consumer-rewiring.md) §2: In `jdbc.db` die umgezogenen Module aus dem Reactor entfernen
(`api`, `record`, `dialect` aus Root-`<modules>`) und `impl`/`importer` auf die **publizierten**
`sql.*`-Snapshots umstellen. `mvn -q verify` im `jdbc.db`-Reactor (setzt voraus, dass `sql`-Snapshots
im lokalen/Sonatype-Repo verfügbar sind → `sql` zuerst `mvn install`).

---

## 6. Reihenfolge-Kurzform

```
A: api → record
B: dialect.api → dialect.db.common → test-support
C: mysql → mariadb;  duckdb, h2, mssqlserver, oracle, postgresql, sqlite (parallel)
D: SQL-Konsumenten (guard/deparser/statement) + Java 21
E: jdbc.db-Rückbau (impl/importer auf sql.*-Snapshots)
```
