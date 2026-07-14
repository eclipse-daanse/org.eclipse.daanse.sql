<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 06 – Сборка, Java 21 и проверка

Назад к [плану минимального среза](README.md).

---

## 1. Java 21 и pom-файлы

Как в плане полного переноса ([../06-build-and-java21.md](../06-build-and-java21.md)):

- Корневой pom SQL `java.version`/`java.release` на **21**.
- Новые агрегаторы `sql/dialect/pom.xml` (packaging=pom) + `sql/dialect/db/pom.xml` с `<modules>`:
  `common test-support duckdb h2 mariadb mssqlserver mysql oracle postgresql sqlite`.
- Расширить корневой `<modules>` на `api`, `dialect` (**без** `record` — он остаётся в `jdbc.db`).
- Расширить `dependencyManagement` тест-драйверами/Testcontainers/JUnit из pom диалектов.
- Родитель `pom.parent:0.0.7`, `${revision}=0.0.1-SNAPSHOT`, снапшот-репозиторий без изменений.

**Отличие от плана полного переноса:** модуля `sql/record` **нет** — `record` остаётся целиком в
`jdbc.db`. `sql/api` содержит только `schema`+`type`+`sql`.

---

## 2. Порядок сборки

```bash
# 1) собрать + установить SQL-репозиторий (предусловие для jdbc.db)
cd org.eclipse.daanse.sql && mvn -q clean install

# 2) jdbc.db против свежих снапшотов sql.* (включая разделение api, компаньоны, impl, importer)
cd ../org.eclipse.daanse.jdbc.db && mvn -q clean verify
```

`sql` **обязан** быть установлен до `jdbc.db` (одностороннее ребро `jdbc.db → sql`).

---

## 3. Проверка

### 3.1 Фаза R (разделение ролей, только `jdbc.db`, до любого переноса)

```bash
cd org.eclipse.daanse.jdbc.db
# main диалектов свободен от record/api.meta:
grep -rE "import org.eclipse.daanse.jdbc.db.(record|api\.meta)\." \
  dialect/db/{h2,mariadb,mssqlserver,mysql,oracle,postgresql}/src/main   # → пусто
# Dialect больше не MetadataProvider:
grep -n "extends" dialect/api/src/main/java/org/eclipse/daanse/jdbc/db/dialect/api/Dialect.java  # нет MetadataProvider
mvn -q verify   # включая round-trip / MetaInfo-тесты против <Db>MetadataProvider
```

### 3.2 Структура после переноса

```bash
# SQL-репозиторий без ссылок на jdbc.db:
grep -rl "org.eclipse.daanse.jdbc.db" org.eclipse.daanse.sql/ --include=*.java   # → пусто
# сторона SQL без record/api.meta:
grep -rn "sql.record\|api\.meta" org.eclipse.daanse.sql/ --include=*.java        # → пусто
# SQL-репозиторий без внешних зависимостей jdbc.db:
grep -rn "jdbc.db" org.eclipse.daanse.sql/ --include=pom.xml                     # → пусто
# jdbc.db сохраняет record/impl/importer/dialect.metadata:
grep -nE "<module>(api|record|impl|importer|dialect)</module>" org.eclipse.daanse.jdbc.db/pom.xml
```

### 3.3 Тесты

- **SQL:** `statement/demo` (H2/MSSQL через Testcontainers), `guard/jsqltranspiler`, `deparser`,
  unit-тесты диалектов (генератор/квотер) — зелёные.
- **jdbc.db:** тесты `impl` (интроспекция + MetaInfo против компаньонов), ETL-тесты `importer/csv` —
  зелёные.

---

## 4. Риски

| Риск | Вероятность | Эффект | Мера |
|---|---|---|---|
| Слом SPI `Dialect` задевает внешних потребителей | средняя | ошибки компиляции у потребителей `dialect.getAll*` | роль A была внутренней (движок использует параметр `MetadataProvider`); предоставить компаньоны; мигрировать |
| Разрешение OSGi диалект ↔ провайдер | средняя | провайдер не найден во время выполнения | сохранить тег `@DialectName`; запасной `MetadataProvider.EMPTY` |
| Разделение `api` проводит границу неверно | низкая | цикл/ошибка компиляции | граница обоснована: `api.schema` не импортирует другие пакеты api |
| Round-trip-тесты держались на «диалект как провайдер» | средняя | ошибки тестов после разделения | перевести тесты на компаньоны ([04](04-consumer-rewiring.md) §2.1) |
| Порядок релизов | средняя | `jdbc.db` не собирается | сначала установить/опубликовать `sql` |

---

## 5. Определение готовности

**Рефакторинг (Фаза R):**
- [ ] `Dialect` без `extends MetadataProvider`; `jdbc.db` зелёный.
- [ ] 6 компаньонов `<Db>MetadataProvider` полны; main диалектов без `record`/`api.meta`.

**Разделение и перенос:**
- [ ] `sql.api` = только schema/type/sql; **нет** `sql.record`.
- [ ] SQL-репозиторий собирается без зависимостей от `jdbc.db` (Java 21); сторона SQL без
      `record`/`api.meta`.
- [ ] `jdbc.db` собирается против снапшотов `sql.*`; `record`, `api.meta`, `impl`, `importer`,
      `dialect.metadata` остаются там.
- [ ] Односторонняя связь `jdbc.db → sql`, без цикла.
- [ ] Интеграционные тесты `statement/demo` (H2/MSSQL) + тесты `impl`/`importer` зелёные.
- [ ] CI лицензионных заголовков / Javadoc в обоих репозиториях зелёный.

**Опционально (Фаза V):** см. [05](05-per-version-hardening.md).
