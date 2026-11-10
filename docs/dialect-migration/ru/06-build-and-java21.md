<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 06 – Сборка, реактор и повышение до Java 21

Назад к [мастер-плану](README.md).

---

## 1. Повышение версии Java (17 → 21)

SQL-репозиторий сегодня собирается с Java 17 (`sql/pom.xml`), а код диалектов происходит из репозитория
на Java 21. В корневом pom SQL:

```xml
<properties>
  ...
  <java.version>21</java.version>
  <java.release>21</java.release>
  <maven.compiler.source>${java.version}</maven.compiler.source>
  <maven.compiler.target>${java.version}</maven.compiler.target>
</properties>
```

Эффект: весь SQL-репозиторий компилируется/тестируется на 21. Toolchain/CI должны предоставлять JDK 21
(проверить `.github/workflows/build_deploy.yml`; проверку `java_check_javadoc.yml` также на 21).

---

## 2. Новые pom-агрегаторы

**`sql/dialect/pom.xml`** (заменяет `jdbc.db/dialect/pom.xml`):

```xml
<parent>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.sql</artifactId>
  <version>${revision}</version>
</parent>
<artifactId>org.eclipse.daanse.sql.dialect</artifactId>
<packaging>pom</packaging>
<modules>
  <module>api</module>
  <module>db</module>
</modules>
```

**`sql/dialect/db/pom.xml`** (packaging=pom, родитель `org.eclipse.daanse.sql.dialect`), `<modules>`:

```
common  test-support  duckdb  h2  mariadb  mssqlserver  mysql  oracle  postgresql  sqlite
```

**Корень `sql/pom.xml`** расширить `<modules>`:

```xml
<modules>
  <module>api</module>
  <module>record</module>
  <module>dialect</module>
  <module>guard</module>
  <module>deparser</module>
  <module>statement</module>
</modules>
```

Порядок в `<modules>` некритичен (Maven сортирует по зависимостям), но для читаемости — фундамент
первым.

---

## 3. Расширение dependencyManagement

Перенести необходимые версии из мигрируемых модулей в корневой `dependencyManagement` SQL или в
соответствующие pom модулей (сегодня в корне SQL есть только slf4j/mockito/assertj):

- **Тест-драйверы и контейнеры** (test-scope, пер-диалектно): `mysql-connector-j`,
  `mariadb-java-client`, `mssql-jdbc`, `ojdbc11`, `postgresql`, `org.xerial:sqlite-jdbc`,
  `org.duckdb:duckdb_jdbc`, `com.h2database:h2`; `org.testcontainers` (mysql, mariadb, mssqlserver,
  oracle-xe, postgresql, junit-jupiter).
- **JUnit 5** (`junit-jupiter-api/engine`) — версии из исходных pom (напр., 5.12.2) унифицировать.
- **assertj** — в корне SQL 3.24.2, часть модулей jdbc.db использует 3.26.0 → унифицировать до одной
  версии.
- **`biz.aQute.bnd:biz.aQute.bndlib`** — compile-зависимость `oracle`.

Точные версии брать из соответствующих `dialect/db/<db>/pom.xml` источника; при сомнении выбирать
более новую и централизованно фиксировать в `dependencyManagement`.

---

## 4. Родительский pom и снапшот-репозиторий (без изменений)

- Родитель остаётся `org.eclipse.daanse:org.eclipse.daanse.pom.parent:0.0.7` (даёт плагины bnd/flatten).
- `${revision}` = `0.0.1-SNAPSHOT`, механика `.flattened-pom.xml` без изменений.
- Снапшот-репозиторий Sonatype `central.sonatype.com/repository/maven-snapshots` без изменений.
- OSGi: файлов `bnd.bnd` нет; управляется DS-`@Component` → `Bundle-SymbolicName` следует за
  artifactId автоматически.

---

## 5. Команды сборки

```bash
# Полная сборка SQL-репозитория + локальная установка (предусловие для jdbc.db)
cd org.eclipse.daanse.sql && mvn -q clean install

# Отдельный модуль вместе с зависимостями
mvn -q -pl org.eclipse.daanse.sql.dialect.db.mysql -am verify

# jdbc.db против свежеустановленных снапшотов sql.*
cd ../org.eclipse.daanse.jdbc.db && mvn -q clean verify
```
