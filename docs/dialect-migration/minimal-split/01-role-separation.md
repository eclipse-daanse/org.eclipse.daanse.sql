<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 01 – Rollen-Trennung: `Dialect` ↔ `MetadataProvider`

Zurück zum [Minimal-Split-Plan](README.md).

Dies ist der **Kern-Refactor**. Er läuft vollständig **innerhalb `jdbc.db`** und ist dort grün baubar,
bevor irgendetwas ins SQL-Repo umzieht. Erst wenn diese Entkopplung steht, wird der Dialekt schlank
genug für den minimalen Umzug.

---

## 1. Ausgangslage (belegt)

- `dialect/api/.../Dialect.java`: `public interface Dialect extends IdentifierQuoter, LiteralQuoter,
  DialectCapabilitiesProvider, TypeMapper, MetadataProvider`. Über `MetadataProvider` (aus `api`) *ist*
  jeder Dialekt ein Metadaten-Provider.
- `MetadataProvider` (`api/.../MetadataProvider.java`): ~40 `default`-Methoden (Rückgabe
  `Optional.empty()`/`List.of()`); importiert ~25 `api.schema.*` + `api.meta.IndexInfo`/`TypeInfo`.
- Die 6 großen Dialekte (h2, mariadb, mssqlserver, mysql, oracle, postgresql) überschreiben je 14–21
  dieser Methoden mit echten `information_schema`-Abfragen und bauen `record.schema`-Objekte.
- `duckdb`, `sqlite`, `common`, alle Factories: **keine** Overrides (erben die leeren Defaults).
- **Aufrufer sind bereits provider-parametrisiert:** `DatabaseServiceImpl` nimmt `MetadataProvider`
  als **Argument** und ruft darauf `getAllIndexInfo/getAllPrimaryKeys/...` auf — es referenziert
  `Dialect` **nicht**. Beispiele:
  ```java
  public MetaInfo createMetaInfo(Connection connection, MetadataProvider metadataProvider) { … }
  Optional<List<IndexInfo>> providerIndexes = provider.getAllIndexInfo(connection, null, null);
  Optional<List<PrimaryKey>> providerPKs   = provider.getAllPrimaryKeys(connection, null, null);
  ```

**Folgerung:** Motor und Dialekt sind faktisch schon getrennt; nur die Vererbung verschweißt sie.

---

## 2. Zielbild

```
Dialect (SQL-Generierung)            MetadataProvider (Introspektion)
  IdentifierQuoter                     getAllIndexInfo(...)
  LiteralQuoter                        getAllPrimaryKeys(...)
  DialectCapabilitiesProvider          getAllImportedKeys(...)
  TypeMapper                           ...
  (KEIN extends MetadataProvider)      ▲
                                       │ implementiert
                          <Db>MetadataProvider  (neu, bleibt in jdbc.db)
```

- `Dialect` verliert `extends MetadataProvider` und alle geerbten `getAll*`-Defaults.
- Pro großem Dialekt entsteht `<Db>MetadataProvider implements MetadataProvider` mit den extrahierten
  Methoden (unverändertes Verhalten). Diese Klassen bleiben bei der Introspektion in `jdbc.db`.
- `MetadataProvider.EMPTY` bleibt der Default für Dialekte ohne native Metadaten (duckdb/sqlite/…).

---

## 3. Schritte (innerhalb `jdbc.db`)

### 3.1 Begleiter-Klassen extrahieren (6×)

Für jeden von `h2, mariadb, mssqlserver, mysql, oracle, postgresql`:

1. Neue Klasse `dialect/metadata/<db>/.../<Db>MetadataProvider.java`,
   `implements org.eclipse.daanse.jdbc.db.api.MetadataProvider`.
2. Die überschriebenen `getAll*`/`get*`-Methoden **aus der Dialektklasse dorthin verschieben**
   (mitsamt privaten Helfern wie `readImportedKey`, `PkBuilder`, `resolveSchema`, `mapMySqlIndexType`).
3. Der Dialektklasse bleiben nur SQL-Generierung, Quoting, Capabilities, TypeMapper.
4. Imports bereinigen: die Dialektklasse importiert danach **kein** `record.*` und **kein**
   `api.meta.*` mehr (Kontrolle: `grep` muss leer sein). Die `<Db>MetadataProvider`-Klasse trägt
   diese Importe jetzt.

> Verbleibende `api.schema`-Importe in der Dialektklasse (Rolle B, nur mssqlserver/oracle/mysql) sind
> **beabsichtigt** — sie sind DDL-Eingabe-Vokabular und wandern später als geschlossene Insel mit
> nach SQL.

### 3.2 Vererbung auflösen

`dialect/api/.../Dialect.java`: `MetadataProvider` aus der `extends`-Liste entfernen; Import
`org.eclipse.daanse.jdbc.db.api.MetadataProvider` streichen. `BestFitColumnType getType(...)` bleibt
(nutzt `api.type`, kein Metadaten-Modell).

### 3.3 Bereitstellung/Verdrahtung der Begleiter (OSGi)

Heute registriert jede Factory `@Component(service = DialectFactory.class, …)`; `DatabaseServiceImpl`
ist `@Component(service = DatabaseService.class)`. Nach dem Split:

- `<Db>MetadataProvider` als eigene DS-Komponente veröffentlichen, getaggt mit `@DialectName("MYSQL")`
  (analog zu den Factories), **oder** über eine `MetadataProviderFactory` parallel zur
  `DialectFactory` bereitstellen.
- Aufrufer, die bisher einen `Dialect` als `MetadataProvider` durchreichten, holen jetzt den passenden
  `<Db>MetadataProvider` (per `@DialectName`/Produktname), Fallback `MetadataProvider.EMPTY`.
- Details der Verdrahtung in [04](04-consumer-rewiring.md).

### 3.4 Aufrufer umstellen

- `DatabaseServiceImpl`: unverändert in der Signatur (`MetadataProvider`-Parameter). Nur die
  **Bereitstellung** des Providers am Aufrufort ändert sich (nicht mehr „der Dialekt", sondern der
  Begleiter).
- Tests, die bisher `service.createMetaInfo(conn, dialect)` aufriefen, rufen künftig
  `service.createMetaInfo(conn, new MySqlMetadataProvider())` (o. ä.) auf.

---

## 4. Aufwand & Umfang

| Dialekt | zu extrahierende Methoden (ca.) | Begleiter nötig |
|---|---|---|
| oracle | 21 | ja |
| mssqlserver | 19 | ja |
| postgresql | 19 | ja |
| mariadb | 15 | ja |
| mysql | 15 | ja |
| h2 | 14 | ja |
| duckdb, sqlite | 0 | nein (EMPTY) |
| common, test-support | 0 | nein |

Reiner Verschiebe-Refactor (Methoden + private Helfer), kein Verhaltensumbau. Der `Dialect`-SPI-Bruch
ist genau eine Zeile (`extends`-Liste) plus die veränderte Bereitstellung der Provider.

---

## 5. Abnahme dieser Phase (nur `jdbc.db`)

- [ ] `Dialect` ohne `extends MetadataProvider`; kein `MetadataProvider`-Import mehr in `Dialect.java`.
- [ ] 6 `<Db>MetadataProvider`-Klassen mit den vollständigen extrahierten Methoden.
- [ ] `grep -rE "import .*\.(record|api\.meta)\." dialect/db/{h2,mariadb,mssqlserver,mysql,oracle,postgresql}/src/main` → **leer**.
- [ ] `mvn -q verify` im `jdbc.db`-Reactor grün (inkl. Round-Trip-/MetaInfo-Tests gegen die Begleiter).
