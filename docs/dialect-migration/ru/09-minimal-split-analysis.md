<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 09 – Минимальный срез: что `sql` реально берёт из `jdbc.db`

Назад к [мастер-плану](README.md).

> 🌐 Язык: [Deutsch](../09-minimal-split-analysis.md) · **Русский** (этот файл)

**Назначение.** Это *второй* анализ, дополняющий План 1 (полный перенос `api`+`record`, см.
[README](README.md) и [02](02-dependency-and-layering.md)). Он отвечает на вопрос: *как выделить
**минимальную** часть, которую `sql` берёт из `jdbc.db`, при том что диалекты тоже нужны?* Повод —
идея встроить диалекты **статически по версиям**, чтобы не требовался JDBC-`record` для установки
свойств.

Документ **намеренно не** фиксирует один путь. Он нейтрально излагает факты и четыре варианта с
трудозатратами/рисками.

---

## 1. Исходный вопрос

План 1 переносит `api` + `record` **целиком** в `sql`. Механически просто, но делает `sql`
владельцем всей модели метаданных/интроспекции. Вопрос здесь: можно ли **меньше** — только то, что
реально нужно диалектам и собственному коду SQL?

---

## 2. Что нужно самому `sql` (очень мало)

Собственный код SQL (`guard`, `deparser`, `statement`) из `jdbc.db.api` затрагивает лишь **7 лёгких
листовых типов** — **никакого** `record`, **никакого** `api.meta`, никакой интроспекции:

| Пакет | Типы |
|---|---|
| `api.type` | `Datatype`, `BestFitColumnType` |
| `api.sql` | `BitOperation`, `OrderedColumn` |
| `api.schema` (References) | `SchemaReference`, `TableReference`, `ColumnReference` |

Сегодня они приходят даже только **транзитивно** через `dialect.api` (ни один pom SQL не объявляет
`jdbc.db.api` напрямую). Они самодостаточны и замкнуты (`BestFitColumnType`/`Datatype` — чистые
enum; `TableReference → SchemaReference → CatalogReference → Named`).

**Проблема не в коде SQL — она в самих диалектах.**

---

## 3. Что тянут диалекты — две роли

Конкретный диалект сегодня совмещает **две независимые обязанности**:

### Роль A — Нативный провайдер метаданных (~85–90 % связности)

`Dialect extends … MetadataProvider`. 6 «больших» диалектов переопределяют по 14–21 метода
`MetadataProvider` (`getAllIndexInfo`, `getAllPrimaryKeys`, `getAllImportedKeys`, `getAllTriggers`,
`getAllSequences`, `getUniqueConstraints`, …), которые запрашивают **`information_schema` /
каталожные таблицы против живого `Connection`** и **конструируют объекты `record.schema` для
возврата**.

- `record.schema.*` и `api.meta.IndexInfo`/`IndexInfoItem` используются **исключительно** для этого.
- Это **интроспекция** (*чтение* метаданных), а не генерация SQL.

| Диалект | Переопределений MetadataProvider | Конструирований `record.schema` |
|---|---|---|
| oracle | 21 | 19 |
| mssqlserver | 19 | 16 |
| postgresql | 19 | 16 |
| mysql | 15 | 11 |
| mariadb | 15 | 12 |
| h2 | 14 | 11 |
| **duckdb / sqlite / common** | **0** | **0** |

### Роль B — Потребитель DDL (~10–15 % связности)

API `DdlGenerator` принимает типы `api.schema` как **входной словарь**:

```java
default String createTable(TableReference table, List<ColumnDefinition> columns,
                           PrimaryKey primaryKey, boolean ifNotExists) { … }
default String alterTableAddColumn(TableReference table, ColumnDefinition column) { … }
default String createTrigger(String name, Trigger.TriggerTiming timing,
                             Trigger.TriggerEvent event, TableReference table, …) { … }
```

- Основная часть — `default`-методы **в общем API** (`DdlGenerator`), а не в диалектах. Только
  mssqlserver/oracle/mysql переопределяют некоторые с входами `api.schema`.
- **Никогда** не затрагивает `record` или `api.meta` — только `api.schema` как параметры.

### Роль C — Конфигурация/возможности: **нет** связности с метаданными

Ни `record`, ни `api.meta`, ни тяжёлые типы-определения `api.schema` не используются для
конфигурации/возможностей (см. §5).

---

## 4. Блокер `sealed` и замкнутый остров `api.schema`

Почему `api.schema` нельзя сократить до «пары References»:

- `SchemaObject` — `sealed … permits TableDefinition, ViewDefinition, MaterializedView, Sequence,
  Function, Procedure, Trigger, UserDefinedType` (8).
- `Constraint` — `sealed … permits PrimaryKey, ImportedKey, UniqueConstraint, CheckConstraint` (4).

Как только генератор затрагивает `Trigger`, `TableDefinition` или `PrimaryKey` (роль B), запечатанная
иерархия заставляет **весь пакет `api.schema`** (39 типов) компилироваться вместе.

**Хорошая новость:** `api.schema` **не** импортирует другие пакеты api (ни `meta`, ни `sql`, ни
`type`). Это **замкнутый остров** — тяжёлый, но переносимый целиком, не таща за собой интроспекцию
(`api.meta`, верхний уровень).

---

## 5. Конфигурация/версионирование уже развязаны

Идея «встроить статически по версиям» попадает в систему, которая **уже** так устроена:

- **`DialectInitData`** — лёгкий `record` (только примитивы: `productVersion`, major/minor, символ
  кавычки, ключевые слова, стили ResultSet). Он **не** зависит от `api.schema`/`record`. Есть
  `ansiDefaults()` (чисто статически, без JDBC) и withers (`withVersion(maj,min)`, …).
- **Возможности** — статические boolean-record с пресетами (`DdlCapabilities.full()/.minimal()`,
  `AggregateCapabilities.none()`, …). Никогда не выводятся из живых метаданных во время запроса.
- **Ветвление по версии** уже статическое — два устоявшихся паттерна:
  - **Подкласс на вариант:** `Db2OldAs400Dialect extends Db2Dialect` (переопределяет
    `allowsFromQuery()→false`), собственный `@DialectName("DB2_OLD_AS400")`.
  - **Внутри метода:** `dialectVersion.isUnknownOrAtLeast(8,0)` (mysql/oracle/postgresql/mssql/mariadb/sqlite).

Таким образом **генерация SQL уже полностью развязана с живыми метаданными** — читает только
константы + один `int` `dialectVersion`, задаваемый без Connection. Единственная связь с
метаданными — роль A (*выдача* метаданных).

---

## 6. Варианты (нейтрально, с трудозатратами/рисками)

### O1 — Разделение ролей: вынести роль MetadataProvider

Убрать `Dialect extends MetadataProvider`; методы-загрузчики (~7–21) 6 больших диалектов вынести в
классы-компаньоны (`<Db>MetadataProvider implements MetadataProvider`), остающиеся в `jdbc.db` при
интроспекции (связывание через OSGi). Диалект со стороны SQL сохраняет всё остальное.

- **Результат:** диалекты больше **не** импортируют `record`/`api.meta`. Ядро SQL = SQL-генерация
  диалекта + `api.schema` (остров) + `api.type` + `api.sql`. `record`, `api.meta`, интроспекция,
  `impl`, `importer` остаются в `jdbc.db`.
- **Трудозатраты:** средние — затронуть 6 диалектов; слом SPI `Dialect` (дефолты `getAll*` уходят в
  отдельный SPI `MetadataProvider`). duckdb/sqlite/common без изменений.
- **Риск:** нужно заново собрать OSGi-связывание диалект ↔ MetadataProvider; потребители
  `dialect.getAllIndexInfo(...)` должны перейти на отдельный провайдер.

### O2 — Разделение ролей + фиксированные подклассы по версиям

Как O1, плюс заменить ветвление `dialectVersion` **фиксированными классами по версиям** (паттерн
`Db2OldAs400Dialect`), свойства полностью вкомпилированы — снапшот `DialectInitData` **больше не
нужен**.

- **Результат:** максимальная развязка; диалект полностью статичен, без метаданных времени
  выполнения.
- **Трудозатраты:** высокие — на каждую БД несколько классов версий; логика выбора/фабрики по версии.
- **Риск:** взрыв числа классов; выбор версии должен быть надёжным (имя/версия продукта → класс).

### O3 — Срез по замыканию без рефакторинга

Без слома API. Диалекты + их транзитивное замыкание в SQL: `dialect.*`, `api.type`, `api.sql`, весь
`api.schema`, `api.meta.IndexInfo(+Item)`, `MetadataProvider`, `record.schema`. В `jdbc.db` остаётся
чистый движок интроспекции (`DatabaseService`, `MetaDataQueries`, `SnapshotBuilder`, `meta` минус
IndexInfo, `record.meta`, `impl`, `importer`).

- **Результат:** меньше, чем полный перенос (движок остаётся), но `record.schema` + `MetadataProvider`
  переезжают. Одностороннее `jdbc.db → sql`, без цикла, без изменения API.
- **Трудозатраты:** средние — `api`/`record` нужно **разделить** (schema/type/sql/IndexInfo → SQL;
  остальное остаётся).
- **Риск:** пакет `api`/`record` разделён между двумя репозиториями; провести границу аккуратно.

### O4 — Полный перенос (= План 1)

`api` + `record` целиком в SQL. См. [README](README.md)/[02](02-dependency-and-layering.md).

- **Результат:** простейшая механика; SQL владеет всей моделью метаданных/интроспекции.
- **Трудозатраты:** низкие–средние; **риск:** низкий; но **не** «минимально».

---

## 7. Сравнение

| | O1 Разделение ролей | O2 + классы версий | O3 Срез по замыканию | O4 Полный перенос |
|---|---|---|---|---|
| SQL получает `record` | нет | нет | `record.schema` | целиком |
| SQL получает `api.meta` | нет | нет | только IndexInfo | целиком |
| SQL получает `api.schema` | целиком (остров) | целиком (остров) | целиком (остров) | целиком |
| `Dialect extends MetadataProvider` | **убрано** | убрано | остаётся | остаётся |
| Слом API | да (SPI) | да (SPI) | нет | нет |
| Затронутые диалекты | 6 | 6 + классы версий | 0 (только зависимости) | 0 |
| `api`/`record` разделены | да | да | да | нет |
| Размер ядра SQL | **минимальный** | **минимальный** | средний | большой |
| Трудозатраты | средние | высокие | средние | низкие–средние |

**Общее для всех вариантов:** `api.type` + `api.sql` всегда уходят в SQL (чистые листья, используются
только диалектами); `impl` + `importer/csv` (ETL) всегда остаются в `jdbc.db`; результат всегда —
одностороннее `jdbc.db → sql` без цикла.

---

## 8. Связь с Планом 1

План 1 ([README](README.md)) соответствует **O4**. Этот анализ показывает, что **O1/O2** дают
по-настоящему минимальное ядро SQL (следуя идее «статически по версиям, без JDBC-record») ценой слома
SPI `Dialect` и правки 6 больших диалектов; **O3** — компромисс без слома API. Выбор намеренно
оставлен открытым.

> **Полный план для O1/O2:** см. [`minimal-split/`](minimal-split/README.md) — исполняемый
> пошаговый план разделения ролей (вынос роли A из диалектов).
