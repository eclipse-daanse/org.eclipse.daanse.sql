<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 08 – Verifikation & Abnahme

Zurück zum [Master-Plan](README.md).

---

## 1. Build-Reihenfolge

```bash
# 1) SQL-Repo bauen, testen, lokal installieren
cd org.eclipse.daanse.sql
mvn -q clean install

# 2) jdbc.db gegen die frisch installierten sql.*-Snapshots
cd ../org.eclipse.daanse.jdbc.db
mvn -q clean verify
```

`sql` **muss** vor `jdbc.db` installiert sein (einseitige Abhängigkeit, siehe
[02](02-dependency-and-layering.md)).

---

## 2. Testumfang

- **SQL-Repo:**
  - `statement/demo`-Integrationstests (H2 in-memory + MSSQL via Testcontainers) laufen grün.
  - `guard/jsqltranspiler`-Tests (nutzen `sql.dialect.db.h2/common`) grün.
  - `deparser`-Tests grün.
  - Migrierte Dialekt-Unit-Tests (Generator-/Quoter-Tests) grün.
  - Round-Trip-Tests mit Cross-Repo-`impl`-Abhängigkeit: pro Dialekt Status dokumentieren
    (umgeschrieben | `@Disabled`, siehe [07](07-anomalies-and-risks.md) §3).
- **jdbc.db-Repo:**
  - `impl`-Tests (DDL-Round-Trip) grün gegen `sql.dialect.db.h2/common`.
  - `importer/csv`-ETL-Tests (`CsvDataLoaderTest`) grün.

---

## 3. Strukturelle Kontrollen

```bash
# Keine alten Dialekt-/api-Pakete mehr im SQL-Repo:
grep -rl "org.eclipse.daanse.jdbc.db" org.eclipse.daanse.sql/ --include=*.java   # → leer

# SQL-Repo referenziert keine externen jdbc.db.dialect-Artefakte mehr:
grep -rn "jdbc.db.dialect" org.eclipse.daanse.sql/ --include=pom.xml             # → leer

# jdbc.db enthält keine api/record/dialect-Module mehr im Reactor:
grep -nE "<module>(api|record|dialect)</module>" org.eclipse.daanse.jdbc.db/pom.xml   # → leer
```

---

## 4. Definition of Done — Dokumentensatz (dieser Ordner)

- [ ] `README.md` + `01`–`08` vorhanden, gegenseitig verlinkt, konsistent.
- [ ] Split-Tabelle deckt jedes Modul beider Repos genau einmal ab.
- [ ] Rename-Mapping vollständig (jedes mitgezogene Paket + ArtifactId hat ein Ziel).
- [ ] Migrationsreihenfolge respektiert den Abhängigkeitsgraph.
- [ ] Zirkularitätsauflösung nachvollziehbar begründet.
- [ ] Anomalien (redshift/snowflake, Java 21, Cross-Repo-Tests) erfasst.

## 5. Definition of Done — Code-Umsetzung (spätere Phase)

- [ ] `mvn clean install` im SQL-Repo grün (Java 21).
- [ ] SQL-Repo hat **keine** externe `jdbc.db.*`-Abhängigkeit mehr.
- [ ] `mvn clean verify` im jdbc.db-Repo grün gegen publizierte `sql.*`-Snapshots.
- [ ] `statement/demo`-Integrationstests (H2/MSSQL) laufen.
- [ ] Lizenz-Header- und Javadoc-CI-Checks in beiden Repos grün.
- [ ] Aktive Dialekt-Untermenge (10 Module) vollständig unter `sql/dialect/db/` gebaut.
- [ ] redshift/snowflake-Anomalie für die spätere Welle dokumentiert (nicht jetzt berührt).

---

## 6. Rollback

Da der Umzug in Arbeitszweigen beider Repos erfolgt und `git mv` genutzt wird, ist ein Rollback durch
Verwerfen der Zweige möglich. Keine der Änderungen wird gemergt, bevor beide Repos grün bauen und die
DoD erfüllt ist.
