<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 06 – Build, Reactor & Java-21-Anhebung

Zurück zum [Master-Plan](README.md).

---

## 1. Java-Version anheben (17 → 21)

Das SQL-Repo baut heute mit Java 17 (`sql/pom.xml`), der Dialekt-Code stammt aus einem Java-21-Repo.
Im SQL-Root-Pom:

```xml
<properties>
  ...
  <java.version>21</java.version>
  <java.release>21</java.release>
  <maven.compiler.source>${java.version}</maven.compiler.source>
  <maven.compiler.target>${java.version}</maven.compiler.target>
</properties>
```

Wirkung: gesamtes SQL-Repo kompiliert/testet auf 21. Toolchain/CI muss ein JDK 21 bereitstellen
(`.github/workflows/build_deploy.yml` prüfen; die `java_check_javadoc.yml`-Prüfung ebenfalls auf 21).

---

## 2. Neue Aggregator-Poms

**`sql/dialect/pom.xml`** (ersetzt `jdbc.db/dialect/pom.xml`):

```xml
<parent>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.sql</artifactId>
  <version>${revision}</version>
</parent>
<artifactId>org.eclipse.daanse.sql.dialect</artifactId>
<packaging>pom</packaging>
<modules>
  <module>api</module>
  <module>db</module>
</modules>
```

**`sql/dialect/db/pom.xml`** (packaging=pom, Parent `org.eclipse.daanse.sql.dialect`), `<modules>`:

```
common  test-support  duckdb  h2  mariadb  mssqlserver  mysql  oracle  postgresql  sqlite
```

**Root `sql/pom.xml`** `<modules>` erweitern:

```xml
<modules>
  <module>api</module>
  <module>record</module>
  <module>dialect</module>
  <module>guard</module>
  <module>deparser</module>
  <module>statement</module>
</modules>
```

Reihenfolge in `<modules>` ist unkritisch (Maven sortiert nach Abhängigkeiten), Lesbarkeit halber
aber Fundament zuerst.

---

## 3. dependencyManagement erweitern

Aus den migrierten Modulen benötigte Versionen ins SQL-Root-`dependencyManagement` bzw. in die
jeweiligen Modul-Poms übernehmen (heute im SQL-Root nur slf4j/mockito/assertj vorhanden):

- **Test-Treiber & Container** (Test-Scope, pro Dialekt): `mysql-connector-j`, `mariadb-java-client`,
  `mssql-jdbc`, `ojdbc11`, `postgresql`, `org.xerial:sqlite-jdbc`, `org.duckdb:duckdb_jdbc`,
  `com.h2database:h2`; `org.testcontainers` (mysql, mariadb, mssqlserver, oracle-xe, postgresql,
  junit-jupiter).
- **JUnit 5** (`junit-jupiter-api/engine`) — Versionen aus den Quell-Poms (z. B. 5.12.2)
  vereinheitlichen.
- **assertj** — SQL-Root hat 3.24.2, einige jdbc.db-Module nutzen 3.26.0 → auf eine Version
  vereinheitlichen.
- **`biz.aQute.bnd:biz.aQute.bndlib`** — compile-Dep von `oracle`.

Die genauen Versionen aus den jeweiligen `dialect/db/<db>/pom.xml` der Quelle übernehmen; im Zweifel
die höhere/neuere wählen und zentral im `dependencyManagement` pinnen.

---

## 4. Parent-Pom & Snapshot-Repo (unverändert)

- Parent bleibt `org.eclipse.daanse:org.eclipse.daanse.pom.parent:0.0.7` (liefert bnd-/flatten-Plugins).
- `${revision}` = `0.0.1-SNAPSHOT`, `.flattened-pom.xml`-Mechanik unverändert.
- Snapshot-Repo Sonatype `central.sonatype.com/repository/maven-snapshots` unverändert.
- OSGi: keine `bnd.bnd`-Dateien; DS-`@Component`-getrieben → `Bundle-SymbolicName` folgt der
  ArtifactId automatisch.

---

## 5. Build-Kommandos

```bash
# SQL-Repo vollständig bauen + lokal installieren (Voraussetzung für jdbc.db)
cd org.eclipse.daanse.sql && mvn -q clean install

# Einzelnes Modul inkl. Abhängigkeiten
mvn -q -pl org.eclipse.daanse.sql.dialect.db.mysql -am verify

# jdbc.db gegen die frisch installierten sql.*-Snapshots
cd ../org.eclipse.daanse.jdbc.db && mvn -q clean verify
```
