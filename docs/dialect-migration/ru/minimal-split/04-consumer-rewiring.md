<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 04 – Перепривязка потребителей и OSGi

Назад к [плану минимального среза](README.md).

Затронуты три группы потребителей: (1) собственный код SQL, (2) модули `jdbc.db`, (3) предоставление/
связывание новых компаньонов `MetadataProvider`.

---

## 1. Потребители в SQL-репозитории (внутри реактора)

Как в плане полного переноса ([../05-consumer-rewiring.md](../05-consumer-rewiring.md) §1), но
листовые типы теперь из `sql.api` вместо `jdbc.db.api`:

| Модуль | Новое (внутри реактора) |
|---|---|
| `statement/api` | `sql.dialect.api.generator.*`, `sql.api.schema.{Schema,Table,Column}Reference`, `sql.api.type.*`, `sql.api.sql.*` |
| `statement/impl` | `sql.dialect.db.common`, `sql.dialect.db.mysql`, `sql.api.*` |
| `statement/demo` | `sql.dialect.db.common/mysql/mssqlserver`, `sql.api.*` |
| `guard/jsqltranspiler` | `sql.dialect.db.h2`, `sql.dialect.db.common` |
| `deparser/api`, `deparser/jsqlparser` | `sql.dialect.api` |

Замена импортов: `org.eclipse.daanse.jdbc.db.dialect` → `org.eclipse.daanse.sql.dialect`;
`org.eclipse.daanse.jdbc.db.api.{schema,type,sql}` → `org.eclipse.daanse.sql.api.{…}`. Код SQL
**никогда** не затрагивает `record`/`api.meta` → других изменений нет.

---

## 2. Потребители в `jdbc.db` (опубликованные снапшоты `sql.*`)

### 2.1 `impl` (движок интроспекции)

- Импорты `…jdbc.db.api.{schema,type,sql}` → `…sql.api.*` (волна S).
- Сохраняет `…jdbc.db.api` (meta + `MetadataProvider`/`DatabaseService`) **локально** и `…record`
  **локально**.
- Pom: зависимость на `sql.api` (внешняя). В продакшн-коде **нет** зависимости от `dialect.*` (движок
  знает только `MetadataProvider`).
- Тесты, ранее передававшие `Dialect` как провайдер, теперь используют нужный `<Db>MetadataProvider`
  (из `jdbc.db.dialect.metadata`) или `MetadataProvider.EMPTY`.

### 2.2 `jdbc.db.dialect.metadata.<db>` (новое — компаньоны)

- Pom: compile-зависимости на `jdbc.db.api` (MetadataProvider/meta), `jdbc.db.record` и `sql.api`
  (интерфейсы схемы, которые реализуют записи). Runtime/DS по необходимости.
- Содержит по `<Db>MetadataProvider` (из волны R).

### 2.3 `importer/csv` (ETL)

- Импорты `…jdbc.db.api.{schema,type}` → `…sql.api.*`; `…dialect.api.Dialect`/`DialectFactory` →
  `…sql.dialect.api.*`. `record`/`impl`/`fastcsv`/`io.fs.watcher.*` без изменений.
- **Примечание:** `CsvDataImporter` использует `Dialect` только для кавычек/DDL (роль B) — после
  разделения по-прежнему корректно. Если он дополнительно *читает* метаданные, для этого использовать
  `MetadataProvider`.

### 2.4 Очистка реактора `jdbc.db/pom.xml`

- Убрать из `<modules>`: `dialect` (активное подмножество `api`+`db`, теперь в SQL).
- Добавить: `dialect/metadata` (сборный модуль компаньонов).
- `api`, `record`, `impl`, `importer` остаются.

---

## 3. Связывание провайдеров через OSGi

**Текущее состояние:** каждая `DialectFactory` — `@Component(service = DialectFactory.class)`, с тегом
`@DialectName("MYSQL")`. `DatabaseServiceImpl` (`@Component(service = DatabaseService.class)`) принимает
`MetadataProvider` как **параметр метода** — то есть провайдер добывается в точке вызова, а не
инжектируется.

**Целевое состояние:** компаньон `MetadataProvider` публикуется как отдельный сервис и делается
разрешимым:

- Вариант A (рекомендуется): `<Db>MetadataProvider` как `@Component(service = MetadataProvider.class)`
  с `@DialectName("MYSQL")`. Вызывающий, которому нужны метаданные продукта, фильтрует сервисы
  `MetadataProvider` по `@DialectName`/имени продукта; запасной — `MetadataProvider.EMPTY`.
- Вариант B: `MetadataProviderFactory` по аналогии с `DialectFactory`
  (`MetadataProvider create(DialectInitData)`), зарегистрированная параллельно.

Важно: `Dialect` и `MetadataProvider` теперь **отдельные сервисы**. Кто ранее использовал `Dialect`
и для генерации SQL, и как провайдер метаданных, теперь держит **две** ссылки (диалект из `sql`,
провайдер из `jdbc.db`), обе разрешимы по одному `@DialectName`/имени продукта.

---

## 4. Предусловие сборки

`jdbc.db` (включая компаньоны) соберётся зелёным только когда доступны снапшоты `sql.api` +
`sql.dialect.*`: в SQL-репозитории сначала `mvn -q install`, затем `jdbc.db`. См.
[06](06-build-and-verification.md).
