<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 03 – Переименование пакетов и артефактов

Назад к [мастер-плану](README.md).

Все переносимые модули переименовываются с `org.eclipse.daanse.jdbc.db.*` на
`org.eclipse.daanse.sql.*` — одинаково Java-пакеты, Maven-artifactId и OSGi-`Bundle-SymbolicName`.

---

## 1. Сопоставление Java-пакетов

| Старое | Новое |
|---|---|
| `org.eclipse.daanse.jdbc.db.api` | `org.eclipse.daanse.sql.api` |
| `org.eclipse.daanse.jdbc.db.api.meta` | `org.eclipse.daanse.sql.api.meta` |
| `org.eclipse.daanse.jdbc.db.api.schema` | `org.eclipse.daanse.sql.api.schema` |
| `org.eclipse.daanse.jdbc.db.api.sql` | `org.eclipse.daanse.sql.api.sql` |
| `org.eclipse.daanse.jdbc.db.api.type` | `org.eclipse.daanse.sql.api.type` |
| `org.eclipse.daanse.jdbc.db.record.meta` | `org.eclipse.daanse.sql.record.meta` |
| `org.eclipse.daanse.jdbc.db.record.schema` | `org.eclipse.daanse.sql.record.schema` |
| `org.eclipse.daanse.jdbc.db.dialect.api` | `org.eclipse.daanse.sql.dialect.api` |
| `org.eclipse.daanse.jdbc.db.dialect.api.capability` | `org.eclipse.daanse.sql.dialect.api.capability` |
| `org.eclipse.daanse.jdbc.db.dialect.api.generator` | `org.eclipse.daanse.sql.dialect.api.generator` |
| `org.eclipse.daanse.jdbc.db.dialect.api.type` | `org.eclipse.daanse.sql.dialect.api.type` |
| `org.eclipse.daanse.jdbc.db.dialect.db.common` | `org.eclipse.daanse.sql.dialect.db.common` |
| `org.eclipse.daanse.jdbc.db.dialect.db.testsupport` | `org.eclipse.daanse.sql.dialect.db.testsupport` |
| `org.eclipse.daanse.jdbc.db.dialect.db.<db>` | `org.eclipse.daanse.sql.dialect.db.<db>` |

Правило: **префикс `org.eclipse.daanse.jdbc.db` → `org.eclipse.daanse.sql`**, суффикс без изменений.
Затрагивает объявления `package`, инструкции `import` и `package-info.java` в **каждом** переносимом
файле — включая потребителей (см. [05](05-consumer-rewiring.md)).

> Примечание: пакет `api.meta` отражает интроспекцию JDBC-`DatabaseMetaData` и потому семантически
> близок к JDBC. Ради согласованности в SQL-репозитории он всё же переименовывается в `sql.api.meta`
> (единый префикс). Более тонкое разделение возможно позже, но **не** является частью этой миграции.

---

## 2. Сопоставление Maven-artifactId

ArtifactId точно отражает корень пакета (соглашение репозитория).

| Старое | Новое |
|---|---|
| `org.eclipse.daanse.jdbc.db.api` | `org.eclipse.daanse.sql.api` |
| `org.eclipse.daanse.jdbc.db.record` | `org.eclipse.daanse.sql.record` |
| `org.eclipse.daanse.jdbc.db.dialect.api` | `org.eclipse.daanse.sql.dialect.api` |
| `org.eclipse.daanse.jdbc.db.dialect.db.common` | `org.eclipse.daanse.sql.dialect.db.common` |
| `org.eclipse.daanse.jdbc.db.dialect.db.test-support` | `org.eclipse.daanse.sql.dialect.db.test-support` |
| `org.eclipse.daanse.jdbc.db.dialect.db.<db>` | `org.eclipse.daanse.sql.dialect.db.<db>` |

Новые агрегаторы получают:
- `org.eclipse.daanse.sql.dialect` (агрегатор, заменяет `…jdbc.db.dialect`)
- `org.eclipse.daanse.sql.dialect.db` (агрегатор, заменяет `…jdbc.db.dialect.db`)

Group-Id остаётся `org.eclipse.daanse`; версия остаётся `${revision}` = `0.0.1-SNAPSHOT`.

---

## 3. OSGi / DS

- **`Bundle-SymbolicName`** выводится плагином `bnd-maven-plugin` из artifactId → меняется
  автоматически вместе с artifactId. Ручное сопровождение `bnd.bnd` не требуется (их нет).
- **DS-компоненты:** каждый диалект экспортирует `@Component(service = DialectFactory.class, …)`.
  Полностью квалифицированный тип `DialectFactory` меняется при переименовании пакета; сама аннотация
  без изменений, генерируемый `OSGI-INF/*.xml` последует автоматически при пересборке.
- **Файлов `META-INF/services` нет** — регистрацию ServiceLoader править не нужно.

---

## 4. Лицензионный заголовок и package-info

- Каждый файл несёт заголовок EPL-2.0 (участники «SmartCity Jena», «Stefan Bischof (bipolis.org)»).
  Заголовок сохраняется при переезде; год копирайта дополнять лишь при содержательном изменении.
- В каждом leaf-пакете есть `package-info.java` — имя пакета в нём тоже переименовать.
- CI требует заголовки (`.licenserc.yaml`, `license.templates`) и Javadoc
  (`java_check_javadoc.yml`) — после переименования эти проверки должны остаться зелёными.

---

## 5. Механика (рекомендуется)

1. `git mv` каталогов модулей (сохранение истории).
2. Переименование каталогов путей пакетов (`.../jdbc/db/...` → `.../sql/...`).
3. Текстовая замена `org.eclipse.daanse.jdbc.db` → `org.eclipse.daanse.sql` **только** в переносимых
   модулях и их потребителях. Затем помодульно `mvn -q -pl <module> -am verify`.
