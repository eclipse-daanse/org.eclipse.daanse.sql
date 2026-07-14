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
package org.eclipse.daanse.sql.statement.api;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/**
 * Factory methods for {@link SqlExpression}s (house style: static helpers over
 * values).
 */
public final class Expressions {

    private Expressions() {
    }

    /** An unqualified column reference. */
    public static SqlExpression column(String name) {
        return new SqlExpression.Column(Optional.empty(), name);
    }

    /** A column reference qualified by a table alias. */
    public static SqlExpression column(TableAlias table, String name) {
        return new SqlExpression.Column(Optional.of(table.name()), name);
    }

    /**
     * A column from a sql.model
     * {@link org.eclipse.daanse.sql.model.schema.ColumnReference}, qualified by
     * the given query alias. Only the column's {@code name()} is used; the
     * reference's own table is metadata and is <em>not</em> the in-query qualifier.
     */
    public static SqlExpression column(TableAlias table, org.eclipse.daanse.sql.model.schema.ColumnReference column) {
        return new SqlExpression.Column(Optional.of(table.name()), column.name());
    }

    /**
     * An unqualified column from a sql.model
     * {@link org.eclipse.daanse.sql.model.schema.ColumnReference} (uses
     * {@code name()} only).
     */
    public static SqlExpression column(org.eclipse.daanse.sql.model.schema.ColumnReference column) {
        return new SqlExpression.Column(Optional.empty(), column.name());
    }

    /** A typed literal value. */
    public static SqlExpression literal(Object value, Datatype datatype) {
        return new SqlExpression.Literal(value, datatype);
    }

    /** A verbatim SQL fragment. */
    public static SqlExpression raw(String sql) {
        return new SqlExpression.Raw(sql);
    }

    /**
     * A per-dialect SQL fragment chosen at render time — the multi-variant sibling
     * of {@link #raw(String)} for computed columns / measure expressions whose SQL
     * differs per engine. The renderer resolves the {@code dialect-name -> SQL} map
     * (live dialect else {@code "generic"}).
     */
    public static SqlExpression rawVariant(java.util.Map<String, String> byDialectName) {
        return new SqlExpression.RawVariant(byDialectName);
    }

    /**
     * A bind parameter carrying an immediate value (rendered as a placeholder,
     * bound by the executor).
     */
    public static SqlExpression param(Object value, Datatype datatype) {
        return new SqlExpression.Param(value, true, datatype);
    }

    /**
     * A positional parameter marker whose value is supplied at execute time (e.g.
     * per batch row).
     */
    public static SqlExpression paramMarker(Datatype datatype) {
        return new SqlExpression.Param(null, false, datatype);
    }

    /** A function call, e.g. {@code function("UPPER", column("name"))}. */
    public static SqlExpression function(String name, SqlExpression... arguments) {
        return new SqlExpression.Function(name, List.of(arguments));
    }

    /**
     * The whole-row projection {@code *} (never given a column alias by the
     * renderer).
     */
    public static SqlExpression star() {
        return new SqlExpression.Star(Optional.empty());
    }

    /**
     * The qualified whole-row projection {@code t.*} (never given a column alias by
     * the renderer).
     */
    public static SqlExpression star(TableAlias table) {
        return new SqlExpression.Star(Optional.of(table.name()));
    }

    /**
     * A scalar subquery {@code (select ...)} usable as a value — in the
     * {@code SELECT} list or as one side of a comparison. Rendered compact; nested
     * bind parameters keep placeholder order.
     */
    public static SqlExpression scalarSubquery(SelectStatement query) {
        return new SqlExpression.ScalarSubquery(query);
    }

    /**
     * A 1-based ordinal position reference, e.g. {@code ordinal(1)} for
     * {@code ORDER BY 1}.
     */
    public static SqlExpression ordinal(int position) {
        return new SqlExpression.Ordinal(position);
    }

    /** The {@code COUNT(*)} aggregate. */
    public static SqlExpression countStar() {
        return new SqlExpression.Function("COUNT", List.of(new SqlExpression.Star(Optional.empty())));
    }

    /**
     * A non-distinct aggregate call, e.g.
     * {@code aggregate("SUM", column("amount"))}.
     */
    public static SqlExpression aggregate(String name, SqlExpression... arguments) {
        requireArgs(arguments, "aggregate");
        return new SqlExpression.Aggregate(name, false, List.of(arguments));
    }

    /**
     * A {@code DISTINCT} aggregate call, e.g. {@code SUM(DISTINCT amount)} or
     * {@code COUNT(DISTINCT a, b)} (the multi-argument form is compound
     * count-distinct, which not every dialect supports — gate at the call site on
     * {@code allowsCompoundCountDistinct}).
     */
    public static SqlExpression aggregateDistinct(String name, SqlExpression... arguments) {
        requireArgs(arguments, "aggregateDistinct");
        return new SqlExpression.Aggregate(name, true, List.of(arguments));
    }

    /** {@code COUNT(DISTINCT arg0, arg1, ...)}. */
    public static SqlExpression countDistinct(SqlExpression... arguments) {
        requireArgs(arguments, "countDistinct");
        return new SqlExpression.Aggregate("COUNT", true, List.of(arguments));
    }

    private static void requireArgs(SqlExpression[] arguments, String method) {
        if (arguments == null || arguments.length == 0) {
            throw new IllegalArgumentException(method + " requires at least one argument");
        }
    }

    /** {@code UPPER(expression)}. */
    public static SqlExpression upper(SqlExpression expression) {
        return new SqlExpression.Function("UPPER", List.of(expression));
    }

    /** {@code LOWER(expression)}. */
    public static SqlExpression lower(SqlExpression expression) {
        return new SqlExpression.Function("LOWER", List.of(expression));
    }

    /** {@code COALESCE(arg0, arg1, ...)}. */
    public static SqlExpression coalesce(SqlExpression... arguments) {
        return new SqlExpression.Function("COALESCE", List.of(arguments));
    }

    /** A binary arithmetic expression {@code left <op> right}. */
    public static SqlExpression arithmetic(SqlExpression left, ArithmeticOperator operator, SqlExpression right) {
        return new SqlExpression.Binary(left, operator, right);
    }

    /**
     * A binary arithmetic expression that renders WITHOUT enclosing parentheses —
     * for infix fragments already inside an enclosing context (e.g. the
     * {@code a / b} inside {@code sum(a / b)}), where the standalone
     * {@link #arithmetic} form's outer parens would be incorrect.
     */
    public static SqlExpression infix(SqlExpression left, ArithmeticOperator operator, SqlExpression right) {
        return new SqlExpression.Binary(left, operator, right, false);
    }

    public static SqlExpression add(SqlExpression left, SqlExpression right) {
        return arithmetic(left, ArithmeticOperator.ADD, right);
    }

    public static SqlExpression subtract(SqlExpression left, SqlExpression right) {
        return arithmetic(left, ArithmeticOperator.SUBTRACT, right);
    }

    public static SqlExpression multiply(SqlExpression left, SqlExpression right) {
        return arithmetic(left, ArithmeticOperator.MULTIPLY, right);
    }

    public static SqlExpression divide(SqlExpression left, SqlExpression right) {
        return arithmetic(left, ArithmeticOperator.DIVIDE, right);
    }

    public static SqlExpression modulo(SqlExpression left, SqlExpression right) {
        return arithmetic(left, ArithmeticOperator.MODULO, right);
    }

    // ---- portable well-known functions (KnownCall: the dialect spells them at
    // render time) ----

    /**
     * Substring from a 1-based start position to the end:
     * {@code SUBSTRING(e, start)}.
     */
    public static SqlExpression substring(SqlExpression expression, SqlExpression start) {
        return new SqlExpression.KnownCall(KnownFunction.SUBSTRING, List.of(expression, start));
    }

    /**
     * Substring of {@code length} characters from a 1-based start position:
     * {@code SUBSTRING(e, start, length)}.
     */
    public static SqlExpression substring(SqlExpression expression, SqlExpression start, SqlExpression length) {
        return new SqlExpression.KnownCall(KnownFunction.SUBSTRING, List.of(expression, start, length));
    }

    /** Character length of a string. */
    public static SqlExpression length(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.LENGTH, List.of(expression));
    }

    /** String concatenation of two or more expressions. */
    public static SqlExpression concat(SqlExpression... arguments) {
        if (arguments == null || arguments.length < 2) {
            throw new IllegalArgumentException("concat requires at least two arguments");
        }
        return new SqlExpression.KnownCall(KnownFunction.CONCAT, List.of(arguments));
    }

    /**
     * 1-based position of {@code needle} inside {@code haystack} — <em>needle
     * first</em>, matching the OData {@code indexof} builtin; dialects whose native
     * function takes the haystack first (Oracle {@code INSTR}) swap the arguments
     * at render time.
     */
    public static SqlExpression indexOf(SqlExpression needle, SqlExpression haystack) {
        return new SqlExpression.KnownCall(KnownFunction.INDEX_OF, List.of(needle, haystack));
    }

    /** Strip leading and trailing whitespace. */
    public static SqlExpression trim(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.TRIM, List.of(expression));
    }

    /** Strip leading whitespace. */
    public static SqlExpression ltrim(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.LTRIM, List.of(expression));
    }

    /** Strip trailing whitespace. */
    public static SqlExpression rtrim(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.RTRIM, List.of(expression));
    }

    /** Year part of a date/time value. */
    public static SqlExpression year(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.YEAR, List.of(expression));
    }

    /** Month part of a date/time value. */
    public static SqlExpression month(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.MONTH, List.of(expression));
    }

    /** Day-of-month part of a date/time value. */
    public static SqlExpression day(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.DAY, List.of(expression));
    }

    /** Hour part of a time value. */
    public static SqlExpression hour(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.HOUR, List.of(expression));
    }

    /** Minute part of a time value. */
    public static SqlExpression minute(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.MINUTE, List.of(expression));
    }

    /** Second part of a time value. */
    public static SqlExpression second(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.SECOND, List.of(expression));
    }

    /** Date part of a date/time value. */
    public static SqlExpression date(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.DATE, List.of(expression));
    }

    /** Time part of a date/time value. */
    public static SqlExpression time(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.TIME, List.of(expression));
    }

    /** Numeric rounding to an integer: {@code ROUND(e)}. */
    public static SqlExpression round(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.ROUND, List.of(expression));
    }

    /**
     * Numeric rounding to {@code digits} decimal places: {@code ROUND(e, digits)}.
     */
    public static SqlExpression round(SqlExpression expression, SqlExpression digits) {
        return new SqlExpression.KnownCall(KnownFunction.ROUND, List.of(expression, digits));
    }

    /** Largest integer not greater than the value. */
    public static SqlExpression floor(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.FLOOR, List.of(expression));
    }

    /** Smallest integer not less than the value. */
    public static SqlExpression ceiling(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.CEILING, List.of(expression));
    }

    /** Absolute value. */
    public static SqlExpression abs(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.ABS, List.of(expression));
    }

    /** Remainder of an integer division: {@code MOD(dividend, divisor)}. */
    public static SqlExpression mod(SqlExpression dividend, SqlExpression divisor) {
        return new SqlExpression.KnownCall(KnownFunction.MOD, List.of(dividend, divisor));
    }

    /** Exponentiation: {@code POWER(base, exponent)}. */
    public static SqlExpression power(SqlExpression base, SqlExpression exponent) {
        return new SqlExpression.KnownCall(KnownFunction.POWER, List.of(base, exponent));
    }

    /** Square root. */
    public static SqlExpression sqrt(SqlExpression expression) {
        return new SqlExpression.KnownCall(KnownFunction.SQRT, List.of(expression));
    }

    /** Current date and time. */
    public static SqlExpression now() {
        return new SqlExpression.KnownCall(KnownFunction.NOW, List.of());
    }

    /** A single {@code WHEN condition THEN result} branch for {@link #caseExpr}. */
    public static SqlExpression.Case.WhenClause when(Predicate condition, SqlExpression result) {
        return new SqlExpression.Case.WhenClause(condition, result);
    }

    /** A {@code CASE WHEN ... THEN ... END} with no ELSE. */
    public static SqlExpression caseExpr(List<SqlExpression.Case.WhenClause> whens) {
        return new SqlExpression.Case(List.copyOf(whens), Optional.empty());
    }

    /** A {@code CASE WHEN ... THEN ... ELSE elseResult END}. */
    public static SqlExpression caseExpr(List<SqlExpression.Case.WhenClause> whens, SqlExpression elseResult) {
        return new SqlExpression.Case(List.copyOf(whens), Optional.ofNullable(elseResult));
    }
}
