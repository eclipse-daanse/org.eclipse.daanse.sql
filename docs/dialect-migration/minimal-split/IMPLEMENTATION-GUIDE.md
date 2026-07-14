<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# Umsetzungs-Anleitung (Schritt für Schritt) — Minimal-Split + Dialekt-Versionen

Zurück zum [Minimal-Split-Plan](README.md).

> 🌐 Sprache: **Deutsch** (diese Datei) · [Русский](../ru/minimal-split/IMPLEMENTATION-GUIDE.md)

**Für wen?** Diese Anleitung ist für einen **Junior-Entwickler** geschrieben. Jeder Schritt sagt dir
**genau**, in welchem **Repo**, welchem **Modul**, welcher **Datei** du **was** tust, mit
**Code-Beispielen** (vorher/nachher) und einem **Prüfbefehl**. Arbeite die Teile **in Reihenfolge** ab
und mache **nach jedem Checkpoint** einen Build. Wenn ein Checkpoint rot ist: **nicht weitermachen** —
erst reparieren.

**Zwei Ziele in dieser Anleitung:**
1. **Minimal-Split** (Teile 1–4): den Dialekt-Stack minimal aus `jdbc.db` nach `sql` holen.
2. **Dialekt-Versionen** (Teil 5): Dialekte fest pro Version bauen (deine Idee, „kein JDBC-Record zum
   Setzen der Eigenschaften").

Am Ende (Teil 6) **baut und läuft alles** in beiden Repos.

---

## Legende / Konventionen

- **Repo `jdbc.db`** = `/home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db`
- **Repo `sql`** = `/home/stbischof/git/daanse/org.eclipse.daanse.sql`
- Paket-Präfix alt: `org.eclipse.daanse.jdbc.db.…` — neu (SQL-Seite): `org.eclipse.daanse.sql.…`
- „Prüfbefehl" heißt: im jeweiligen Repo-Verzeichnis ausführen.
- **Wichtig:** Immer `git mv` benutzen (nicht kopieren+löschen) — das erhält die Git-Historie.

---

# Teil 0 — Vorbereitung

### Schritt 0.1 — Beide Repos, Arbeitszweige

**Repo:** beide. **Aktion:** je einen Branch anlegen.

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db && git checkout -b feat/minimal-split
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql     && git checkout -b feat/minimal-split
```

### Schritt 0.2 — Ausgangs-Build (muss grün sein, bevor du etwas änderst)

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db && mvn -q clean install
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql     && mvn -q clean install
```

> Wenn hier schon etwas rot ist, liegt das **nicht** an dir — kläre das zuerst mit dem Team.

### Schritt 0.3 — Merke dir die 3 Dialekt-Gruppen

- **6 „große" Dialekte** (haben Metadaten-Rolle A): `h2, mariadb, mssqlserver, mysql, oracle, postgresql`
- **2 „kleine" Dialekte** (keine Rolle A): `duckdb, sqlite`
- **Infrastruktur:** `common, test-support`

Nur die **6 großen** bekommen in Teil 1 einen Begleiter.

---

# Teil 1 — Phase R: Rollen-Trennung (komplett im Repo `jdbc.db`)

**Ziel:** Aus jedem großen Dialekt die „Metadaten-lesen"-Methoden (Rolle A) in eine eigene
Begleiter-Klasse verschieben, und `Dialect` von `MetadataProvider` entkoppeln. **Es zieht noch nichts
um** — alles bleibt in `jdbc.db`, muss aber danach grün bauen.

### Schritt 1.1 — Neues Modul `dialect/metadata` anlegen

**Repo:** `jdbc.db`. **Aktion:** neuen Ordner + Aggregator-Pom.

Datei **neu**: `dialect/metadata/pom.xml`
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.eclipse.daanse</groupId>
    <artifactId>org.eclipse.daanse.jdbc.db.dialect</artifactId>
    <version>${revision}</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>org.eclipse.daanse.jdbc.db.dialect.metadata</artifactId>
  <packaging>pom</packaging>
  <name>Eclipse Daanse JDBC DB Dialect Metadata Providers</name>
  <modules>
    <module>mysql</module>
    <module>mariadb</module>
    <module>h2</module>
    <module>mssqlserver</module>
    <module>oracle</module>
    <module>postgresql</module>
  </modules>
</project>
```

Datei **ändern**: `dialect/pom.xml` → in `<modules>` `metadata` ergänzen:
```xml
<modules>
  <module>api</module>
  <module>db</module>
  <module>metadata</module>   <!-- NEU -->
</modules>
```

### Schritt 1.2 — Leaf-Pom für den ersten Begleiter (MySQL)

**Repo:** `jdbc.db`. **Datei neu:** `dialect/metadata/mysql/pom.xml`
```xml
<project ...>
  <parent>
    <groupId>org.eclipse.daanse</groupId>
    <artifactId>org.eclipse.daanse.jdbc.db.dialect.metadata</artifactId>
    <version>${revision}</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>org.eclipse.daanse.jdbc.db.dialect.metadata.mysql</artifactId>
  <name>Eclipse Daanse JDBC DB Metadata Provider MySQL</name>
  <dependencies>
    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
    <!-- MetadataProvider + api.meta + api.schema kommen aus api -->
    <dependency>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.jdbc.db.api</artifactId>
      <version>${revision}</version>
    </dependency>
    <!-- record.schema-Objekte, die zurückgegeben werden -->
    <dependency>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.jdbc.db.record</artifactId>
      <version>${revision}</version>
    </dependency>
    <!-- @DialectName Annotation -->
    <dependency>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.jdbc.db.dialect.api</artifactId>
      <version>${revision}</version>
    </dependency>
  </dependencies>
</project>
```

### Schritt 1.3 — Begleiter-Klasse `MySqlMetadataProvider` anlegen

**Repo:** `jdbc.db`. **Datei neu:**
`dialect/metadata/mysql/src/main/java/org/eclipse/daanse/jdbc/db/dialect/metadata/mysql/MySqlMetadataProvider.java`

Kopiere **den EPL-Header** aus einer bestehenden Datei an den Anfang. Dann:

```java
package org.eclipse.daanse.jdbc.db.dialect.metadata.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.MetadataProvider;
import org.eclipse.daanse.jdbc.db.api.meta.IndexInfo;
import org.eclipse.daanse.jdbc.db.api.meta.IndexInfoItem;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnReference;
import org.eclipse.daanse.jdbc.db.api.schema.ImportedKey;
import org.eclipse.daanse.jdbc.db.api.schema.PrimaryKey;
import org.eclipse.daanse.jdbc.db.api.schema.SchemaReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.jdbc.db.dialect.api.DialectName;
import org.eclipse.daanse.jdbc.db.record.schema.IndexInfoItemRecord;
import org.eclipse.daanse.jdbc.db.record.schema.IndexInfoRecord;
import org.eclipse.daanse.jdbc.db.record.schema.PrimaryKeyRecord;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * MySQL-native metadata provider. Extracted from MySqlDialect (role A) so that
 * the SQL-generation dialect no longer depends on record/api.meta.
 */
@Component(service = MetadataProvider.class, scope = ServiceScope.SINGLETON)
@DialectName("MYSQL")
public class MySqlMetadataProvider implements MetadataProvider {

    // ---- HIER: die aus MySqlDialect verschobenen getAll*/get*-Methoden ----
    // Beispiel: getAllPrimaryKeys (1:1 aus MySqlDialect übernommen)

    @Override
    public Optional<List<PrimaryKey>> getAllPrimaryKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, kcu.COLUMN_NAME, kcu.ORDINAL_POSITION
                FROM information_schema.TABLE_CONSTRAINTS tc
                JOIN information_schema.KEY_COLUMN_USAGE kcu
                  ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY' AND tc.TABLE_SCHEMA = ?
                ORDER BY tc.TABLE_NAME, kcu.ORDINAL_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        Map<String, PkBuilder> pkMap = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("CONSTRAINT_NAME");
                    String tableName = rs.getString("TABLE_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    String key = tableName + "." + constraintName;
                    pkMap.computeIfAbsent(key, k -> new PkBuilder(tableName, constraintName, schemaName))
                            .addColumn(columnName);
                }
            }
        }
        List<PrimaryKey> result = new ArrayList<>();
        for (PkBuilder builder : pkMap.values()) {
            result.add(builder.build());
        }
        return Optional.of(List.copyOf(result));
    }

    // ... ebenso getAllImportedKeys, getAllExportedKeys, getAllIndexInfo,
    //     getUniqueConstraints, getAllTriggers, getAllSequences (alle aus MySqlDialect) ...

    // ---- HIER: die privaten Helfer, die diese Methoden brauchen ----
    private String resolveSchema(String schema, Connection connection) throws SQLException {
        if (schema != null) return schema;
        String catalog = connection.getCatalog();
        if (catalog != null) return catalog;
        return connection.getSchema() != null ? connection.getSchema() : "";
    }

    private static IndexInfoItem.IndexType mapMySqlIndexType(String indexType) {
        if (indexType == null) return IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        return switch (indexType.toUpperCase()) {
            case "HASH" -> IndexInfoItem.IndexType.TABLE_INDEX_HASHED;
            default -> IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        };
    }

    // Builder für zusammengesetzte Primärschlüssel — 1:1 aus MySqlDialect
    private static final class PkBuilder {
        private final String tableName;
        private final String constraintName;
        private final String schemaName;
        private final List<String> columns = new ArrayList<>();
        PkBuilder(String tableName, String constraintName, String schemaName) {
            this.tableName = tableName; this.constraintName = constraintName; this.schemaName = schemaName;
        }
        void addColumn(String c) { columns.add(c); }
        PrimaryKey build() {
            TableReference table = new TableReference(
                    Optional.of(new SchemaReference(schemaName)), tableName);
            List<ColumnReference> cols = new ArrayList<>();
            for (String c : columns) cols.add(new ColumnReference(Optional.of(table), c));
            return new PrimaryKeyRecord(table, cols, Optional.ofNullable(constraintName));
        }
    }
}
```

> **Wichtig:** Nimm die Methoden-Rümpfe **wörtlich** aus `MySqlDialect.java`. Verschiebe **auch** alle
> privaten Helfer, die nur von diesen Methoden benutzt werden (`readImportedKey`, `mapTriggerTiming`,
> `mapTriggerEvent`, `mapReferentialAction`, `mapMySqlIndexType`, `resolveSchema`, `PkBuilder` …).

### Schritt 1.4 — Die verschobenen Methoden aus `MySqlDialect` entfernen

**Repo:** `jdbc.db`. **Datei:**
`dialect/db/mysql/src/main/java/.../mysql/MySqlDialect.java`

1. Alle `@Override`-Methoden löschen, die du nach `MySqlMetadataProvider` kopiert hast
   (`getAllPrimaryKeys`, `getAllIndexInfo`, `getAllImportedKeys`, …).
2. Die nur dort benutzten privaten Helfer löschen (`PkBuilder`, `resolveSchema`, `mapMySqlIndexType`, …).
3. Die jetzt unbenutzten Imports entfernen — insbesondere alle
   `import org.eclipse.daanse.jdbc.db.record.*` und `import org.eclipse.daanse.jdbc.db.api.meta.*`.

**Prüfung (muss leer sein):**
```bash
grep -nE "import org.eclipse.daanse.jdbc.db.(record|api\.meta)\." \
  dialect/db/mysql/src/main/java/org/eclipse/daanse/jdbc/db/dialect/db/mysql/MySqlDialect.java
```

> **Achtung Rolle B:** `import …api.schema.*` (z. B. `TableReference`, `ColumnDefinition`) **bleiben**
> in der Dialektklasse, wenn sie in DDL-Methoden benutzt werden — die sind erlaubt und ziehen später
> als Insel mit nach SQL.

### Schritt 1.5 — Für die 5 übrigen großen Dialekte wiederholen

**Repo:** `jdbc.db`. Wiederhole Schritt 1.2–1.4 für: `h2, mariadb, mssqlserver, oracle, postgresql`.
Jeweils `dialect/metadata/<db>/…/<Db>MetadataProvider.java` mit `@DialectName("<NAME>")`. Die
`@DialectName`-Werte findest du in der jeweiligen `<Db>DialectFactory` (z. B. `H2`, `MARIADB`,
`MSSQLSERVER`, `ORACLE`, `POSTGRESQL`).

> `duckdb`, `sqlite`, `common`: **nichts tun** — sie haben keine Rolle-A-Methoden.

### Schritt 1.6 — `Dialect` von `MetadataProvider` entkoppeln

**Repo:** `jdbc.db`. **Datei:** `dialect/api/src/main/java/.../dialect/api/Dialect.java`

Vorher:
```java
import org.eclipse.daanse.jdbc.db.api.MetadataProvider;
...
public interface Dialect
        extends IdentifierQuoter, LiteralQuoter, DialectCapabilitiesProvider, TypeMapper, MetadataProvider {
```
Nachher:
```java
// (Import gelöscht)
public interface Dialect
        extends IdentifierQuoter, LiteralQuoter, DialectCapabilitiesProvider, TypeMapper {
```

`BestFitColumnType getType(ResultSetMetaData, int)` und die `api.type`-Importe **bleiben**.

### Schritt 1.7 — Aufrufer & Tests auf den Begleiter umstellen

**Repo:** `jdbc.db`.

- **`DatabaseServiceImpl`** (`impl/.../DatabaseServiceImpl.java`) ändert sich **nicht** in der
  Signatur — es nimmt `MetadataProvider` als Parameter (`createMetaInfo(Connection, MetadataProvider)`,
  `getCatalogs(Connection, MetadataProvider)`). Nur die **Aufrufstelle** übergibt jetzt den Begleiter.
- **Tests**, die bisher einen Dialekt als Provider durchreichten, ändern die eine Zeile:

Vorher:
```java
MetaInfo mi = service.createMetaInfo(conn, new MySqlDialect(init));
```
Nachher:
```java
MetaInfo mi = service.createMetaInfo(conn, new MySqlMetadataProvider());
```

> Dialekte ohne native Metadaten (duckdb/sqlite): `MetadataProvider.EMPTY` verwenden.

### ✅ Checkpoint 1 (Repo `jdbc.db` muss grün sein)

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db
# Dialekt-Main frei von record/api.meta:
grep -rE "import org.eclipse.daanse.jdbc.db.(record|api\.meta)\." \
  dialect/db/{h2,mariadb,mssqlserver,mysql,oracle,postgresql}/src/main   # → leer
# Dialect nicht mehr MetadataProvider:
grep -n "extends" dialect/api/src/main/java/org/eclipse/daanse/jdbc/db/dialect/api/Dialect.java
mvn -q clean verify   # muss grün sein
```

Wenn grün: **Phase R ist fertig.** Committe: `git commit -am "Phase R: split MetadataProvider role out of dialects"`.

---

# Teil 2 — Phase S: `api`-Split (schema/type/sql → neues `sql.api`)

**Ziel:** Nur die vom Dialekt gebrauchten Pakete (`api.schema`, `api.type`, `api.sql`) werden zu einem
neuen SQL-Modul. Der Introspektions-Rest (`api.meta`, `MetadataProvider`, `DatabaseService`, …) bleibt
in `jdbc.db`.

### Schritt 2.1 — Modul `sql/api` anlegen

**Repo:** `sql`. **Datei neu:** `api/pom.xml`
```xml
<project ...>
  <parent>
    <groupId>org.eclipse.daanse</groupId>
    <artifactId>org.eclipse.daanse.sql</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>org.eclipse.daanse.sql.api</artifactId>
  <name>Eclipse Daanse SQL API (schema/type/sql model)</name>
  <dependencies>
    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
  </dependencies>
</project>
```
**Datei ändern:** `pom.xml` (SQL-Root) → in `<modules>` `api` an den Anfang setzen:
```xml
<modules>
  <module>api</module>       <!-- NEU -->
  <module>guard</module>
  <module>deparser</module>
  <module>statement</module>
</modules>
```

### Schritt 2.2 — Pakete verschieben (`git mv`) + umbenennen

**Repo:** beide (Quelle `jdbc.db`, Ziel `sql`). Für jedes der 3 Pakete (`schema`, `type`, `sql`):

```bash
# Beispiel für 'schema' – analog für 'type' und 'sql'
SRC=/home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db/api/src/main/java/org/eclipse/daanse/jdbc/db/api/schema
DST=/home/stbischof/git/daanse/org.eclipse.daanse.sql/api/src/main/java/org/eclipse/daanse/sql/api/schema
mkdir -p "$(dirname "$DST")"
git -C /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db mv "$SRC" "$DST"   # wenn im selben Repo; sonst normales mv + git add in beiden
```

> **Achtung:** `git mv` funktioniert nur innerhalb eines Repos. Über Repo-Grenzen: Ordner mit `mv`
> verschieben, dann in `jdbc.db` `git rm -r` (alten Pfad) und in `sql` `git add` (neuer Pfad). Die
> Historie geht dabei über die Grenze verloren — das ist ok (siehe Wholesale-Plan
> [../07-anomalies-and-risks.md](../07-anomalies-and-risks.md) §5).

Dann in **allen** verschobenen Dateien den Paketnamen ersetzen:
```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql/api
grep -rl "org.eclipse.daanse.jdbc.db.api.schema" . | xargs sed -i \
  's/org\.eclipse\.daanse\.jdbc\.db\.api\.schema/org.eclipse.daanse.sql.api.schema/g'
# ebenso .type und .sql
```
Und die `package-info.java` in jedem der drei Pakete anpassen.

### Schritt 2.3 — Rest von `jdbc.db/api` auf `sql.api` zeigen lassen

**Repo:** `jdbc.db`. Das verbleibende `api`-Modul (jetzt nur `api.meta` + top-level Introspektion)
importiert Schema-Typen. Diese Importe umbiegen:
```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db/api
grep -rl "org.eclipse.daanse.jdbc.db.api.\(schema\|type\|sql\)" . | xargs sed -i \
  -e 's/org\.eclipse\.daanse\.jdbc\.db\.api\.schema/org.eclipse.daanse.sql.api.schema/g' \
  -e 's/org\.eclipse\.daanse\.jdbc\.db\.api\.type/org.eclipse.daanse.sql.api.type/g' \
  -e 's/org\.eclipse\.daanse\.jdbc\.db\.api\.sql/org.eclipse.daanse.sql.api.sql/g'
```
**Datei:** `api/pom.xml` → Dependency auf das neue SQL-Modul ergänzen:
```xml
<dependency>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.sql.api</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Schritt 2.4 — `record`, `impl`, Begleiter auf `sql.api` umstellen

**Repo:** `jdbc.db`, Module `record`, `impl`, `dialect/metadata/*`. Gleiche `sed`-Ersetzung der
`api.{schema,type,sql}`-Importe wie in 2.3; in jedem `pom.xml` die `sql.api`-Dependency ergänzen.

### ✅ Checkpoint 2

```bash
# SQL-api baut isoliert:
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql && mvn -q -pl org.eclipse.daanse.sql.api -am install
# jdbc.db baut gegen den frischen sql.api-Snapshot:
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db && mvn -q clean verify
```
Committen, wenn grün.

---

# Teil 3 — Phase D: Dialekte nach `sql` verschieben

**Ziel:** `dialect.api` (jetzt ohne MetadataProvider) + `common` + `test-support` + die 8 aktiven
Dialekte nach `sql`. Sie brauchen jetzt **kein** `record` und **kein** `api.meta` mehr.

### Schritt 3.1 — Aggregatoren im SQL-Repo

**Repo:** `sql`. **Dateien neu:** `dialect/pom.xml` und `dialect/db/pom.xml` (packaging=pom). Muster
siehe [06-build-and-verification.md](06-build-and-verification.md) §1. Root-`<modules>` um `dialect`
ergänzen.

### Schritt 3.2 — `dialect.api` verschieben + umbenennen

**Repo:** beide. Ordner `jdbc.db/dialect/api` → `sql/dialect/api`. Paket-Rename
`org.eclipse.daanse.jdbc.db.dialect.api` → `org.eclipse.daanse.sql.dialect.api`. **Pom:** Parent auf
`org.eclipse.daanse.sql.dialect`, Dependency `jdbc.db.api` → `org.eclipse.daanse.sql.api`
(reaktor-intern, `${project.version}`).

### Schritt 3.3 — `common` und `test-support` verschieben

**Repo:** beide. Wie 3.2; Deps → `sql.dialect.api` / `sql.api`.

### Schritt 3.4 — Die 8 aktiven Dialekte verschieben (mysql VOR mariadb)

**Repo:** beide. Reihenfolge: `mysql`, dann `mariadb` (weil `mariadb → mysql`), dann `duckdb, h2,
mssqlserver, oracle, postgresql, sqlite` in beliebiger Reihenfolge.

Pro Dialekt:
1. Ordner `jdbc.db/dialect/db/<db>` → `sql/dialect/db/<db>`.
2. Paket-Rename `…dialect.db.<db>` → `…sql.dialect.db.<db>`; Schema-Importe `…jdbc.db.api.schema` →
   `…sql.api.schema` (usw.).
3. **Pom umschreiben** — Beispiel `mysql` (vorher/nachher der Kern-Deps):

Vorher (`dialect/db/mysql/pom.xml`):
```xml
<dependency><artifactId>org.eclipse.daanse.jdbc.db.dialect.api</artifactId><version>${revision}</version></dependency>
<dependency><artifactId>org.eclipse.daanse.jdbc.db.dialect.db.common</artifactId><version>${revision}</version></dependency>
<dependency><artifactId>org.eclipse.daanse.jdbc.db.record</artifactId><version>${revision}</version></dependency>   <!-- weg! -->
<dependency><artifactId>org.eclipse.daanse.jdbc.db.impl</artifactId><version>${revision}</version><scope>test</scope></dependency> <!-- weg! -->
```
Nachher (`sql/dialect/db/mysql/pom.xml`):
```xml
<dependency><artifactId>org.eclipse.daanse.sql.dialect.api</artifactId><version>${project.version}</version></dependency>
<dependency><artifactId>org.eclipse.daanse.sql.dialect.db.common</artifactId><version>${project.version}</version></dependency>
<dependency><artifactId>org.eclipse.daanse.sql.api</artifactId><version>${project.version}</version></dependency>
<!-- KEIN record, KEIN impl mehr — Rolle A ist raus -->
<!-- Test-Treiber/Testcontainers bleiben (mysql-connector-j, testcontainers) -->
```

> Der `record`- und der `impl`(test)-Dependency **entfallen**, weil die Metadaten-Rolle (die diese
> brauchte) in Teil 1 nach `jdbc.db/dialect/metadata` gewandert ist. Falls ein Dialekt-Test noch
> `impl` braucht (Round-Trip), verschiebe diesen Test mit nach `jdbc.db/dialect/metadata/<db>` oder
> markiere ihn temporär `@Disabled` (siehe [01-role-separation.md](01-role-separation.md)).

4. In `sql/dialect/db/pom.xml` `<module><db></module>` eintragen.

### ✅ Checkpoint 3

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql
# Kein jdbc.db mehr in den Dialekt-Pakten:
grep -rl "org.eclipse.daanse.jdbc.db" dialect/ --include=*.java   # → leer
mvn -q -pl org.eclipse.daanse.sql.dialect.db.mysql -am install     # baut inkl. Abhängigkeiten
```

---

# Teil 4 — Phase C: Konsumenten umverdrahten + `jdbc.db`-Rückbau

### Schritt 4.1 — SQL-Konsumenten reaktor-intern

**Repo:** `sql`, Module `guard`, `deparser`, `statement`. In deren `pom.xml` die externen
`jdbc.db.dialect.*`-Deps durch die reaktor-internen `sql.dialect.*` ersetzen; Java-Importe
`org.eclipse.daanse.jdbc.db.dialect` → `org.eclipse.daanse.sql.dialect`, `…jdbc.db.api.{schema,type,sql}`
→ `…sql.api.*`. Details: [04-consumer-rewiring.md](04-consumer-rewiring.md) §1.

### Schritt 4.2 — Java 21 im SQL-Repo

**Repo:** `sql`. **Datei:** `pom.xml` (Root) → `java.version`/`java.release` auf `21` (siehe
[06-build-and-verification.md](06-build-and-verification.md) §1).

### Schritt 4.3 — `jdbc.db` auf publizierte `sql.*`-Snapshots

**Repo:** `jdbc.db`, Module `impl`, `importer/csv`, `dialect/metadata/*`. In den `pom.xml`:
Dialekt-/Schema-Deps auf `sql.*` (extern, `0.0.1-SNAPSHOT`). **Datei:** `jdbc.db/pom.xml` → aus
`<modules>` das (jetzt umgezogene) `dialect`-Aggregat entfernen bzw. auf `dialect/metadata` reduzieren.

### ✅ Checkpoint 4 (Reihenfolge beachten!)

```bash
# 1) SQL zuerst installieren:
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql && mvn -q clean install
# 2) jdbc.db gegen sql.*-Snapshots:
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db && mvn -q clean verify
```

**Damit ist der Minimal-Split fertig.** Teil 5 ist die optionale Dialekt-Versionen-Erweiterung.

---

# Teil 5 — Phase V: Dialekt-Versionen fest einbauen (deine Idee, O2)

**Ziel:** Statt zur Laufzeit die Version aus `DialectInitData` auszuwerten (`isUnknownOrAtLeast(8,0)`),
gibt es **feste Klassen pro Version** mit einkompilierten Eigenschaften. Beispiel MySQL. **Repo:** `sql`,
Modul `sql/dialect/db/mysql`.

### Schritt 5.1 — Basisklasse identifizieren

`MySqlDialect` bleibt die **gemeinsame Basis** (SQL-Generierung, Quoting). Heute verzweigt sie intern:
```java
// MySqlDialect.java (heute)
public CteGenerator cteGenerator() {
    boolean supports = DialectVersion.UNKNOWN.equals(dialectVersion) || dialectVersion.atLeast(8, 0);
    ...
}
public boolean requiresOrderByAlias() { return dialectVersion.isUnknownOrAtLeast(5, 7); }
```

### Schritt 5.2 — Zwei Versionsklassen anlegen

**Datei neu:** `…/mysql/MySql8Dialect.java`
```java
package org.eclipse.daanse.sql.dialect.db.mysql;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;

/** MySQL 8.x — recursive CTE, ORDER BY alias required, percentile functions. */
public class MySql8Dialect extends MySqlDialect {
    public MySql8Dialect(DialectInitData init) {
        super(init);
    }
    @Override public boolean requiresOrderByAlias() { return true; }   // fest, statt Version-Check
    // cteGenerator(): rekursive Variante — hier fest aktivieren
    // supportsPercentileCont()/Disc(): true
}
```
**Datei neu:** `…/mysql/MySql57Dialect.java`
```java
package org.eclipse.daanse.sql.dialect.db.mysql;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;

/** MySQL 5.7 — no recursive CTE, ORDER BY alias required, no percentile. */
public class MySql57Dialect extends MySqlDialect {
    public MySql57Dialect(DialectInitData init) {
        super(init);
    }
    @Override public boolean requiresOrderByAlias() { return true; }
    // cteGenerator(): NICHT-rekursive Variante
    // supportsPercentileCont()/Disc(): false
}
```

> Ziehe die heutigen `dialectVersion.isUnknownOrAtLeast(...)`-Verzweigungen aus `MySqlDialect` heraus
> und mache sie in den Versionsklassen zu **festen** `@Override`-Rückgaben. Was versionsunabhängig ist,
> bleibt in `MySqlDialect`.

### Schritt 5.3 — Factory wählt anhand der Version

**Datei:** `…/mysql/MySqlDialectFactory.java`

Vorher:
```java
@Override
public Function<DialectInitData, MySqlDialect> getConstructorFunction() {
    return MySqlDialect::new;
}
```
Nachher (Auswahl statt fixem Konstruktor):
```java
@Override
public Dialect createDialect(DialectInitData init) {
    if (MySqlDialect.looksLikeInfobright(init)) {
        throw new IllegalStateException("Snapshot looks like Infobright; use InfobrightDialectFactory");
    }
    // Version bestimmt die konkrete Klasse; UNKNOWN → neueste bekannte (8.x)
    var v = init.version();
    if (v.atLeast(8, 0) || v.equals(org.eclipse.daanse.sql.dialect.api.DialectVersion.UNKNOWN)) {
        return new MySql8Dialect(init);
    }
    return new MySql57Dialect(init);
}
```
`getConstructorFunction()` wird dann nicht mehr gebraucht (oder liefert die Basis für Kompatibilität).

### Schritt 5.4 — Unit-Test ohne Live-DB

**Datei neu:** `…/mysql/src/test/java/.../MySql8DialectTest.java`
```java
@Test
void mysql8_requiresOrderByAlias_true_offline() {
    var init = DialectInitData.ansiDefaults()
            .withQuoteIdentifierString("`")
            .withVersion(8, 0);          // rein statisch, KEINE Connection nötig
    var d = new MySql8Dialect(init);
    assertThat(d.requiresOrderByAlias()).isTrue();
}
```

### ✅ Checkpoint 5

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql
mvn -q -pl org.eclipse.daanse.sql.dialect.db.mysql -am verify   # Versionsklassen + Offline-Tests grün
```

> Wiederhole 5.1–5.4 für weitere DBs nur nach Bedarf — jede DB ist unabhängig.

---

# Teil 6 — Endabnahme: alles baut und läuft

### Schritt 6.1 — Voller Build in korrekter Reihenfolge

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql     && mvn -q clean install   # SQL zuerst!
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db && mvn -q clean verify
```

### Schritt 6.2 — Struktur-Kontrollen (alle müssen „leer" liefern)

```bash
# SQL-Repo enthält keine jdbc.db-Referenzen mehr:
grep -rl "org.eclipse.daanse.jdbc.db" /home/stbischof/git/daanse/org.eclipse.daanse.sql/ --include=*.java
# SQL-Seite ohne record/api.meta:
grep -rn "sql\.record\|api\.meta" /home/stbischof/git/daanse/org.eclipse.daanse.sql/ --include=*.java
# SQL-Poms ohne jdbc.db-Deps:
grep -rn "jdbc.db" /home/stbischof/git/daanse/org.eclipse.daanse.sql/ --include=pom.xml
```

### Schritt 6.3 — Wichtige Tests

- **SQL:** `statement/demo` (H2 + MSSQL via Testcontainers), `guard`, `deparser`, MySQL-Versionstests.
- **jdbc.db:** `impl` (Introspektion + MetaInfo gegen die neuen Begleiter), `importer/csv` (ETL).

### Schritt 6.4 — Abschluss-Checkliste

- [ ] Beide Repos bauen grün (SQL zuerst installiert).
- [ ] SQL-Repo hat **keine** `jdbc.db`-Abhängigkeit; SQL-Dialekt-Code ohne `record`/`api.meta`.
- [ ] `Dialect` erbt nicht mehr von `MetadataProvider`; 6 Begleiter in `jdbc.db/dialect/metadata`.
- [ ] MySQL-Versionsklassen (`MySql8Dialect`/`MySql57Dialect`) + Offline-Tests grün.
- [ ] `statement/demo`-Integrationstests laufen; `impl`/`importer`-Tests grün.
- [ ] Lizenz-Header-/Javadoc-CI in beiden Repos grün.

**Wenn alle Haken sitzen: fertig.** Split + Dialekt-Versionen sind umgesetzt und alles läuft.

---

## Anhang — Wenn ein Build rot wird (Junior-Hilfe)

| Symptom | Wahrscheinliche Ursache | Fix |
|---|---|---|
| `cannot find symbol: class PkBuilder` in `MySqlDialect` | Helfer gelöscht, aber noch referenziert | Referenzen prüfen; Helfer gehört jetzt in `MySqlMetadataProvider` |
| `package org.eclipse.daanse.jdbc.db.api.schema does not exist` (SQL-Seite) | Import nicht umbenannt | `sed`-Ersetzung auf `sql.api.schema` erneut laufen lassen |
| `record`/`api.meta` immer noch im Dialekt-Import | Rolle-A-Methode übersehen | `grep` aus Checkpoint 1; restliche Methode verschieben |
| `jdbc.db` findet `sql.api` nicht | SQL nicht installiert | im SQL-Repo `mvn -q clean install`, **dann** `jdbc.db` |
| OSGi: Provider zur Laufzeit `EMPTY` | `@DialectName` fehlt/falsch am Begleiter | `@DialectName("MYSQL")` prüfen (gleicher Wert wie Factory) |
| Zyklus-/Reactor-Fehler | Modul in falscher Reihenfolge/`<modules>` fehlt | Reihenfolge R→S→D einhalten; Aggregator-`<modules>` prüfen |
