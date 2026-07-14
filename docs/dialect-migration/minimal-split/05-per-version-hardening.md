<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 05 – Per-Version-Härtung (optional, Option O2)

Zurück zum [Minimal-Split-Plan](README.md).

**Optional.** Diese Phase ist für den minimalen Schnitt **nicht erforderlich** — nach der
Rollen-Trennung ([01](01-role-separation.md)) ist der Dialekt bereits frei von `record`/`api.meta`.
Sie setzt die ursprüngliche Idee „**Dialekte fest pro Version einbauen, ohne JDBC-Record zum Setzen
der Eigenschaften**" konsequent um und entkoppelt den Dialekt zusätzlich von jeglicher Laufzeit-
Metadaten-Beschaffung.

---

## 1. Ausgangslage (belegt)

Die SQL-Generierung liest heute nur Konstanten + einen `int` `dialectVersion` (aus `DialectInitData`).
Zwei Versions-Muster existieren bereits:

- **Subklasse pro Variante:** `Db2OldAs400Dialect extends Db2Dialect` (überschreibt
  `allowsFromQuery()→false`), eigener `@DialectName("DB2_OLD_AS400")`.
- **In-Methode:** `dialectVersion.isUnknownOrAtLeast(8,0)` (mysql/oracle/postgresql/mssql/mariadb/sqlite).

`DialectInitData` trägt neben der Version noch metadaten-abgeleitete Felder (`readOnly`,
`supportedResultSetStyles`, `sqlKeywordsLower`, `maxColumnNameLength`), die eine fest verdrahtete
Variante als Konstanten liefern könnte.

---

## 2. Ziel

Statt zur Laufzeit `DialectInitData.fromConnection(...)` zu lesen, feste Pro-Version-Klassen mit
**einkompilierten** Eigenschaften — die `DialectFactory` wählt allein anhand von Produktname + Version
die passende Klasse; **kein** `DatabaseMetaData`-Zugriff mehr nötig.

```
MySqlDialect (Basis, gemeinsame SQL-Generierung)
 ├─ MySql57Dialect   → dialectVersion=(5,7), Capabilities/Keywords als Konstanten
 └─ MySql8Dialect    → dialectVersion=(8,0), rekursive CTE, Percentile, …
```

---

## 3. Schritte (pro DB, exemplarisch MySQL)

1. Die in-Methode-Verzweigungen (`isUnknownOrAtLeast(8,0)`) identifizieren und in **feste
   Überschreibungen** je Versionsklasse überführen (z. B. `cteGenerator()` in `MySql8Dialect` liefert
   direkt die rekursive Variante).
2. Metadaten-abgeleitete Felder als Konstanten in die Versionsklasse ziehen (Quote-Zeichen,
   `maxColumnNameLength`, ggf. Keyword-Set).
3. `MySqlDialectFactory.createDialect(DialectInitData init)` wählt anhand `init.version()` /
   `init.productVersion()` die konkrete Klasse (Fallback: neueste bekannte Version bei UNKNOWN).
4. `DialectInitData` bleibt als Träger für Produktname/Version bestehen, kann aber vollständig
   **offline** erzeugt werden (`ansiDefaults().withVersion(8,0).withQuoteIdentifierString("`")`).

---

## 4. Abwägung

| Vorteil | Nachteil |
|---|---|
| Dialekt komplett statisch, kein Laufzeit-`DatabaseMetaData` | Klassen-Explosion (mehrere Versionen je DB) |
| Testbar ohne Live-DB (feste Varianten) | Versions-Auswahllogik muss robust sein |
| Verhalten pro Version explizit sichtbar | Pflegeaufwand bei neuen DB-Versionen |

**Empfehlung zur Reihenfolge:** Erst den Rollen-Split ([01](01-role-separation.md)) + Umzug
abschließen (liefert bereits den minimalen Kern). Die Per-Version-Härtung danach **inkrementell pro
DB** durchführen — sie ist unabhängig und nicht Voraussetzung des Splits.

---

## 5. Abnahme (optional)

- [ ] Pro gehärteter DB: Versions-spezifische Klassen mit konstanten Eigenschaften; keine
      `isUnknownOrAtLeast`-Verzweigung mehr im heißen Pfad.
- [ ] `DialectFactory` wählt korrekt anhand Produktname/Version; UNKNOWN → definierter Fallback.
- [ ] Dialekt-Unit-Tests laufen **ohne** Live-DB (nur `ansiDefaults().withVersion(...)`).
