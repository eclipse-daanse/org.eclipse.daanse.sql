<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 03 – Процедура миграции (пошагово)

Назад к [плану минимального среза](README.md).

Порядок: **сначала развязать (в `jdbc.db`), потом разделить, потом переносить.** Каждая волна
завершается зелёной сборкой.

---

## Волна R — разделение ролей (в `jdbc.db`)

Полностью описано в [01](01-role-separation.md). Коротко:

1. По каждому большому диалекту (h2, mariadb, mssqlserver, mysql, oracle, postgresql): методы роли A
   + приватные помощники перенести в `dialect/metadata/<db>/.../<Db>MetadataProvider.java`.
2. `Dialect.java`: убрать `extends MetadataProvider`, удалить импорт.
3. Предоставить компаньоны как OSGi-компоненты; перевести вызывающих/тесты на компаньон
   ([04](04-consumer-rewiring.md)).
4. **Gate:** `mvn -q verify` в `jdbc.db` зелёный; main-код диалектов свободен от `record`/`api.meta`.

> После волны R рефакторинг завершён и полезен сам по себе — перенос репозитория (волны S–D) можно
> планировать независимо.

---

## Волна S — разделение API (подготовка модели SQL)

1. Создать новый модуль `sql/api` (запись в корневой реактор); родитель = корень SQL.
2. Вынести пакеты `api.schema`, `api.type`, `api.sql` из `jdbc.db/api`:
   - `git mv` трёх деревьев пакетов в
     `sql/api/src/main/java/org/eclipse/daanse/sql/api/{schema,type,sql}`.
   - Переименование `org.eclipse.daanse.jdbc.db.api.{schema,type,sql}` →
     `org.eclipse.daanse.sql.api.{…}` во всех перенесённых файлах.
3. В оставшемся `jdbc.db/api` (только `api.meta` + верхний уровень интроспекции): перевести импорты
   трёх вынесенных пакетов на `org.eclipse.daanse.sql.api.*`; добавить в pom зависимость на `sql.api`
   (внешнюю, `0.0.1-SNAPSHOT`).
4. `record` и `impl`: импорты `…jdbc.db.api.{schema,type,sql}` → `…sql.api.*`; добавить в pom
   зависимость на `sql.api`.
5. **Gate:** `sql/api` собирается изолированно (`mvn -q -pl org.eclipse.daanse.sql.api -am verify`);
   `jdbc.db` собирается против снапшота `sql.api`.

> `record` **не** переносится — только его импорты переводятся на `sql.api`.

---

## Волна D — перенос диалектов в SQL

Порядок: `sql.dialect.api` → `sql.dialect.db.common` → `test-support` → конкретные диалекты
(`mysql` перед `mariadb`).

1. `git mv jdbc.db/dialect/api sql/dialect/api`; переименование `…jdbc.db.dialect.api` →
   `…sql.dialect.api`; зависимость pom на `sql.api` (внутри реактора). `Dialect` уже без
   `MetadataProvider` (волна R).
2. `git mv jdbc.db/dialect/db/common sql/dialect/db/common`; переименование; зависимость →
   `sql.dialect.api`.
3. `test-support` аналогично.
4. Активные диалекты (`duckdb, h2, mariadb, mssqlserver, mysql, oracle, postgresql, sqlite`):
   `git mv` в `sql/dialect/db/<db>`; переименование `…dialect.db.<db>` → `…sql.dialect.db.<db>`;
   зависимости pom → `sql.dialect.api`, `sql.dialect.db.common`, `sql.api` (внутри реактора).
   **Нет** зависимости от `record`, **нет** от `api.meta` (благодаря волне R).
5. Агрегаторы `sql/dialect/pom.xml`, `sql/dialect/db/pom.xml` + корневой `<modules>`
   ([06](06-build-and-verification.md)).
6. **Gate:** реактор SQL собирается; модули диалектов не имеют зависимостей от `jdbc.db`.

> Компаньоны `<Db>MetadataProvider` остаются в `jdbc.db/dialect/metadata` — они **не** переезжают.

---

## Волна C — потребители и демонтаж jdbc.db

См. [04](04-consumer-rewiring.md): потребители SQL (guard/deparser/statement) — на внутренние
реактора; потребители `jdbc.db` (`impl`, `importer`, `dialect.metadata`) — на опубликованные снапшоты
`sql.*`; убрать `dialect/api`+`dialect/db/активные` из реактора `jdbc.db` (модуль компаньонов остаётся).

---

## Волна V — усиление по версиям (опционально)

См. [05](05-per-version-hardening.md). Может быть выполнена в любой момент после волны D и независима.

---

## Краткая форма порядка

```
R: разделение ролей (jdbc.db)        → Gate: jdbc.db зелёный, main диалектов без record/api.meta
S: разделение api (schema/type/sql → sql.api)
D: dialect.api + активные dialect.db.* → sql
C: потребители + демонтаж jdbc.db
V: (опционально) усиление по версиям
B: сборка/Java21/проверка (сквозная)
```
