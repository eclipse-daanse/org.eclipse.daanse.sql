<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 07 – Аномалии и риски

Назад к [мастер-плану](README.md).

---

## 1. Путаница redshift/snowflake (⚠ целостность данных)

Два Maven-модуля `dialect/db/redshift` и `dialect/db/snowflake` заполнены **перекрёстно**
(проверено чтением объявлений `package`):

| Каталог модуля / ArtifactId | Содержащийся пакет + класс |
|---|---|
| `dialect/db/redshift` (`…dialect.db.redshift`) | `…dialect.db.snowflake` · `SnowflakeDialect` / `SnowflakeDialectFactory` |
| `dialect/db/snowflake` (`…dialect.db.snowflake`) | `…dialect.db.redshift` · `RedshiftDialect` / `RedshiftDialectFactory` |

- Оба модуля **неактивны** и **не** входят в текущее активное подмножество → сейчас действий **не**
  требуется.
- **При последующем переносе обязательно исправить:** согласовать либо каталог/artifactId, либо
  пакет/класс так, чтобы код Snowflake был в модуле `snowflake`, а код Redshift — в модуле `redshift`.
  Чисто механический перенос по имени каталога закрепил бы неверное именование.
- Учесть дополнительное ребро: модуль `snowflake` (с кодом Redshift) зависит от `postgresql`.

---

## 2. Java 17 → 21

- Код диалектов написан под Java 21; SQL-репозиторий повышается (см. [06](06-build-and-java21.md)).
  Риск невелик, но CI/toolchain должны предоставлять JDK 21.
- Все существующие модули SQL (`guard`/`deparser`/`statement`) будут собираться на 21 —
  известных препятствий нет, но регрессионный тест входит в приёмку.

---

## 3. Кросс-репо тестовая зависимость `dialect.db.<db> → jdbc.db.impl`

Round-trip-тесты некоторых диалектов (mysql, mariadb, mssqlserver, oracle, postgresql, derby)
используют `DatabaseServiceImpl` из `impl`. Поскольку `impl` остаётся в `jdbc.db`, это ребро
**не** должно сохраниться как зависимость `sql → jdbc.db` (реактивировало бы устранённый цикл).
Варианты (фиксировать по каждому диалекту в [08](08-verification.md)):

1. **Переписать** на собственные тест-фикстуры SQL-репозитория (предпочтительно, где посильно).
2. **Временно `@Disabled`** и продолжить интеграционные тесты в `jdbc.db` (при `impl`).

---

## 4. Неактивные диалекты (поздняя волна)

~27 модулей + `configurable` лежат закомментированными в `jdbc.db/dialect/db/`. Пока остаются там.
Риски при переносе: путаница redshift/snowflake (§1) и меж-диалектные цепочки
(`greenplum/netezza → postgresql`, `impala → hive`, `vectorwise → ingres`, `sqlstream → luciddb`,
`infobright → mysql`) — учитывать порядок.

---

## 5. Сохранение истории (опционально)

`git mv` сохраняет историю внутри одного репозитория. Для **полной** файловой истории через границу
репозиториев (jdbc.db → sql) потребовался бы `git filter-repo` с последующим слиянием — существенно
трудозатратнее. Рекомендация: `git mv` достаточно; происхождение прослеживается через этот набор
документов.

---

## 6. Именование `api.meta`

`api.meta` (интроспекция JDBC-`DatabaseMetaData`) семантически близка к JDBC, но ради согласованности
переименовывается в `sql.api.meta`. Если позже понадобится более чистое разделение «чистая модель
SQL» vs «интроспекция JDBC», `api.meta` можно выделить отдельным шагом — **не** часть этой миграции.

---

## 7. Матрица рисков (кратко)

| Риск | Вероятность | Эффект | Мера |
|---|---|---|---|
| redshift/snowflake неверно названы | позже | неверный выбор диалекта в рантайме | §1, исправить при переносе |
| цикл реактивирован через тест-ребро | средняя | сборка/релиз заблокированы | §3, переписать/отключить тесты |
| нет JDK-21 toolchain в CI | низкая | сборка красная | перевести CI-workflow на 21 |
| расхождение версий (assertj/junit) | низкая | предупреждения/ошибки сборки | централизованно зафиксировать ([06](06-build-and-java21.md)) |
| перепутан порядок релизов | средняя | jdbc.db не собирается | сначала установить/опубликовать `sql` |
