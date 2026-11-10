<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# Руководство по внедрению (пошагово) — минимальный срез + версии диалектов

Назад к [плану минимального среза](README.md).

> 🌐 Язык: [Deutsch](../../minimal-split/IMPLEMENTATION-GUIDE.md) · **Русский** (этот файл)

**Для кого?** Это руководство написано для **junior-разработчика**. Каждый шаг говорит **точно**: в
каком **репозитории**, каком **модуле**, каком **файле** и **что** ты делаешь, с **примерами кода**
(до/после) и **командой проверки**. Выполняй части **по порядку** и делай сборку **после каждой
контрольной точки**. Если контрольная точка красная — **не продолжай**, сначала почини.

**Две цели этого руководства:**
1. **Минимальный срез** (части 1–4): минимально вынести стек диалектов из `jdbc.db` в `sql`.
2. **Версии диалектов** (часть 5): встроить диалекты статически по версиям (твоя идея — «без
   JDBC-record для установки свойств»).

В конце (часть 6) **всё собирается и работает** в обоих репозиториях.

---

## Обозначения / соглашения

- **Репозиторий `jdbc.db`** = `/home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db`
- **Репозиторий `sql`** = `/home/stbischof/git/daanse/org.eclipse.daanse.sql`
- Префикс пакета старый: `org.eclipse.daanse.jdbc.db.…` — новый (сторона SQL): `org.eclipse.daanse.sql.…`
- «Команда проверки» означает: выполнять в каталоге соответствующего репозитория.
- **Важно:** всегда используй `git mv` (не копирование+удаление) — это сохраняет историю Git.

---

# Часть 0 — Подготовка

### Шаг 0.1 — Оба репозитория, рабочие ветки

**Репо:** оба. **Действие:** создать по ветке.

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db && git checkout -b feat/minimal-split
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql     && git checkout -b feat/minimal-split
```

### Шаг 0.2 — Исходная сборка (должна быть зелёной до любых изменений)

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db && mvn -q clean install
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql     && mvn -q clean install
```

> Если здесь уже что-то красное — это **не** из-за тебя; сначала выясни с командой.

### Шаг 0.3 — Запомни 3 группы диалектов

- **6 «больших» диалектов** (имеют роль A метаданных): `h2, mariadb, mssqlserver, mysql, oracle, postgresql`
- **2 «маленьких» диалекта** (нет роли A): `duckdb, sqlite`
- **Инфраструктура:** `common, test-support`

Компаньон в части 1 получают только **6 больших**.

---

# Часть 1 — Фаза R: разделение ролей (целиком в репозитории `jdbc.db`)

**Цель:** из каждого большого диалекта вынести методы «чтения метаданных» (роль A) в отдельный
класс-компаньон и отвязать `Dialect` от `MetadataProvider`. **Пока ничего не переезжает** — всё
остаётся в `jdbc.db`, но после этого должно собираться зелёным.

### Шаг 1.1 — Создать новый модуль `dialect/metadata`

**Репо:** `jdbc.db`. **Действие:** новая папка + агрегатор-pom.

Новый файл: `dialect/metadata/pom.xml`
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.eclipse.daanse</groupId>
    <artifactId>org.eclipse.daanse.jdbc.db.dialect</artifactId>
    <version>${revision}</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>org.eclipse.daanse.jdbc.db.dialect.metadata</artifactId>
  <packaging>pom</packaging>
  <name>Eclipse Daanse JDBC DB Dialect Metadata Providers</name>
  <modules>
    <module>mysql</module>
    <module>mariadb</module>
    <module>h2</module>
    <module>mssqlserver</module>
    <module>oracle</module>
    <module>postgresql</module>
  </modules>
</project>
```

Изменить файл: `dialect/pom.xml` → добавить `metadata` в `<modules>`:
```xml
<modules>
  <module>api</module>
  <module>db</module>
  <module>metadata</module>   <!-- НОВОЕ -->
</modules>
```

### Шаг 1.2 — Leaf-pom для первого компаньона (MySQL)

**Репо:** `jdbc.db`. **Новый файл:** `dialect/metadata/mysql/pom.xml`
```xml
<project ...>
  <parent>
    <groupId>org.eclipse.daanse</groupId>
    <artifactId>org.eclipse.daanse.jdbc.db.dialect.metadata</artifactId>
    <version>${revision}</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>org.eclipse.daanse.jdbc.db.dialect.metadata.mysql</artifactId>
  <name>Eclipse Daanse JDBC DB Metadata Provider MySQL</name>
  <dependencies>
    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
    <!-- MetadataProvider + api.meta + api.schema из api -->
    <dependency>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.jdbc.db.api</artifactId>
      <version>${revision}</version>
    </dependency>
    <!-- объекты record.schema, которые возвращаются -->
    <dependency>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.jdbc.db.record</artifactId>
      <version>${revision}</version>
    </dependency>
    <!-- аннотация @DialectName -->
    <dependency>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.jdbc.db.dialect.api</artifactId>
      <version>${revision}</version>
    </dependency>
  </dependencies>
</project>
```

### Шаг 1.3 — Создать класс-компаньон `MySqlMetadataProvider`

**Репо:** `jdbc.db`. **Новый файл:**
`dialect/metadata/mysql/src/main/java/org/eclipse/daanse/jdbc/db/dialect/metadata/mysql/MySqlMetadataProvider.java`

Скопируй **заголовок EPL** из существующего файла в начало. Затем:

```java
package org.eclipse.daanse.jdbc.db.dialect.metadata.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.MetadataProvider;
import org.eclipse.daanse.jdbc.db.api.meta.IndexInfo;
import org.eclipse.daanse.jdbc.db.api.meta.IndexInfoItem;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnReference;
import org.eclipse.daanse.jdbc.db.api.schema.ImportedKey;
import org.eclipse.daanse.jdbc.db.api.schema.PrimaryKey;
import org.eclipse.daanse.jdbc.db.api.schema.SchemaReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.jdbc.db.dialect.api.DialectName;
import org.eclipse.daanse.jdbc.db.record.schema.IndexInfoItemRecord;
import org.eclipse.daanse.jdbc.db.record.schema.IndexInfoRecord;
import org.eclipse.daanse.jdbc.db.record.schema.PrimaryKeyRecord;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * MySQL-native metadata provider. Extracted from MySqlDialect (role A) so that
 * the SQL-generation dialect no longer depends on record/api.meta.
 */
@Component(service = MetadataProvider.class, scope = ServiceScope.SINGLETON)
@DialectName("MYSQL")
public class MySqlMetadataProvider implements MetadataProvider {

    // ---- ЗДЕСЬ: методы getAll*/get*, перенесённые из MySqlDialect ----
    // Пример: getAllPrimaryKeys (взято 1:1 из MySqlDialect)

    @Override
    public Optional<List<PrimaryKey>> getAllPrimaryKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, kcu.COLUMN_NAME, kcu.ORDINAL_POSITION
                FROM information_schema.TABLE_CONSTRAINTS tc
                JOIN information_schema.KEY_COLUMN_USAGE kcu
                  ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY' AND tc.TABLE_SCHEMA = ?
                ORDER BY tc.TABLE_NAME, kcu.ORDINAL_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        Map<String, PkBuilder> pkMap = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("CONSTRAINT_NAME");
                    String tableName = rs.getString("TABLE_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    String key = tableName + "." + constraintName;
                    pkMap.computeIfAbsent(key, k -> new PkBuilder(tableName, constraintName, schemaName))
                            .addColumn(columnName);
                }
            }
        }
        List<PrimaryKey> result = new ArrayList<>();
        for (PkBuilder builder : pkMap.values()) {
            result.add(builder.build());
        }
        return Optional.of(List.copyOf(result));
    }

    // ... аналогично getAllImportedKeys, getAllExportedKeys, getAllIndexInfo,
    //     getUniqueConstraints, getAllTriggers, getAllSequences (все из MySqlDialect) ...

    // ---- ЗДЕСЬ: приватные помощники, нужные этим методам ----
    private String resolveSchema(String schema, Connection connection) throws SQLException {
        if (schema != null) return schema;
        String catalog = connection.getCatalog();
        if (catalog != null) return catalog;
        return connection.getSchema() != null ? connection.getSchema() : "";
    }

    private static IndexInfoItem.IndexType mapMySqlIndexType(String indexType) {
        if (indexType == null) return IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        return switch (indexType.toUpperCase()) {
            case "HASH" -> IndexInfoItem.IndexType.TABLE_INDEX_HASHED;
            default -> IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        };
    }

    // Builder для составных первичных ключей — 1:1 из MySqlDialect
    private static final class PkBuilder {
        private final String tableName;
        private final String constraintName;
        private final String schemaName;
        private final List<String> columns = new ArrayList<>();
        PkBuilder(String tableName, String constraintName, String schemaName) {
            this.tableName = tableName; this.constraintName = constraintName; this.schemaName = schemaName;
        }
        void addColumn(String c) { columns.add(c); }
        PrimaryKey build() {
            TableReference table = new TableReference(
                    Optional.of(new SchemaReference(schemaName)), tableName);
            List<ColumnReference> cols = new ArrayList<>();
            for (String c : columns) cols.add(new ColumnReference(Optional.of(table), c));
            return new PrimaryKeyRecord(table, cols, Optional.ofNullable(constraintName));
        }
    }
}
```

> **Важно:** тела методов бери **дословно** из `MySqlDialect.java`. Перенеси **также** все приватные
> помощники, которые используются только этими методами (`readImportedKey`, `mapTriggerTiming`,
> `mapTriggerEvent`, `mapReferentialAction`, `mapMySqlIndexType`, `resolveSchema`, `PkBuilder` …).

### Шаг 1.4 — Удалить перенесённые методы из `MySqlDialect`

**Репо:** `jdbc.db`. **Файл:**
`dialect/db/mysql/src/main/java/.../mysql/MySqlDialect.java`

1. Удалить все `@Override`-методы, скопированные в `MySqlMetadataProvider` (`getAllPrimaryKeys`,
   `getAllIndexInfo`, `getAllImportedKeys`, …).
2. Удалить приватные помощники, используемые только там (`PkBuilder`, `resolveSchema`,
   `mapMySqlIndexType`, …).
3. Удалить ставшие неиспользуемыми импорты — прежде всего все
   `import org.eclipse.daanse.jdbc.db.record.*` и `import org.eclipse.daanse.jdbc.db.api.meta.*`.

**Проверка (должно быть пусто):**
```bash
grep -nE "import org.eclipse.daanse.jdbc.db.(record|api\.meta)\." \
  dialect/db/mysql/src/main/java/org/eclipse/daanse/jdbc/db/dialect/db/mysql/MySqlDialect.java
```

> **Внимание, роль B:** импорты `…api.schema.*` (например `TableReference`, `ColumnDefinition`)
> **остаются** в классе диалекта, если используются в DDL-методах — они разрешены и позже переедут как
> остров в SQL.

### Шаг 1.5 — Повторить для 5 остальных больших диалектов

**Репо:** `jdbc.db`. Повтори шаги 1.2–1.4 для: `h2, mariadb, mssqlserver, oracle, postgresql`. Для
каждого — `dialect/metadata/<db>/…/<Db>MetadataProvider.java` с `@DialectName("<NAME>")`. Значения
`@DialectName` смотри в соответствующей `<Db>DialectFactory` (например `H2`, `MARIADB`, `MSSQLSERVER`,
`ORACLE`, `POSTGRESQL`).

> `duckdb`, `sqlite`, `common`: **ничего не делать** — у них нет методов роли A.

### Шаг 1.6 — Отвязать `Dialect` от `MetadataProvider`

**Репо:** `jdbc.db`. **Файл:** `dialect/api/src/main/java/.../dialect/api/Dialect.java`

До:
```java
import org.eclipse.daanse.jdbc.db.api.MetadataProvider;
...
public interface Dialect
        extends IdentifierQuoter, LiteralQuoter, DialectCapabilitiesProvider, TypeMapper, MetadataProvider {
```
После:
```java
// (импорт удалён)
public interface Dialect
        extends IdentifierQuoter, LiteralQuoter, DialectCapabilitiesProvider, TypeMapper {
```

`BestFitColumnType getType(ResultSetMetaData, int)` и импорты `api.type` **остаются**.

### Шаг 1.7 — Перевести вызывающих и тесты на компаньон

**Репо:** `jdbc.db`.

- **`DatabaseServiceImpl`** (`impl/.../DatabaseServiceImpl.java`) **не** меняет сигнатуры — он
  принимает `MetadataProvider` как параметр (`createMetaInfo(Connection, MetadataProvider)`,
  `getCatalogs(Connection, MetadataProvider)`). Меняется только **точка вызова** — теперь передаётся
  компаньон.
- **Тесты**, ранее передававшие диалект как провайдер, меняют одну строку:

До:
```java
MetaInfo mi = service.createMetaInfo(conn, new MySqlDialect(init));
```
После:
```java
MetaInfo mi = service.createMetaInfo(conn, new MySqlMetadataProvider());
```

> Для диалектов без нативных метаданных (duckdb/sqlite): используй `MetadataProvider.EMPTY`.

### ✅ Контрольная точка 1 (репозиторий `jdbc.db` должен быть зелёным)

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db
# main диалектов свободен от record/api.meta:
grep -rE "import org.eclipse.daanse.jdbc.db.(record|api\.meta)\." \
  dialect/db/{h2,mariadb,mssqlserver,mysql,oracle,postgresql}/src/main   # → пусто
# Dialect больше не MetadataProvider:
grep -n "extends" dialect/api/src/main/java/org/eclipse/daanse/jdbc/db/dialect/api/Dialect.java
mvn -q clean verify   # должно быть зелёным
```

Если зелёно: **фаза R завершена.** Коммит: `git commit -am "Phase R: split MetadataProvider role out of dialects"`.

---

# Часть 2 — Фаза S: разделение `api` (schema/type/sql → новый `sql.api`)

**Цель:** только пакеты, нужные диалекту (`api.schema`, `api.type`, `api.sql`), становятся новым
модулем SQL. Остаток интроспекции (`api.meta`, `MetadataProvider`, `DatabaseService`, …) остаётся в
`jdbc.db`.

### Шаг 2.1 — Создать модуль `sql/api`

**Репо:** `sql`. **Новый файл:** `api/pom.xml`
```xml
<project ...>
  <parent>
    <groupId>org.eclipse.daanse</groupId>
    <artifactId>org.eclipse.daanse.sql</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>org.eclipse.daanse.sql.api</artifactId>
  <name>Eclipse Daanse SQL API (schema/type/sql model)</name>
  <dependencies>
    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
  </dependencies>
</project>
```
**Изменить файл:** `pom.xml` (корень SQL) → поставить `api` в начало `<modules>`:
```xml
<modules>
  <module>api</module>       <!-- НОВОЕ -->
  <module>guard</module>
  <module>deparser</module>
  <module>statement</module>
</modules>
```

### Шаг 2.2 — Перенести пакеты (`git mv`) + переименовать

**Репо:** оба (источник `jdbc.db`, цель `sql`). Для каждого из 3 пакетов (`schema`, `type`, `sql`):

```bash
# Пример для 'schema' — аналогично для 'type' и 'sql'
SRC=/home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db/api/src/main/java/org/eclipse/daanse/jdbc/db/api/schema
DST=/home/stbischof/git/daanse/org.eclipse.daanse.sql/api/src/main/java/org/eclipse/daanse/sql/api/schema
mkdir -p "$(dirname "$DST")"
```

> **Внимание:** `git mv` работает только внутри одного репозитория. Через границу репозиториев:
> перенеси папку через `mv`, затем в `jdbc.db` — `git rm -r` (старый путь), а в `sql` — `git add`
> (новый путь). История при этом через границу теряется — это нормально (см. план полного переноса
> [../07-anomalies-and-risks.md](../07-anomalies-and-risks.md) §5).

Затем во **всех** перенесённых файлах заменить имя пакета:
```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql/api
grep -rl "org.eclipse.daanse.jdbc.db.api.schema" . | xargs sed -i \
  's/org\.eclipse\.daanse\.jdbc\.db\.api\.schema/org.eclipse.daanse.sql.api.schema/g'
# аналогично .type и .sql
```
И поправить `package-info.java` в каждом из трёх пакетов.

### Шаг 2.3 — Остаток `jdbc.db/api` направить на `sql.api`

**Репо:** `jdbc.db`. Оставшийся модуль `api` (теперь только `api.meta` + верхний уровень интроспекции)
импортирует типы схемы. Перенаправить эти импорты:
```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db/api
grep -rl "org.eclipse.daanse.jdbc.db.api.\(schema\|type\|sql\)" . | xargs sed -i \
  -e 's/org\.eclipse\.daanse\.jdbc\.db\.api\.schema/org.eclipse.daanse.sql.api.schema/g' \
  -e 's/org\.eclipse\.daanse\.jdbc\.db\.api\.type/org.eclipse.daanse.sql.api.type/g' \
  -e 's/org\.eclipse\.daanse\.jdbc\.db\.api\.sql/org.eclipse.daanse.sql.api.sql/g'
```
**Файл:** `api/pom.xml` → добавить зависимость на новый модуль SQL:
```xml
<dependency>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.sql.api</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Шаг 2.4 — Перевести `record`, `impl`, компаньоны на `sql.api`

**Репо:** `jdbc.db`, модули `record`, `impl`, `dialect/metadata/*`. Та же замена `sed` импортов
`api.{schema,type,sql}`, что и в 2.3; в каждом `pom.xml` добавить зависимость `sql.api`.

### ✅ Контрольная точка 2

```bash
# sql.api собирается изолированно:
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql && mvn -q -pl org.eclipse.daanse.sql.api -am install
# jdbc.db собирается против свежего снапшота sql.api:
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db && mvn -q clean verify
```
Коммит, если зелёно.

---

# Часть 3 — Фаза D: перенос диалектов в `sql`

**Цель:** `dialect.api` (теперь без MetadataProvider) + `common` + `test-support` + 8 активных
диалектов в `sql`. Им теперь **не** нужны `record` и `api.meta`.

### Шаг 3.1 — Агрегаторы в SQL-репозитории

**Репо:** `sql`. **Новые файлы:** `dialect/pom.xml` и `dialect/db/pom.xml` (packaging=pom). Шаблон см.
[06-build-and-verification.md](06-build-and-verification.md) §1. Добавить `dialect` в корневой
`<modules>`.

### Шаг 3.2 — Перенести + переименовать `dialect.api`

**Репо:** оба. Папка `jdbc.db/dialect/api` → `sql/dialect/api`. Переименование пакета
`org.eclipse.daanse.jdbc.db.dialect.api` → `org.eclipse.daanse.sql.dialect.api`. **Pom:** родитель на
`org.eclipse.daanse.sql.dialect`, зависимость `jdbc.db.api` → `org.eclipse.daanse.sql.api` (внутри
реактора, `${project.version}`).

### Шаг 3.3 — Перенести `common` и `test-support`

**Репо:** оба. Как 3.2; зависимости → `sql.dialect.api` / `sql.api`.

### Шаг 3.4 — Перенести 8 активных диалектов (mysql ПЕРЕД mariadb)

**Репо:** оба. Порядок: `mysql`, затем `mariadb` (т. к. `mariadb → mysql`), затем `duckdb, h2,
mssqlserver, oracle, postgresql, sqlite` в любом порядке.

По каждому диалекту:
1. Папка `jdbc.db/dialect/db/<db>` → `sql/dialect/db/<db>`.
2. Переименование пакета `…dialect.db.<db>` → `…sql.dialect.db.<db>`; импорты схемы
   `…jdbc.db.api.schema` → `…sql.api.schema` (и т. д.).
3. **Переписать pom** — пример `mysql` (до/после ключевых зависимостей):

До (`dialect/db/mysql/pom.xml`):
```xml
<dependency><artifactId>org.eclipse.daanse.jdbc.db.dialect.api</artifactId><version>${revision}</version></dependency>
<dependency><artifactId>org.eclipse.daanse.jdbc.db.dialect.db.common</artifactId><version>${revision}</version></dependency>
<dependency><artifactId>org.eclipse.daanse.jdbc.db.record</artifactId><version>${revision}</version></dependency>   <!-- убрать! -->
<dependency><artifactId>org.eclipse.daanse.jdbc.db.impl</artifactId><version>${revision}</version><scope>test</scope></dependency> <!-- убрать! -->
```
После (`sql/dialect/db/mysql/pom.xml`):
```xml
<dependency><artifactId>org.eclipse.daanse.sql.dialect.api</artifactId><version>${project.version}</version></dependency>
<dependency><artifactId>org.eclipse.daanse.sql.dialect.db.common</artifactId><version>${project.version}</version></dependency>
<dependency><artifactId>org.eclipse.daanse.sql.api</artifactId><version>${project.version}</version></dependency>
<!-- НЕТ record, НЕТ impl — роль A вынесена -->
<!-- Тест-драйверы/Testcontainers остаются (mysql-connector-j, testcontainers) -->
```

> Зависимости `record` и `impl`(test) **исчезают**, потому что роль метаданных (которая их требовала)
> в части 1 переехала в `jdbc.db/dialect/metadata`. Если тесту диалекта всё ещё нужен `impl`
> (round-trip), перенеси этот тест в `jdbc.db/dialect/metadata/<db>` или временно пометь `@Disabled`
> (см. [01-role-separation.md](01-role-separation.md)).

4. Внести `<module><db></module>` в `sql/dialect/db/pom.xml`.

### ✅ Контрольная точка 3

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql
# В пакетах диалектов больше нет jdbc.db:
grep -rl "org.eclipse.daanse.jdbc.db" dialect/ --include=*.java   # → пусто
mvn -q -pl org.eclipse.daanse.sql.dialect.db.mysql -am install     # собирается вместе с зависимостями
```

---

# Часть 4 — Фаза C: перепривязка потребителей + демонтаж `jdbc.db`

### Шаг 4.1 — Потребители SQL внутри реактора

**Репо:** `sql`, модули `guard`, `deparser`, `statement`. В их `pom.xml` заменить внешние
`jdbc.db.dialect.*` на внутренние `sql.dialect.*`; импорты Java `org.eclipse.daanse.jdbc.db.dialect`
→ `org.eclipse.daanse.sql.dialect`, `…jdbc.db.api.{schema,type,sql}` → `…sql.api.*`. Подробнее:
[04-consumer-rewiring.md](04-consumer-rewiring.md) §1.

### Шаг 4.2 — Java 21 в SQL-репозитории

**Репо:** `sql`. **Файл:** `pom.xml` (корень) → `java.version`/`java.release` на `21` (см.
[06-build-and-verification.md](06-build-and-verification.md) §1).

### Шаг 4.3 — `jdbc.db` на опубликованные снапшоты `sql.*`

**Репо:** `jdbc.db`, модули `impl`, `importer/csv`, `dialect/metadata/*`. В `pom.xml`:
зависимости диалектов/схемы на `sql.*` (внешние, `0.0.1-SNAPSHOT`). **Файл:** `jdbc.db/pom.xml` →
убрать из `<modules>` (переехавший) агрегат `dialect` или свести к `dialect/metadata`.

### ✅ Контрольная точка 4 (соблюдай порядок!)

```bash
# 1) сначала установить SQL:
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql && mvn -q clean install
# 2) jdbc.db против снапшотов sql.*:
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db && mvn -q clean verify
```

**На этом минимальный срез завершён.** Часть 5 — опциональное расширение с версиями диалектов.

---

# Часть 5 — Фаза V: встроить версии диалектов статически (твоя идея, O2)

**Цель:** вместо вычисления версии из `DialectInitData` во время выполнения
(`isUnknownOrAtLeast(8,0)`) — **фиксированные классы на версию** с вкомпилированными свойствами.
Пример MySQL. **Репо:** `sql`, модуль `sql/dialect/db/mysql`.

### Шаг 5.1 — Определить базовый класс

`MySqlDialect` остаётся **общей базой** (генерация SQL, кавычки). Сегодня внутри ветвится:
```java
// MySqlDialect.java (сегодня)
public CteGenerator cteGenerator() {
    boolean supports = DialectVersion.UNKNOWN.equals(dialectVersion) || dialectVersion.atLeast(8, 0);
    ...
}
public boolean requiresOrderByAlias() { return dialectVersion.isUnknownOrAtLeast(5, 7); }
```

### Шаг 5.2 — Создать два класса версий

**Новый файл:** `…/mysql/MySql8Dialect.java`
```java
package org.eclipse.daanse.sql.dialect.db.mysql;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;

/** MySQL 8.x — recursive CTE, ORDER BY alias required, percentile functions. */
public class MySql8Dialect extends MySqlDialect {
    public MySql8Dialect(DialectInitData init) {
        super(init);
    }
    @Override public boolean requiresOrderByAlias() { return true; }   // фиксировано, вместо проверки версии
    // cteGenerator(): рекурсивный вариант — включить фиксировано
    // supportsPercentileCont()/Disc(): true
}
```
**Новый файл:** `…/mysql/MySql57Dialect.java`
```java
package org.eclipse.daanse.sql.dialect.db.mysql;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;

/** MySQL 5.7 — no recursive CTE, ORDER BY alias required, no percentile. */
public class MySql57Dialect extends MySqlDialect {
    public MySql57Dialect(DialectInitData init) {
        super(init);
    }
    @Override public boolean requiresOrderByAlias() { return true; }
    // cteGenerator(): НЕ-рекурсивный вариант
    // supportsPercentileCont()/Disc(): false
}
```

> Вынеси сегодняшние ветвления `dialectVersion.isUnknownOrAtLeast(...)` из `MySqlDialect` и сделай их
> **фиксированными** `@Override`-возвратами в классах версий. То, что не зависит от версии, остаётся в
> `MySqlDialect`.

### Шаг 5.3 — Фабрика выбирает по версии

**Файл:** `…/mysql/MySqlDialectFactory.java`

До:
```java
@Override
public Function<DialectInitData, MySqlDialect> getConstructorFunction() {
    return MySqlDialect::new;
}
```
После (выбор вместо фиксированного конструктора):
```java
@Override
public Dialect createDialect(DialectInitData init) {
    if (MySqlDialect.looksLikeInfobright(init)) {
        throw new IllegalStateException("Snapshot looks like Infobright; use InfobrightDialectFactory");
    }
    // Версия определяет конкретный класс; UNKNOWN → новейший известный (8.x)
    var v = init.version();
    if (v.atLeast(8, 0) || v.equals(org.eclipse.daanse.sql.dialect.api.DialectVersion.UNKNOWN)) {
        return new MySql8Dialect(init);
    }
    return new MySql57Dialect(init);
}
```
`getConstructorFunction()` тогда больше не нужен (или возвращает базу для совместимости).

### Шаг 5.4 — Unit-тест без живой БД

**Новый файл:** `…/mysql/src/test/java/.../MySql8DialectTest.java`
```java
@Test
void mysql8_requiresOrderByAlias_true_offline() {
    var init = DialectInitData.ansiDefaults()
            .withQuoteIdentifierString("`")
            .withVersion(8, 0);          // чисто статически, БЕЗ Connection
    var d = new MySql8Dialect(init);
    assertThat(d.requiresOrderByAlias()).isTrue();
}
```

### ✅ Контрольная точка 5

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql
mvn -q -pl org.eclipse.daanse.sql.dialect.db.mysql -am verify   # классы версий + офлайн-тесты зелёные
```

> Повторяй 5.1–5.4 для других БД только по необходимости — каждая БД независима.

---

# Часть 6 — Финальная приёмка: всё собирается и работает

### Шаг 6.1 — Полная сборка в правильном порядке

```bash
cd /home/stbischof/git/daanse/org.eclipse.daanse.sql     && mvn -q clean install   # SQL сначала!
cd /home/stbischof/git/daanse/org.eclipse.daanse.jdbc.db && mvn -q clean verify
```

### Шаг 6.2 — Структурные проверки (все должны выдать «пусто»)

```bash
# В SQL-репозитории больше нет ссылок на jdbc.db:
grep -rl "org.eclipse.daanse.jdbc.db" /home/stbischof/git/daanse/org.eclipse.daanse.sql/ --include=*.java
# Сторона SQL без record/api.meta:
grep -rn "sql\.record\|api\.meta" /home/stbischof/git/daanse/org.eclipse.daanse.sql/ --include=*.java
# Pom-файлы SQL без зависимостей jdbc.db:
grep -rn "jdbc.db" /home/stbischof/git/daanse/org.eclipse.daanse.sql/ --include=pom.xml
```

### Шаг 6.3 — Важные тесты

- **SQL:** `statement/demo` (H2 + MSSQL через Testcontainers), `guard`, `deparser`, тесты версий MySQL.
- **jdbc.db:** `impl` (интроспекция + MetaInfo против новых компаньонов), `importer/csv` (ETL).

### Шаг 6.4 — Итоговый чек-лист

- [ ] Оба репозитория собираются зелёными (SQL установлен первым).
- [ ] SQL-репозиторий **не** зависит от `jdbc.db`; код диалектов SQL без `record`/`api.meta`.
- [ ] `Dialect` больше не наследует `MetadataProvider`; 6 компаньонов в `jdbc.db/dialect/metadata`.
- [ ] Классы версий MySQL (`MySql8Dialect`/`MySql57Dialect`) + офлайн-тесты зелёные.
- [ ] Интеграционные тесты `statement/demo` проходят; тесты `impl`/`importer` зелёные.
- [ ] CI лицензионных заголовков / Javadoc в обоих репозиториях зелёный.

**Когда все галочки стоят — готово.** Срез + версии диалектов внедрены, и всё работает.

---

## Приложение — Если сборка стала красной (помощь junior'у)

| Симптом | Вероятная причина | Исправление |
|---|---|---|
| `cannot find symbol: class PkBuilder` в `MySqlDialect` | помощник удалён, но ещё используется | проверь ссылки; помощник теперь в `MySqlMetadataProvider` |
| `package org.eclipse.daanse.jdbc.db.api.schema does not exist` (сторона SQL) | импорт не переименован | снова прогони замену `sed` на `sql.api.schema` |
| `record`/`api.meta` всё ещё в импортах диалекта | пропущен метод роли A | `grep` из контрольной точки 1; перенеси оставшийся метод |
| `jdbc.db` не находит `sql.api` | SQL не установлен | в SQL-репозитории `mvn -q clean install`, **потом** `jdbc.db` |
| OSGi: провайдер во время выполнения `EMPTY` | нет/неверный `@DialectName` у компаньона | проверь `@DialectName("MYSQL")` (то же значение, что у фабрики) |
| Ошибка цикла/реактора | модуль в неверном порядке/нет в `<modules>` | соблюдай порядок R→S→D; проверь `<modules>` агрегатора |
