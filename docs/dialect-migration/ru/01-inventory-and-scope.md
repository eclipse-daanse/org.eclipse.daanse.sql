<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 01 – Инвентаризация и объём

Назад к [мастер-плану](README.md).

Этот документ перечисляет **все** модули обоих репозиториев, классифицирует их и точно очерчивает
объём текущей миграции.

---

## 1. Репозиторий `org.eclipse.daanse.jdbc.db` (источник)

- Сборка: многомодульный Maven, родитель `org.eclipse.daanse:org.eclipse.daanse.pom.parent:0.0.7`
- Group: `org.eclipse.daanse`, версия `${revision}` = `0.0.1-SNAPSHOT`
- **Java: 21**
- Метаданные OSGi через `bnd-maven-plugin` (от родителя) + аннотации DS (`@Component`); **нет** файлов
  `bnd.bnd` и **нет** `META-INF/services`.

Модули верхнего уровня (5): `api`, `record`, `impl`, `dialect`, `importer`.

### 1.1 Классификация

| Модуль | Корень пакета | Класс | Переезд? |
|---|---|---|---|
| `api` | `org.eclipse.daanse.jdbc.db.api` (`.meta/.schema/.sql/.type`) | Модель метаданных/схемы/типов/SQL (интерфейсы) | **→ sql** |
| `record` | `org.eclipse.daanse.jdbc.db.record` (`.meta/.schema`) | Record-реализации интерфейсов `api` | **→ sql** |
| `dialect/api` | `org.eclipse.daanse.jdbc.db.dialect.api` (`.capability/.generator/.type`) | API диалектов | **→ sql** |
| `dialect/db/common` | `…dialect.db.common` | База диалектов (`AbstractJdbcDialect`, `AnsiDialect`, Jdbc*) | **→ sql** |
| `dialect/db/test-support` | `…dialect.db.testsupport` | Тестовые помощники (`GeneratorTestSupport`, `RoundTripAssertions`) | **→ sql** |
| `dialect/db/<db>` (активный) | `…dialect.db.<db>` | Конкретные диалекты | **→ sql** (подмножество, см. §2) |
| `dialect/db/<db>` (неактивный) | `…dialect.db.<db>` | Конкретные диалекты | остаётся (поздняя волна) |
| `dialect/db/configurable` | `…dialect.db.configurable` | Настраиваемый в рантайме диалект | остаётся (неактивен) |
| `impl` | `org.eclipse.daanse.jdbc.db.impl` | Движок метаданных (`DatabaseServiceImpl`) — **не ETL** | **остаётся** (станет потребителем sql) |
| `importer` + `importer/csv` | `…importer.csv.api/.impl` | **ETL** (импорт CSV) | **остаётся** (ограничение) |

> **Почему `impl` остаётся, хотя это не ETL:** `impl` — движок JDBC-`DatabaseMetaData`,
> реализующий рантайм-интроспекцию. После переезда он становится чистым нижестоящим потребителем
> `sql` (см. [02](02-dependency-and-layering.md)). Его сохранение не создаёт цикла, поскольку после
> переезда `sql` больше не зависит от `jdbc.db`.

---

## 2. Объём диалектов: активное подмножество

Реактор `dialect/db/pom.xml` сейчас активирует **10** модулей; все прочие (~27) лежат на диске
закомментированными.

**Активные (мигрируют сейчас):**

```
common   test-support   duckdb   h2   mariadb   mssqlserver   mysql   oracle   postgresql   sqlite
```

- `common` и `test-support` — инфраструктура и предпосылка для всех диалектов.
- Единственное меж-диалектное ребро внутри подмножества: **`mariadb → mysql`** (оба активны →
  подмножество замкнуто). Все остальные активные диалекты зависят только от `common` + `api`.
- Диалекты, потребляемые SQL-репозиторием сегодня (`common`, `h2`, `mysql`, `mssqlserver`),
  полностью включены.

**Неактивные (пока остаются, поздняя волна):**

```
configurable  access  clickhouse  db2  derby  firebird  googlebigquery  greenplum  hive  hsqldb
impala  infobright  informix  ingres  interbase  luciddb  monetdb  neoview  netezza  nuodb
opensearch  pdidataservice  redshift  snowflake  sqlstream  sybase  teradata  vectorwise  vertica
```

> **Внимание — redshift/snowflake:** у этих двух модулей имя каталога и содержащийся пакет/класс
> перекрещены (см. [07](07-anomalies-and-risks.md)). Оба **не** входят в активное подмножество, поэтому
> сейчас не затрагиваются; при последующем переносе обязательно исправить.

**Полный список диалектов (все 37 конкретных модулей БД):** access, clickhouse, db2 (два класса
диалекта: `Db2Dialect`, `Db2OldAs400Dialect`), derby, duckdb, firebird, googlebigquery, greenplum,
h2, hive, hsqldb, impala, infobright, informix, ingres, interbase, luciddb, mariadb, monetdb,
mssqlserver, mysql, neoview, netezza, nuodb, opensearch, oracle, pdidataservice, postgresql,
redshift, snowflake, sqlite, sqlstream, sybase, teradata, vectorwise, vertica. Плюс `AnsiDialect`
(в `common`) и `ConfigurableDialect` (в `configurable`).

---

## 3. Репозиторий `org.eclipse.daanse.sql` (цель)

- Сборка: многомодульный Maven, тот же родитель `pom.parent:0.0.7`, group `org.eclipse.daanse`,
  `${revision}` = `0.0.1-SNAPSHOT`. **Java: 17** (повышается до 21).
- Существующие группы модулей:

| Группа | Leaf-модули | Роль |
|---|---|---|
| `deparser` | `api`, `jsqlparser` | Генерация SQL с учётом диалекта из разобранных инструкций |
| `guard` | `api`, `jsqltranspiler` | Безопасность/валидация SQL |
| `statement` | `api`, `impl`, `demo` | Построитель запросов с учётом диалекта, независимый от домена |

Эти группы сегодня **потребляют** внешние артефакты `jdbc.db.dialect.*` и `jdbc.db.api`; после
миграции они переключаются на внутренние связи реактора (см. [05](05-consumer-rewiring.md)).

---

## 4. Контрольный список объёма

- [ ] Каждый из 5 модулей верхнего уровня `jdbc.db` отнесён ровно к одной категории (переезд | остаётся).
- [ ] Активное подмножество диалектов = ровно 10 модулей, перечисленных в реакторе.
- [ ] Неактивные диалекты явно помечены как поздняя волна.
- [ ] ETL (`importer/csv`) явно исключён.
