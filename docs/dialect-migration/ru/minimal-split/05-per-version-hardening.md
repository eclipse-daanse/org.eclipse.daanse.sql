<!--
Copyright (c) 2026 Contributors to the Eclipse Foundation.
This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0/
SPDX-License-Identifier: EPL-2.0
-->

# 05 – Усиление по версиям (опционально, вариант O2)

Назад к [плану минимального среза](README.md).

**Опционально.** Эта фаза **не требуется** для минимального среза — после разделения ролей
([01](01-role-separation.md)) диалект уже свободен от `record`/`api.meta`. Она последовательно
воплощает исходную идею «**встроить диалекты статически по версиям, без JDBC-record для установки
свойств**» и дополнительно развязывает диалект от любого получения метаданных во время выполнения.

---

## 1. Исходное состояние (обосновано)

Генерация SQL сегодня читает только константы + один `int` `dialectVersion` (из `DialectInitData`).
Уже существуют два паттерна версий:

- **Подкласс на вариант:** `Db2OldAs400Dialect extends Db2Dialect` (переопределяет
  `allowsFromQuery()→false`), собственный `@DialectName("DB2_OLD_AS400")`.
- **Внутри метода:** `dialectVersion.isUnknownOrAtLeast(8,0)` (mysql/oracle/postgresql/mssql/mariadb/sqlite).

`DialectInitData` кроме версии несёт ещё выводимые из метаданных поля (`readOnly`,
`supportedResultSetStyles`, `sqlKeywordsLower`, `maxColumnNameLength`), которые жёстко заданный
вариант мог бы предоставить как константы.

---

## 2. Цель

Вместо чтения `DialectInitData.fromConnection(...)` во время выполнения — фиксированные классы по
версиям с **вкомпилированными** свойствами; `DialectFactory` выбирает нужный класс только по имени
продукта + версии; доступ к `DatabaseMetaData` **больше не нужен**.

```
MySqlDialect (база, общая генерация SQL)
 ├─ MySql57Dialect   → dialectVersion=(5,7), возможности/ключевые слова как константы
 └─ MySql8Dialect    → dialectVersion=(8,0), рекурсивные CTE, Percentile, …
```

---

## 3. Шаги (на каждую БД, пример MySQL)

1. Выявить внутриметодные ветвления (`isUnknownOrAtLeast(8,0)`) и перенести их в **фиксированные
   переопределения** по классу версии (например, `cteGenerator()` в `MySql8Dialect` сразу возвращает
   рекурсивный вариант).
2. Поля, выводимые из метаданных, внести константами в класс версии (символ кавычки,
   `maxColumnNameLength`, при необходимости набор ключевых слов).
3. `MySqlDialectFactory.createDialect(DialectInitData init)` выбирает по `init.version()` /
   `init.productVersion()` конкретный класс (запасной — новейшая известная версия при UNKNOWN).
4. `DialectInitData` остаётся носителем имени продукта/версии, но может создаваться полностью
   **офлайн** (`ansiDefaults().withVersion(8,0).withQuoteIdentifierString("`")`).

---

## 4. Компромисс

| Плюс | Минус |
|---|---|
| Диалект полностью статичен, без `DatabaseMetaData` во время выполнения | Взрыв числа классов (несколько версий на БД) |
| Тестируем без живой БД (фиксированные варианты) | Логика выбора версии должна быть надёжной |
| Поведение по версии явно видно | Поддержка при новых версиях БД |

**Рекомендация по порядку:** сначала завершить разделение ролей ([01](01-role-separation.md)) +
перенос (уже даёт минимальное ядро). Усиление по версиям — потом, **инкрементально по каждой БД**; оно
независимо и не является предпосылкой разделения.

---

## 5. Приёмка (опционально)

- [ ] По каждой усиленной БД: классы для конкретных версий с константными свойствами; нет ветвления
      `isUnknownOrAtLeast` в горячем пути.
- [ ] `DialectFactory` выбирает корректно по имени продукта/версии; UNKNOWN → определённый запасной.
- [ ] Unit-тесты диалектов проходят **без** живой БД (только `ansiDefaults().withVersion(...)`).
