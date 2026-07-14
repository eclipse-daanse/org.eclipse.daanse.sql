<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 08 – Проверка и приёмка

Назад к [мастер-плану](README.md).

---

## 1. Порядок сборки

```bash
# 1) собрать, протестировать и локально установить SQL-репозиторий
cd org.eclipse.daanse.sql
mvn -q clean install

# 2) jdbc.db против свежеустановленных снапшотов sql.*
cd ../org.eclipse.daanse.jdbc.db
mvn -q clean verify
```

`sql` **обязан** быть установлен до `jdbc.db` (односторонняя зависимость, см.
[02](02-dependency-and-layering.md)).

---

## 2. Объём тестов

- **SQL-репозиторий:**
  - Интеграционные тесты `statement/demo` (H2 in-memory + MSSQL через Testcontainers) зелёные.
  - Тесты `guard/jsqltranspiler` (используют `sql.dialect.db.h2/common`) зелёные.
  - Тесты `deparser` зелёные.
  - Перенесённые unit-тесты диалектов (тесты генераторов/квотеров) зелёные.
  - Round-trip-тесты с кросс-репо зависимостью от `impl`: статус по каждому диалекту
    задокументирован (переписан | `@Disabled`, см. [07](07-anomalies-and-risks.md) §3).
- **jdbc.db:**
  - Тесты `impl` (DDL round-trip) зелёные против `sql.dialect.db.h2/common`.
  - ETL-тесты `importer/csv` (`CsvDataLoaderTest`) зелёные.

---

## 3. Структурные проверки

```bash
# В SQL-репозитории больше нет старых пакетов dialect/api:
grep -rl "org.eclipse.daanse.jdbc.db" org.eclipse.daanse.sql/ --include=*.java   # → пусто

# SQL-репозиторий больше не ссылается на внешние артефакты jdbc.db.dialect:
grep -rn "jdbc.db.dialect" org.eclipse.daanse.sql/ --include=pom.xml             # → пусто

# jdbc.db больше не содержит модулей api/record/dialect в реакторе:
grep -nE "<module>(api|record|dialect)</module>" org.eclipse.daanse.jdbc.db/pom.xml   # → пусто
```

---

## 4. Определение готовности — набор документов (этот каталог)

- [ ] `README.md` + `01`–`08` присутствуют, взаимно связаны, согласованы.
- [ ] Таблица разделения покрывает каждый модуль обоих репозиториев ровно один раз.
- [ ] Сопоставление переименований полное (у каждого переносимого пакета + artifactId есть цель).
- [ ] Порядок миграции соблюдает граф зависимостей.
- [ ] Устранение цикличности обосновано.
- [ ] Аномалии (redshift/snowflake, Java 21, кросс-репо тесты) зафиксированы.

## 5. Определение готовности — реализация кода (поздняя фаза)

- [ ] `mvn clean install` в SQL-репозитории зелёный (Java 21).
- [ ] SQL-репозиторий **не** имеет внешних зависимостей `jdbc.db.*`.
- [ ] `mvn clean verify` в jdbc.db зелёный против опубликованных снапшотов `sql.*`.
- [ ] Интеграционные тесты `statement/demo` (H2/MSSQL) проходят.
- [ ] CI-проверки лицензионных заголовков и Javadoc в обоих репозиториях зелёные.
- [ ] Активное подмножество диалектов (10 модулей) полностью собирается под `sql/dialect/db/`.
- [ ] Аномалия redshift/snowflake задокументирована для поздней волны (сейчас не затрагивается).

---

## 6. Откат

Поскольку переезд выполняется в рабочих ветках обоих репозиториев и используется `git mv`, откат
возможен отбрасыванием веток. Ни одно изменение не сливается до тех пор, пока оба репозитория не
собираются зелёными и не выполнено DoD.
