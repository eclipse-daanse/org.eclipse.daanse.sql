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
package org.eclipse.daanse.sql.statement.api.expression;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.dialect.api.generator.BitOperation;
import org.eclipse.daanse.jdbc.db.dialect.api.sql.OrderedColumn;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;

/**
 * A scalar SQL expression usable in {@code SELECT}, {@code WHERE},
 * {@code GROUP BY} and {@code ORDER BY} positions.
 * <p>
 * The model is intentionally dialect-free: it stores <em>structure</em> only.
 * All quoting and spelling happens later, in the renderer. The {@link Raw}
 * variant is an escape hatch for SQL fragments that are already final (e.g.
 * opaque view SQL) and is rendered verbatim.
 */
public sealed interface SqlExpression {

    /**
     * A function or aggregate invocation — a name plus argument expressions. Shared
     * by {@link Function} and {@link Aggregate} (which additionally carries a
     * {@code distinct} flag); lets a caller match any call with one
     * {@code instanceof Call} (e.g. "find every {@code COUNT(...)}").
     */
    sealed interface Call extends SqlExpression permits Function, Aggregate {
        String name();

        List<SqlExpression> arguments();
    }

    /**
     * A column reference, optionally qualified by a table alias.
     *
     * @param tableQualifier the table alias (its raw name), or empty for an
     *                       unqualified column
     * @param name           the column name (unquoted; the renderer quotes it)
     */
    record Column(Optional<String> tableQualifier, String name) implements SqlExpression {
    }

    /**
     * A typed literal value. The {@link Datatype} tells the renderer how to quote
     * it.
     *
     * @param value    the raw value (may be {@code null})
     * @param datatype the value's SQL datatype
     */
    record Literal(Object value, Datatype datatype) implements SqlExpression {
    }

    /**
     * A function or operator call, e.g. {@code COUNT(...)} or {@code UPPER(...)}.
     *
     * @param name      the function name (rendered verbatim, upper-case by
     *                  convention)
     * @param arguments the argument expressions
     */
    record Function(String name, List<SqlExpression> arguments) implements Call {
        public Function {
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * An aggregate-function call that may carry a {@code DISTINCT} qualifier, e.g.
     * {@code COUNT(DISTINCT a, b)} or {@code SUM(amount)}. Distinguished from
     * {@link Function} because the renderer must place {@code DISTINCT}
     * <em>inside</em> the parentheses, and because a multi-argument
     * {@code COUNT(DISTINCT ...)} (compound count-distinct) is its own
     * dialect-gated shape.
     *
     * @param name      the aggregate name (rendered verbatim, upper-case by
     *                  convention)
     * @param distinct  whether the {@code DISTINCT} qualifier precedes the
     *                  arguments
     * @param arguments the argument expressions (at least one)
     */
    record Aggregate(String name, boolean distinct, List<SqlExpression> arguments) implements Call {
        public Aggregate {
            if (arguments == null || arguments.isEmpty()) {
                throw new IllegalArgumentException("Aggregate requires at least one argument");
            }
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * A portable well-known function call, identified by
     * {@link org.eclipse.daanse.jdbc.db.dialect.api.generator.KnownFunction KnownFunction}
     * <em>intent</em> rather than a verbatim function name — the dialect's
     * {@code FunctionGenerator} chooses the spelling at render time (e.g.
     * {@code LENGTH} renders as {@code CHAR_LENGTH(x)} on ANSI but {@code LEN(x)}
     * on SQL Server).
     * <p>
     * Deliberately NOT a {@link Call}: {@code Call.name()} promises a verbatim name
     * that is rendered as-is, but a {@code KnownCall} has no name until render
     * time.
     *
     * @param function  the portable function intent
     * @param arguments the argument expressions (arity is validated by the
     *                  renderer's dialect generator; the {@code Expressions}
     *                  factories validate it eagerly)
     */
    record KnownCall(org.eclipse.daanse.jdbc.db.dialect.api.generator.KnownFunction function,
            List<SqlExpression> arguments) implements SqlExpression {
        public KnownCall {
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * A binary arithmetic expression, e.g. {@code price * quantity}.
     *
     * @param left          left operand
     * @param operator      the arithmetic operator
     * @param right         right operand
     * @param parenthesized whether the rendered expression is wrapped in
     *                      parentheses; {@code true} for a standalone binary (the
     *                      safe default), {@code false} for an infix fragment that
     *                      is already inside an enclosing context (e.g.
     *                      {@code sum(a / b)}) and must not gain an extra paren
     *                      pair
     */
    record Binary(SqlExpression left, ArithmeticOperator operator, SqlExpression right, boolean parenthesized)
            implements SqlExpression {
        /** Backward-compatible constructor: parenthesized by default. */
        public Binary(SqlExpression left, ArithmeticOperator operator, SqlExpression right) {
            this(left, operator, right, true);
        }
    }

    /**
     * A searched {@code CASE WHEN condition THEN result ... [ELSE result] END}
     * expression.
     *
     * @param whens      the ordered WHEN/THEN branches (at least one)
     * @param elseResult the ELSE result, if any
     */
    record Case(List<WhenClause> whens, Optional<SqlExpression> elseResult) implements SqlExpression {

        /** One {@code WHEN condition THEN result} branch. */
        public record WhenClause(Predicate condition, SqlExpression result) {
        }
    }

    /**
     * A bind parameter. Renders as a dialect placeholder ({@code ?}, {@code $n}, …)
     * and its value is carried alongside the SQL for the executor to bind —
     * avoiding literal string interpolation.
     *
     * @param value    the value to bind when {@code bound} is true (may be
     *                 {@code null})
     * @param bound    {@code true} for an immediate value; {@code false} for a
     *                 positional marker whose value is supplied later (e.g. per
     *                 batch row)
     * @param datatype the parameter's SQL datatype
     */
    record Param(Object value, boolean bound, Datatype datatype) implements SqlExpression {
    }

    /**
     * A scalar subquery: a parenthesized {@code SELECT} used where a single value
     * is expected (a projection, or one side of a comparison). The subquery renders
     * compact (like a CTE body or a derived table); any bind parameters inside
     * accumulate with the enclosing statement's, in placeholder order.
     *
     * @param query the subquery producing the scalar value
     */
    record ScalarSubquery(SelectStatement query) implements SqlExpression {
    }

    /**
     * The whole-row projection {@code *} or {@code alias.*}. Never receives a
     * column alias — the renderer suppresses the generated {@code as cN} for it
     * ({@code select * as c0} is invalid SQL).
     *
     * @param tableQualifier the table alias to qualify with ({@code t.*}), or empty
     *                       for bare {@code *}
     */
    record Star(Optional<String> tableQualifier) implements SqlExpression {
    }

    /**
     * A 1-based ordinal position reference, e.g. the {@code 1} in
     * {@code ORDER BY 1}. Rendered as the bare number.
     *
     * @param position the position (1-based, as in SQL)
     */
    record Ordinal(int position) implements SqlExpression {
        public Ordinal {
            if (position < 1) {
                throw new IllegalArgumentException("Ordinal position is 1-based (SQL), got " + position);
            }
        }
    }

    /**
     * A pre-rendered SQL fragment, emitted verbatim by the renderer.
     *
     * @param sql the literal SQL text
     */
    record Raw(String sql) implements SqlExpression {
    }

    /**
     * The multi-variant sibling of {@link Raw}: a pre-rendered SQL fragment chosen
     * per dialect at render time. Carries the whole {@code dialect-name -> SQL} map
     * (author-written per-engine fragments — e.g. a computed column / measure
     * expression with several {@code <SQL dialect="..."/>} entries) instead of a
     * single already-picked string, so the expression stays dialect-free and the
     * {@code Statement} stays cache-safe. The renderer resolves the map
     * ({@code dialect.name()} else {@code "generic"}) and emits it verbatim like
     * {@link Raw}.
     *
     * @param byDialectName the {@code dialect-name -> SQL} variants (must contain
     *                      the live dialect or {@code "generic"})
     */
    record RawVariant(java.util.Map<String, String> byDialectName) implements SqlExpression {
    }

    /**
     * A dialect-generated extra aggregate (PERCENTILE / LISTAGG / bitwise /
     * NTH_VALUE) whose SQL is produced at RENDER time by
     * {@code dialect.aggregationGenerator()} — each engine spells these very
     * differently, so (like
     * {@link org.eclipse.daanse.sql.statement.api.model.FromClause.FromInline}) the
     * node carries only the dialect-free parameters and the renderer dispatches on
     * {@link Spec}.
     *
     * @param operand the aggregated operand expression when the kind uses one
     *                (LISTAGG / bitwise / NTH_VALUE); empty for PERCENTILE, which
     *                takes its ordered column directly
     * @param spec    the per-kind dialect-free parameters
     */
    record ExtraAggregate(Optional<SqlExpression> operand, Spec spec) implements SqlExpression {

        /** The per-kind parameters of an {@link ExtraAggregate}. */
        public sealed interface Spec {

            /**
             * {@code PERCENTILE_DISC|CONT(fraction) WITHIN GROUP (ORDER BY column [DESC])}.
             */
            record Percentile(double fraction, boolean continuous, boolean descending, String tableName,
                    String columnName) implements Spec {
            }

            /**
             * {@code LISTAGG([DISTINCT] operand, separator) WITHIN GROUP (ORDER BY columns)}.
             */
            record ListAgg(boolean distinct, String separator, String coalesce, String onOverflowTruncate,
                    List<OrderedColumn> columns) implements Spec {
            }

            /**
             * A bitwise aggregation (AND/OR/XOR, optionally negated) of {@code operand}.
             */
            record BitAggregation(BitOperation operation) implements Spec {
            }

            /** {@code NTH_VALUE(operand, n) [IGNORE NULLS] OVER (ORDER BY columns)}. */
            record NthValue(boolean ignoreNulls, int n, List<OrderedColumn> columns) implements Spec {
            }
        }
    }

    /**
     * A dialect-specific case fold (upper-casing) of an expression, e.g.
     * {@code UPPER(x)} / {@code UCASE(x)}. Rendered at render time via
     * {@code dialect.functionGenerator().wrapIntoSqlUpperCaseFunction(...)} — each
     * engine spells the function differently, so (like {@link ExtraAggregate}) the
     * node stays dialect-free and the renderer supplies the spelling. Used for
     * case-insensitive name-column comparisons.
     *
     * @param inner the expression to upper-case
     */
    record CaseFold(SqlExpression inner) implements SqlExpression {
    }
}
