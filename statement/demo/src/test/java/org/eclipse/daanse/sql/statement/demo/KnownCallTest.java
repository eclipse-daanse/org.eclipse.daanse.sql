/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.statement.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.generator.KnownFunction;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.jdbc.db.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.jdbc.db.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * {@code SqlExpression.KnownCall}: a portable well-known function <em>intent</em> whose spelling the
 * dialect's {@code FunctionGenerator} chooses at render time. Exercises the OData {@code $filter}
 * builtin set (substring, length, concat, indexof, trim, year..second, round, floor, ceiling)
 * against the ANSI, MySQL and SQL Server dialects, plus the arity checks of the
 * {@code Expressions} factories.
 */
class KnownCallTest {

    private final DialectSqlRenderer ansi = new DialectSqlRenderer(new AnsiDialect());
    private final DialectSqlRenderer mysql = new DialectSqlRenderer(new MySqlDialect());
    private final DialectSqlRenderer mssql = new DialectSqlRenderer(new MicrosoftSqlServerDialect());

    private static final SqlExpression X = Expressions.raw("x");
    private static final SqlExpression A = Expressions.raw("a");
    private static final SqlExpression B = Expressions.raw("b");
    private static final SqlExpression TWO = Expressions.raw("2");
    private static final SqlExpression THREE = Expressions.raw("3");

    // ---- string builtins ---------------------------------------------------------

    @Test
    void substring_perDialect() {
        SqlExpression two = Expressions.substring(X, TWO);
        SqlExpression three = Expressions.substring(X, TWO, THREE);
        assertEquals("SUBSTRING(x, 2)", ansi.renderExpression(two));
        assertEquals("SUBSTRING(x, 2, 3)", ansi.renderExpression(three));
        assertEquals("SUBSTRING(x, 2)", mysql.renderExpression(two));
        assertEquals("SUBSTRING(x, 2, 3)", mssql.renderExpression(three));
    }

    @Test
    void length_perDialect() {
        SqlExpression e = Expressions.length(X);
        assertEquals("CHAR_LENGTH(x)", ansi.renderExpression(e));
        // MySQL's LENGTH() counts bytes; CHAR_LENGTH stays.
        assertEquals("CHAR_LENGTH(x)", mysql.renderExpression(e));
        assertEquals("LEN(x)", mssql.renderExpression(e));
    }

    @Test
    void concat_perDialect() {
        SqlExpression e = Expressions.concat(A, B, X);
        assertEquals("(a || b || x)", ansi.renderExpression(e));
        assertEquals("CONCAT(a, b, x)", mysql.renderExpression(e));
        assertEquals("CONCAT(a, b, x)", mssql.renderExpression(e));
    }

    @Test
    void indexOf_needleFirst_perDialect() {
        // indexOf(needle, haystack) — needle first, like the OData indexof builtin.
        SqlExpression e = Expressions.indexOf(A, B);
        assertEquals("POSITION(a IN b)", ansi.renderExpression(e));
        assertEquals("LOCATE(a, b)", mysql.renderExpression(e));
        assertEquals("CHARINDEX(a, b)", mssql.renderExpression(e));
    }

    @Test
    void trim_family_perDialect() {
        assertEquals("TRIM(x)", ansi.renderExpression(Expressions.trim(X)));
        assertEquals("TRIM(LEADING FROM x)", ansi.renderExpression(Expressions.ltrim(X)));
        assertEquals("TRIM(TRAILING FROM x)", ansi.renderExpression(Expressions.rtrim(X)));
        assertEquals("TRIM(x)", mysql.renderExpression(Expressions.trim(X)));
        assertEquals("TRIM(x)", mssql.renderExpression(Expressions.trim(X)));
    }

    // ---- date/time builtins ------------------------------------------------------

    @Test
    void dateParts_perDialect() {
        assertEquals("EXTRACT(YEAR FROM x)", ansi.renderExpression(Expressions.year(X)));
        assertEquals("EXTRACT(MONTH FROM x)", ansi.renderExpression(Expressions.month(X)));
        assertEquals("EXTRACT(DAY FROM x)", ansi.renderExpression(Expressions.day(X)));
        assertEquals("EXTRACT(HOUR FROM x)", ansi.renderExpression(Expressions.hour(X)));
        assertEquals("EXTRACT(MINUTE FROM x)", ansi.renderExpression(Expressions.minute(X)));
        assertEquals("EXTRACT(SECOND FROM x)", ansi.renderExpression(Expressions.second(X)));

        assertEquals("EXTRACT(YEAR FROM x)", mysql.renderExpression(Expressions.year(X)));
        assertEquals("EXTRACT(SECOND FROM x)", mysql.renderExpression(Expressions.second(X)));

        // T-SQL has no EXTRACT; the whole family renders via DATEPART.
        assertEquals("DATEPART(year, x)", mssql.renderExpression(Expressions.year(X)));
        assertEquals("DATEPART(month, x)", mssql.renderExpression(Expressions.month(X)));
        assertEquals("DATEPART(day, x)", mssql.renderExpression(Expressions.day(X)));
        assertEquals("DATEPART(hour, x)", mssql.renderExpression(Expressions.hour(X)));
        assertEquals("DATEPART(minute, x)", mssql.renderExpression(Expressions.minute(X)));
        assertEquals("DATEPART(second, x)", mssql.renderExpression(Expressions.second(X)));
    }

    @Test
    void dateAndTime_castSpelling() {
        assertEquals("CAST(x AS DATE)", ansi.renderExpression(Expressions.date(X)));
        assertEquals("CAST(x AS TIME)", ansi.renderExpression(Expressions.time(X)));
        assertEquals("CAST(x AS DATE)", mssql.renderExpression(Expressions.date(X)));
    }

    @Test
    void now_perDialect() {
        assertEquals("CURRENT_TIMESTAMP", ansi.renderExpression(Expressions.now()));
        assertEquals("CURRENT_TIMESTAMP", mysql.renderExpression(Expressions.now()));
        assertEquals("GETDATE()", mssql.renderExpression(Expressions.now()));
    }

    // ---- math builtins -----------------------------------------------------------

    @Test
    void round_floor_ceiling_perDialect() {
        assertEquals("ROUND(x)", ansi.renderExpression(Expressions.round(X)));
        assertEquals("ROUND(x, 2)", ansi.renderExpression(Expressions.round(X, TWO)));
        assertEquals("FLOOR(x)", ansi.renderExpression(Expressions.floor(X)));
        assertEquals("CEILING(x)", ansi.renderExpression(Expressions.ceiling(X)));
        assertEquals("ROUND(x)", mysql.renderExpression(Expressions.round(X)));
        assertEquals("CEILING(x)", mysql.renderExpression(Expressions.ceiling(X)));
        assertEquals("ROUND(x, 2)", mssql.renderExpression(Expressions.round(X, TWO)));
        assertEquals("FLOOR(x)", mssql.renderExpression(Expressions.floor(X)));
        assertEquals("CEILING(x)", mssql.renderExpression(Expressions.ceiling(X)));
    }

    @Test
    void abs_mod_power_sqrt_defaultSpellings() {
        assertEquals("ABS(x)", ansi.renderExpression(Expressions.abs(X)));
        assertEquals("MOD(a, b)", ansi.renderExpression(Expressions.mod(A, B)));
        assertEquals("POWER(a, b)", ansi.renderExpression(Expressions.power(A, B)));
        assertEquals("SQRT(x)", ansi.renderExpression(Expressions.sqrt(X)));
    }

    // ---- nested arguments render through renderExpression ------------------------

    @Test
    void arguments_areRenderedExpressions_notRawText() {
        SqlExpression e = Expressions.length(Expressions.trim(Expressions.column("name")));
        assertEquals("CHAR_LENGTH(TRIM(\"name\"))", ansi.renderExpression(e));
        assertEquals("LEN(TRIM(\"name\"))", mssql.renderExpression(e));
    }

    // ---- factory arity checks ----------------------------------------------------

    @Test
    void concat_factoryRequiresAtLeastTwoArguments() {
        assertThrows(IllegalArgumentException.class, () -> Expressions.concat(A));
        assertThrows(IllegalArgumentException.class, () -> Expressions.concat());
    }

    @Test
    void handBuiltKnownCall_arityFailsAtRender() {
        // The factories are arity-safe by signature; a hand-built node still fails in the
        // dialect generator's arity check at render time.
        SqlExpression tooMany = new SqlExpression.KnownCall(KnownFunction.LENGTH, List.of(A, B));
        assertThrows(IllegalArgumentException.class, () -> ansi.renderExpression(tooMany));
    }

    @Test
    void knownCall_copiesItsArgumentList() {
        java.util.List<SqlExpression> args = new java.util.ArrayList<>(List.of(A));
        SqlExpression.KnownCall call = new SqlExpression.KnownCall(KnownFunction.ABS, args);
        args.add(B);
        assertEquals(1, call.arguments().size());
    }
}
