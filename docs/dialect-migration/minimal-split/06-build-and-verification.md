<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 06 – Build, Java 21 & Verifikation

Zurück zum [Minimal-Split-Plan](README.md).

---

## 1. Java 21 & Poms

Wie beim Wholesale-Plan ([../06-build-and-java21.md](../06-build-and-java21.md)):

- SQL-Root-Pom `java.version`/`java.release` auf **21**.
- Neue Aggregatoren `sql/dialect/pom.xml` (packaging=pom) + `sql/dialect/db/pom.xml` mit
  `<modules>`: `common test-support duckdb h2 mariadb mssqlserver mysql oracle postgresql sqlite`.
- Root-`<modules>` um `api`, `dialect` ergänzen (**kein** `record` — es bleibt in `jdbc.db`).
- `dependencyManagement` um Test-Treiber/Testcontainers/JUnit aus den Dialekt-Poms erweitern.
- Parent `pom.parent:0.0.7`, `${revision}=0.0.1-SNAPSHOT`, Snapshot-Repo unverändert.

**Unterschied zum Wholesale-Plan:** `sql/record` gibt es **nicht** — `record` bleibt komplett in
`jdbc.db`. `sql/api` enthält nur `schema`+`type`+`sql`.

---

## 2. Build-Reihenfolge

```bash
# 1) SQL-Repo bauen + installieren (Voraussetzung für jdbc.db)
cd org.eclipse.daanse.sql && mvn -q clean install

# 2) jdbc.db gegen die frischen sql.*-Snapshots (inkl. api-Split, Begleiter, impl, importer)
cd ../org.eclipse.daanse.jdbc.db && mvn -q clean verify
```

`sql` **muss** vor `jdbc.db` installiert sein (einseitige Kante `jdbc.db → sql`).

---

## 3. Verifikation

### 3.1 Phase R (Rollen-Trennung, nur `jdbc.db`, vor jedem Umzug)

```bash
cd org.eclipse.daanse.jdbc.db
# Dialekt-Main frei von record/api.meta:
grep -rE "import org.eclipse.daanse.jdbc.db.(record|api\.meta)\." \
  dialect/db/{h2,mariadb,mssqlserver,mysql,oracle,postgresql}/src/main   # → leer
# Dialect nicht mehr MetadataProvider:
grep -n "extends" dialect/api/src/main/java/org/eclipse/daanse/jdbc/db/dialect/api/Dialect.java  # kein MetadataProvider
mvn -q verify   # inkl. Round-Trip-/MetaInfo-Tests gegen die <Db>MetadataProvider
```

### 3.2 Struktur nach dem Umzug

```bash
# SQL-Repo ohne jdbc.db-Referenzen:
grep -rl "org.eclipse.daanse.jdbc.db" org.eclipse.daanse.sql/ --include=*.java   # → leer
# SQL-Seite ohne record/api.meta:
grep -rn "sql.record\|api\.meta" org.eclipse.daanse.sql/ --include=*.java        # → leer
# SQL-Repo ohne externe jdbc.db-Deps:
grep -rn "jdbc.db" org.eclipse.daanse.sql/ --include=pom.xml                     # → leer
# jdbc.db behält record/impl/importer/dialect.metadata:
grep -nE "<module>(api|record|impl|importer|dialect)</module>" org.eclipse.daanse.jdbc.db/pom.xml
```

### 3.3 Tests

- **SQL:** `statement/demo` (H2/MSSQL via Testcontainers), `guard/jsqltranspiler`, `deparser`,
  Dialekt-Unit-Tests (Generator/Quoter) — grün.
- **jdbc.db:** `impl`-Tests (Introspektion + MetaInfo gegen die Begleiter), `importer/csv`-ETL-Tests
  — grün.

---

## 4. Risiken

| Risiko | Eintritt | Wirkung | Gegenmaßnahme |
|---|---|---|---|
| `Dialect`-SPI-Bruch trifft externe Nutzer | mittel | Kompilierfehler bei Konsumenten von `dialect.getAll*` | Rolle A war intern (Motor nutzt `MetadataProvider`-Param); Begleiter bereitstellen; migrieren |
| OSGi-Auflösung Dialekt ↔ Provider | mittel | Provider zur Laufzeit nicht gefunden | `@DialectName`-Tagging beibehalten; Fallback `MetadataProvider.EMPTY` |
| `api`-Split zieht falsche Grenze | gering | Zyklus/Kompilierfehler | Grenze belegt: `api.schema` importiert kein anderes api-Paket |
| Round-Trip-Tests hingen am Dialekt-als-Provider | mittel | Testfehler nach Split | Tests auf Begleiter umstellen ([04](04-consumer-rewiring.md) §2.1) |
| Release-Reihenfolge | mittel | `jdbc.db` baut nicht | `sql` zuerst installieren/publizieren |

---

## 5. Definition of Done

**Refactor (Phase R):**
- [ ] `Dialect` ohne `extends MetadataProvider`; `jdbc.db` grün.
- [ ] 6 `<Db>MetadataProvider`-Begleiter vollständig; Dialekt-Main ohne `record`/`api.meta`.

**Split & Umzug:**
- [ ] `sql.api` = nur schema/type/sql; **kein** `sql.record`.
- [ ] SQL-Repo baut ohne `jdbc.db`-Deps (Java 21); SQL-Seite ohne `record`/`api.meta`.
- [ ] `jdbc.db` baut gegen `sql.*`-Snapshots; `record`, `api.meta`, `impl`, `importer`,
      `dialect.metadata` bleiben dort.
- [ ] Einweg `jdbc.db → sql`, kein Zyklus.
- [ ] `statement/demo`-Integrationstests (H2/MSSQL) + `impl`/`importer`-Tests grün.
- [ ] Lizenz-Header-/Javadoc-CI in beiden Repos grün.

**Optional (Phase V):** siehe [05](05-per-version-hardening.md).
