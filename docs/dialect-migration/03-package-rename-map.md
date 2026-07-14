<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 03 – Paket- & Artefakt-Rename

Zurück zum [Master-Plan](README.md).

Alle mitgezogenen Module werden von `org.eclipse.daanse.jdbc.db.*` auf `org.eclipse.daanse.sql.*`
umbenannt — Java-Pakete, Maven-ArtifactIds und OSGi-`Bundle-SymbolicName` gleichermaßen.

---

## 1. Java-Paket-Mapping

| Alt | Neu |
|---|---|
| `org.eclipse.daanse.jdbc.db.api` | `org.eclipse.daanse.sql.api` |
| `org.eclipse.daanse.jdbc.db.api.meta` | `org.eclipse.daanse.sql.api.meta` |
| `org.eclipse.daanse.jdbc.db.api.schema` | `org.eclipse.daanse.sql.api.schema` |
| `org.eclipse.daanse.jdbc.db.api.sql` | `org.eclipse.daanse.sql.api.sql` |
| `org.eclipse.daanse.jdbc.db.api.type` | `org.eclipse.daanse.sql.api.type` |
| `org.eclipse.daanse.jdbc.db.record.meta` | `org.eclipse.daanse.sql.record.meta` |
| `org.eclipse.daanse.jdbc.db.record.schema` | `org.eclipse.daanse.sql.record.schema` |
| `org.eclipse.daanse.jdbc.db.dialect.api` | `org.eclipse.daanse.sql.dialect.api` |
| `org.eclipse.daanse.jdbc.db.dialect.api.capability` | `org.eclipse.daanse.sql.dialect.api.capability` |
| `org.eclipse.daanse.jdbc.db.dialect.api.generator` | `org.eclipse.daanse.sql.dialect.api.generator` |
| `org.eclipse.daanse.jdbc.db.dialect.api.type` | `org.eclipse.daanse.sql.dialect.api.type` |
| `org.eclipse.daanse.jdbc.db.dialect.db.common` | `org.eclipse.daanse.sql.dialect.db.common` |
| `org.eclipse.daanse.jdbc.db.dialect.db.testsupport` | `org.eclipse.daanse.sql.dialect.db.testsupport` |
| `org.eclipse.daanse.jdbc.db.dialect.db.<db>` | `org.eclipse.daanse.sql.dialect.db.<db>` |

Regel: **Präfix `org.eclipse.daanse.jdbc.db` → `org.eclipse.daanse.sql`**, Suffix unverändert.
Betrifft `package`-Deklarationen, `import`-Anweisungen und `package-info.java` in **jeder**
mitgezogenen Datei — inkl. der Konsumenten (siehe [05](05-consumer-rewiring.md)).

> Anmerkung: Das `api.meta`-Paket bildet JDBC-`DatabaseMetaData`-Introspektion ab und ist damit
> semantisch JDBC-nah. Für Konsistenz im SQL-Repo wird es dennoch nach `sql.api.meta` umbenannt
> (einheitliches Präfix). Ein späteres feineres Umbenennen bleibt möglich, ist aber **nicht** Teil
> dieser Migration.

---

## 2. Maven-ArtifactId-Mapping

ArtifactId spiegelt exakt die Paket-Wurzel (Repo-Konvention).

| Alt | Neu |
|---|---|
| `org.eclipse.daanse.jdbc.db.api` | `org.eclipse.daanse.sql.api` |
| `org.eclipse.daanse.jdbc.db.record` | `org.eclipse.daanse.sql.record` |
| `org.eclipse.daanse.jdbc.db.dialect.api` | `org.eclipse.daanse.sql.dialect.api` |
| `org.eclipse.daanse.jdbc.db.dialect.db.common` | `org.eclipse.daanse.sql.dialect.db.common` |
| `org.eclipse.daanse.jdbc.db.dialect.db.test-support` | `org.eclipse.daanse.sql.dialect.db.test-support` |
| `org.eclipse.daanse.jdbc.db.dialect.db.<db>` | `org.eclipse.daanse.sql.dialect.db.<db>` |

Die neuen Aggregatoren erhalten:
- `org.eclipse.daanse.sql.dialect` (aggregator, ersetzt `…jdbc.db.dialect`)
- `org.eclipse.daanse.sql.dialect.db` (aggregator, ersetzt `…jdbc.db.dialect.db`)

Group-Id bleibt `org.eclipse.daanse`; Version bleibt `${revision}` = `0.0.1-SNAPSHOT`.

---

## 3. OSGi / DS

- **`Bundle-SymbolicName`** wird vom `bnd-maven-plugin` aus der ArtifactId abgeleitet → ändert sich
  automatisch mit der ArtifactId. Keine manuelle `bnd.bnd`-Pflege nötig (es existieren keine).
- **DS-Komponenten:** Jeder Dialekt exportiert `@Component(service = DialectFactory.class, …)`. Der
  vollqualifizierte `DialectFactory`-Typ ändert sich mit dem Paket-Rename; die Annotation selbst
  bleibt unverändert, das generierte `OSGI-INF/*.xml` folgt automatisch beim Neubau.
- **Keine `META-INF/services`-Dateien** vorhanden — nichts an ServiceLoader-Registrierung anzupassen.

---

## 4. Lizenz-Header & package-info

- Jede Datei trägt den EPL-2.0-Header (Contributors „SmartCity Jena“, „Stefan Bischof (bipolis.org)“).
  Header bleibt beim Umzug erhalten; nur das Copyright-Jahr bei inhaltlicher Änderung ergänzen.
- Jedes Leaf-Paket hat `package-info.java` — Paketname darin mit umbenennen.
- CI erzwingt Header (`.licenserc.yaml`, `license.templates`) und Javadoc
  (`java_check_javadoc.yml`) — nach dem Rename müssen diese Checks grün bleiben.

---

## 5. Mechanik (empfohlen)

1. `git mv` der Modulverzeichnisse (Historie erhalten).
2. Verzeichnis-Umbenennung der Paketpfade (`.../jdbc/db/...` → `.../sql/...`).
3. Textersetzung `org.eclipse.daanse.jdbc.db` → `org.eclipse.daanse.sql` **nur** in mitgezogenen
   Modulen und deren Konsumenten (nicht in `api.meta`-fremden Kontexten). Danach modulweise
   `mvn -q -pl <modul> -am verify`.
