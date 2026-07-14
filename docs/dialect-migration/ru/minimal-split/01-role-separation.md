<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 01 – Разделение ролей: `Dialect` ↔ `MetadataProvider`

Назад к [плану минимального среза](README.md).

Это **ключевой рефакторинг**. Он выполняется целиком **внутри `jdbc.db`** и там собирается зелёным
ещё до переноса чего-либо в SQL-репозиторий. Только когда эта развязка готова, диалект становится
достаточно лёгким для минимального переноса.

---

## 1. Исходное состояние (обосновано)

- `dialect/api/.../Dialect.java`: `public interface Dialect extends IdentifierQuoter, LiteralQuoter,
  DialectCapabilitiesProvider, TypeMapper, MetadataProvider`. Через `MetadataProvider` (из `api`)
  каждый диалект *является* провайдером метаданных.
- `MetadataProvider` (`api/.../MetadataProvider.java`): ~40 `default`-методов (возврат
  `Optional.empty()`/`List.of()`); импортирует ~25 `api.schema.*` + `api.meta.IndexInfo`/`TypeInfo`.
- 6 больших диалектов (h2, mariadb, mssqlserver, mysql, oracle, postgresql) переопределяют по 14–21
  из этих методов реальными запросами к `information_schema` и строят объекты `record.schema`.
- `duckdb`, `sqlite`, `common`, все фабрики: **нет** переопределений (наследуют пустые дефолты).
- **Вызывающие уже параметризованы провайдером:** `DatabaseServiceImpl` принимает `MetadataProvider`
  как **аргумент** и вызывает на нём `getAllIndexInfo/getAllPrimaryKeys/...` — он **не** ссылается на
  `Dialect`. Примеры:
  ```java
  public MetaInfo createMetaInfo(Connection connection, MetadataProvider metadataProvider) { … }
  Optional<List<IndexInfo>> providerIndexes = provider.getAllIndexInfo(connection, null, null);
  Optional<List<PrimaryKey>> providerPKs   = provider.getAllPrimaryKeys(connection, null, null);
  ```

**Вывод:** движок и диалект фактически уже разделены; их сваривает только наследование.

---

## 2. Целевая картина

```
Dialect (генерация SQL)              MetadataProvider (интроспекция)
  IdentifierQuoter                     getAllIndexInfo(...)
  LiteralQuoter                        getAllPrimaryKeys(...)
  DialectCapabilitiesProvider          getAllImportedKeys(...)
  TypeMapper                           ...
  (НЕТ extends MetadataProvider)       ▲
                                       │ реализует
                          <Db>MetadataProvider  (новое, остаётся в jdbc.db)
```

- `Dialect` теряет `extends MetadataProvider` и все унаследованные дефолты `getAll*`.
- На каждый большой диалект создаётся `<Db>MetadataProvider implements MetadataProvider` с вынесенными
  методами (поведение без изменений). Эти классы остаются при интроспекции в `jdbc.db`.
- `MetadataProvider.EMPTY` остаётся дефолтом для диалектов без нативных метаданных (duckdb/sqlite/…).

---

## 3. Шаги (внутри `jdbc.db`)

### 3.1 Вынести классы-компаньоны (6×)

Для каждого из `h2, mariadb, mssqlserver, mysql, oracle, postgresql`:

1. Новый класс `dialect/metadata/<db>/.../<Db>MetadataProvider.java`,
   `implements org.eclipse.daanse.jdbc.db.api.MetadataProvider`.
2. Переопределённые методы `getAll*`/`get*` **перенести туда** (вместе с приватными помощниками:
   `readImportedKey`, `PkBuilder`, `resolveSchema`, `mapMySqlIndexType`).
3. В классе диалекта остаются только генерация SQL, кавычки, возможности, TypeMapper.
4. Почистить импорты: класс диалекта после этого **не** импортирует `record.*` и `api.meta.*`
   (проверка: `grep` должен быть пуст). Класс `<Db>MetadataProvider` теперь несёт эти импорты.

> Оставшиеся импорты `api.schema` в классе диалекта (роль B, только mssqlserver/oracle/mysql)
> **намеренны** — это входной словарь DDL, он позже переезжает как замкнутый остров в SQL.

### 3.2 Убрать наследование

`dialect/api/.../Dialect.java`: убрать `MetadataProvider` из списка `extends`; удалить импорт
`org.eclipse.daanse.jdbc.db.api.MetadataProvider`. `BestFitColumnType getType(...)` остаётся
(использует `api.type`, не модель метаданных).

### 3.3 Предоставление/связывание компаньонов (OSGi)

Сегодня каждая фабрика регистрирует `@Component(service = DialectFactory.class, …)`;
`DatabaseServiceImpl` — `@Component(service = DatabaseService.class)`. После разделения:

- Опубликовать `<Db>MetadataProvider` как отдельный DS-компонент с тегом `@DialectName("MYSQL")`
  (по аналогии с фабриками), **или** предоставить через `MetadataProviderFactory` параллельно
  `DialectFactory`.
- Вызывающие, которые ранее передавали `Dialect` как `MetadataProvider`, теперь берут нужный
  `<Db>MetadataProvider` (по `@DialectName`/имени продукта), запасной — `MetadataProvider.EMPTY`.
- Детали связывания в [04](04-consumer-rewiring.md).

### 3.4 Перевести вызывающих

- `DatabaseServiceImpl`: сигнатуры без изменений (параметр `MetadataProvider`). Меняется только
  **предоставление** провайдера в точке вызова (не «диалект», а компаньон).
- Тесты, ранее вызывавшие `service.createMetaInfo(conn, dialect)`, теперь вызывают
  `service.createMetaInfo(conn, new MySqlMetadataProvider())` (и т. п.).

---

## 4. Трудозатраты и объём

| Диалект | методов к выносу (ориентир.) | нужен компаньон |
|---|---|---|
| oracle | 21 | да |
| mssqlserver | 19 | да |
| postgresql | 19 | да |
| mariadb | 15 | да |
| mysql | 15 | да |
| h2 | 14 | да |
| duckdb, sqlite | 0 | нет (EMPTY) |
| common, test-support | 0 | нет |

Чистый рефакторинг-перенос (методы + приватные помощники), без перестройки поведения. Слом SPI
`Dialect` — ровно одна строка (список `extends`) плюс изменённое предоставление провайдеров.

---

## 5. Приёмка этой фазы (только `jdbc.db`)

- [ ] `Dialect` без `extends MetadataProvider`; нет импорта `MetadataProvider` в `Dialect.java`.
- [ ] 6 классов `<Db>MetadataProvider` с полностью вынесенными методами.
- [ ] `grep -rE "import .*\.(record|api\.meta)\." dialect/db/{h2,mariadb,mssqlserver,mysql,oracle,postgresql}/src/main` → **пусто**.
- [ ] `mvn -q verify` в реакторе `jdbc.db` зелёный (включая round-trip / MetaInfo-тесты против компаньонов).
