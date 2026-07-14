<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 07 – Anomalien & Risiken

Zurück zum [Master-Plan](README.md).

---

## 1. redshift/snowflake-Vertauschung (⚠ Datenintegrität)

Die beiden Maven-Module `dialect/db/redshift` und `dialect/db/snowflake` sind **gekreuzt** befüllt
(durch Lesen der `package`-Deklarationen verifiziert):

| Modulverzeichnis / ArtifactId | Enthaltenes Paket + Klasse |
|---|---|
| `dialect/db/redshift` (`…dialect.db.redshift`) | `…dialect.db.snowflake` · `SnowflakeDialect` / `SnowflakeDialectFactory` |
| `dialect/db/snowflake` (`…dialect.db.snowflake`) | `…dialect.db.redshift` · `RedshiftDialect` / `RedshiftDialectFactory` |

- Beide Module sind **inaktiv** und **nicht** Teil der jetzigen aktiven Untermenge → aktuell **kein**
  Handlungsbedarf.
- **Beim späteren Nachziehen zwingend korrigieren:** entweder Verzeichnis/ArtifactId oder
  Paket/Klasse angleichen, sodass Snowflake-Code im `snowflake`-Modul und Redshift-Code im
  `redshift`-Modul liegt. Ein rein mechanischer Umzug nach Verzeichnisname würde die
  Fehlbenennung zementieren.
- Zusatzkante beachten: das `snowflake`-Modul (mit Redshift-Code) hängt an `postgresql`.

---

## 2. Java 17 → 21

- Der Dialekt-Code ist für Java 21 geschrieben; das SQL-Repo wird angehoben (siehe
  [06](06-build-and-java21.md)). Risiko gering, aber CI/Toolchain muss JDK 21 bereitstellen.
- Alle bestehenden SQL-Module (`guard`/`deparser`/`statement`) werden dadurch ebenfalls auf 21
  gebaut — kein bekanntes Hindernis, aber Regressionstest ist Teil der Abnahme.

---

## 3. Cross-Repo-Test-Abhängigkeit `dialect.db.<db> → jdbc.db.impl`

Round-Trip-Tests einiger Dialekte (mysql, mariadb, mssqlserver, oracle, postgresql, derby) nutzen
`DatabaseServiceImpl` aus `impl`. Da `impl` in `jdbc.db` bleibt, darf diese Test-Kante **nicht** als
`sql → jdbc.db`-Dependency bestehen bleiben (würde den aufgelösten Zyklus reaktivieren). Optionen
(pro Dialekt in [08](08-verification.md) festhalten):

1. **Umschreiben** auf SQL-repo-eigene Test-Fixtures (bevorzugt, wo mit vertretbarem Aufwand möglich).
2. **Temporär `@Disabled`** und die Integrationstests in `jdbc.db` (bei `impl`) weiterführen.

---

## 4. Inaktive Dialekte (spätere Welle)

~27 Module + `configurable` liegen auskommentiert in `jdbc.db/dialect/db/`. Sie bleiben vorerst dort.
Risiken beim Nachziehen: die redshift/snowflake-Vertauschung (§1) und Inter-Dialekt-Ketten
(`greenplum/netezza → postgresql`, `impala → hive`, `vectorwise → ingres`, `sqlstream → luciddb`,
`infobright → mysql`) — Reihenfolge beachten.

---

## 5. Historie-Erhaltung (optional)

`git mv` erhält die Historie innerhalb eines Repos. Für **volle** Datei-Historie über die
Repo-Grenze (jdbc.db → sql) wäre `git filter-repo` mit anschließendem Merge nötig — deutlich
aufwändiger. Empfehlung: `git mv` genügt; die Herkunft ist über diesen Dokumentensatz nachvollziehbar.

---

## 6. `api.meta`-Benennung

`api.meta` (JDBC-`DatabaseMetaData`-Introspektion) ist semantisch JDBC-nah, wird aber der Konsistenz
halber nach `sql.api.meta` umbenannt. Falls später eine sauberere Trennung „reines SQL-Modell“ vs.
„JDBC-Introspektion“ gewünscht ist, kann `api.meta` in einem Folgeschritt separiert werden — **nicht**
Teil dieser Migration.

---

## 7. Risiko-Matrix (Kurzfassung)

| Risiko | Eintritt | Wirkung | Gegenmaßnahme |
|---|---|---|---|
| redshift/snowflake falsch benannt | später | falsche Dialektauswahl zur Laufzeit | §1, beim Nachziehen korrigieren |
| Zyklus über Test-Kante reaktiviert | mittel | Build/Release blockiert | §3, Tests umschreiben/disablen |
| JDK-21-Toolchain fehlt in CI | gering | Build rot | CI-Workflow auf 21 stellen |
| Versions-Divergenz (assertj/junit) | gering | Build-Warnungen/-Fehler | zentral pinnen ([06](06-build-and-java21.md)) |
| Release-Reihenfolge vertauscht | mittel | jdbc.db baut nicht | `sql` zuerst installieren/publizieren |
