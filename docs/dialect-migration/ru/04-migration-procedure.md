<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 04 – Процедура миграции (пошагово)

Назад к [мастер-плану](README.md).

Помодульный переезд в **порядке зависимостей**. Каждый шаг завершается зелёной сборкой до начала
следующего.

---

## 0. Подготовка

- Создать рабочую ветку в **обоих** репозиториях (например, `feat/dialect-migration`).
- Оба репозитория лежат рядом в `…/git/daanse/`.
- Для сохранения истории использовать `git mv`; как альтернатива — `git filter-repo`, если нужна
  полная файловая история через границу репозиториев (опционально, см. [07](07-anomalies-and-risks.md)).

---

## 1. Волна A — фундамент (`api`, `record`)

Для **`api`** (затем аналогично `record`):

1. `git mv jdbc.db/api sql/api` (каталог в реактор SQL).
2. Переместить пути пакетов `src/main/java/org/eclipse/daanse/jdbc/db/api/…` → `…/sql/api/…`.
3. Во всех файлах заменить `org.eclipse.daanse.jdbc.db.api` → `org.eclipse.daanse.sql.api`
   (`package`, `import`, `package-info`).
4. `api/pom.xml`: установить родителя на корень SQL, artifactId → `org.eclipse.daanse.sql.api`.
5. Корень `sql/pom.xml`: добавить `<module>api</module>`.
6. `record` точно так же; зависимость `record/pom.xml` `…jdbc.db.api` → `…sql.api` (внутри реактора,
   `${project.version}`).
7. `mvn -q -pl api,record -am verify`.

---

## 2. Волна B — ядро диалектов

Порядок: **`dialect/api` → `dialect/db/common` → `dialect/db/test-support`.**

1. Создать новые агрегаторы: `sql/dialect/pom.xml` (packaging=pom, родитель=корень SQL) и
   `sql/dialect/db/pom.xml` (packaging=pom, родитель=`sql.dialect`).
2. `git mv jdbc.db/dialect/api sql/dialect/api`; переименование `…jdbc.db.dialect.api` →
   `…sql.dialect.api`; зависимость pom `…jdbc.db.api` → внутри реактора `…sql.api`.
3. `git mv jdbc.db/dialect/db/common sql/dialect/db/common`; переименование; зависимость →
   `sql.dialect.api`.
4. `git mv jdbc.db/dialect/db/test-support sql/dialect/db/test-support`; переименование; зависимости →
   `sql.api`, `sql.dialect.api`.
5. Внести `sql.dialect` в корневой `<modules>`; `common`/`test-support` в `sql/dialect/db/pom.xml`.
6. `mvn -q -pl org.eclipse.daanse.sql.dialect.api,…common,…test-support -am verify`.

---

## 3. Волна C — конкретные диалекты (активное подмножество)

Для каждого из `duckdb, h2, mariadb, mssqlserver, mysql, oracle, postgresql, sqlite`
(**`mysql` перед `mariadb`**, т. к. `mariadb → mysql`):

1. `git mv jdbc.db/dialect/db/<db> sql/dialect/db/<db>`.
2. Переименование пакета `…jdbc.db.dialect.db.<db>` → `…sql.dialect.db.<db>`.
3. Переключить зависимости `pom.xml`:
   - compile: `…dialect.db.common` → `sql.dialect.db.common`, `…dialect.api` → `sql.dialect.api`,
     `…record` → `sql.record`, `…api` → `sql.api` (все внутри реактора, `${project.version}`).
   - test: `…jdbc.db.impl` → см. ниже (кросс-репо!), `…dialect.db.test-support` →
     `sql.dialect.db.test-support`; версии JDBC-драйверов/Testcontainers перенести из исходного pom.
4. Внести `<module><db></module>` в `sql/dialect/db/pom.xml`.
5. `mvn -q -pl org.eclipse.daanse.sql.dialect.db.<db> -am verify`.

> **Тестовое ребро `dialect.db.<db> → jdbc.db.impl`:** некоторые тесты диалектов (mysql, mariadb,
> mssqlserver, oracle, postgresql, derby) используют `DatabaseServiceImpl`. Поскольку `impl` остаётся
> в `jdbc.db`, это создало бы **кросс-репо тестовую зависимость** `sql → jdbc.db` и через чёрный ход
> вернуло бы цикл, устранённый в [02](02-dependency-and-layering.md). **Указание:** эти round-trip-тесты
> **не** переезжают вместе; их либо (a) переписывают на собственные тест-фикстуры `sql`, либо
> (b) временно помечают `@Disabled` и продолжают как интеграционные тесты в `jdbc.db` (где лежит
> `impl`). Решение по каждому диалекту фиксировать в [08](08-verification.md). В активном подмножестве
> это касается mysql, mariadb, mssqlserver, oracle, postgresql.

---

## 4. Волна D — переключение потребителей SQL

См. [05](05-consumer-rewiring.md) §1: переключить `guard`, `deparser`, `statement`, `statement/demo`
с внешних зависимостей `jdbc.db.*` на новые внутренние модули `sql.*` реактора, поправить импорты.
Повышение корня SQL до Java 21 (см. [06](06-build-and-java21.md)). Затем `mvn -q verify` для всего
реактора SQL.

---

## 5. Волна E — демонтаж `jdbc.db`

См. [05](05-consumer-rewiring.md) §2: в `jdbc.db` удалить переехавшие модули из реактора
(`api`, `record`, `dialect` из корневого `<modules>`) и переключить `impl`/`importer` на
**опубликованные** снапшоты `sql.*`. `mvn -q verify` реактора `jdbc.db` (предполагает, что снапшоты
`sql` доступны в локальном/Sonatype-репозитории → сначала `mvn install` для `sql`).

---

## 6. Краткая форма порядка

```
A: api → record
B: dialect.api → dialect.db.common → test-support
C: mysql → mariadb;  duckdb, h2, mssqlserver, oracle, postgresql, sqlite (параллельно)
D: потребители SQL (guard/deparser/statement) + Java 21
E: демонтаж jdbc.db (impl/importer на снапшоты sql.*)
```
