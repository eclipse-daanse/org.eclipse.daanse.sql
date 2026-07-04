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

import org.eclipse.daanse.sql.statement.api.model.SelectStatement;

/**
 * A boolean condition usable in {@code WHERE}/{@code HAVING}/join-{@code ON}
 * positions.
 * <p>
 * Like {@link SqlExpression}, predicates carry structure only; the renderer
 * performs all quoting and spelling. {@link Raw} is the verbatim escape hatch.
 */
public sealed interface Predicate {

    /** {@code left <op> right}. */
    record Comparison(SqlExpression left, ComparisonOperator operator, SqlExpression right) implements Predicate {
    }

    /** {@code expression IN (values...)}. */
    record In(SqlExpression expression, List<SqlExpression> values) implements Predicate {
    }

    /**
     * Row-value / tuple {@code IN}:
     * {@code (c1, c2, ...) IN ((v11, v12, ...), (v21, v22, ...), ...)}. Each row in
     * {@code rows} must have the same arity as {@code columns}. Gate construction
     * at the call site on {@code dialect.supportsMultiValueInExpr()}; dialects
     * without it need an OR-of-ANDs expansion instead.
     */
    record InTuple(List<SqlExpression> columns, List<List<SqlExpression>> rows) implements Predicate {
    }

    /** {@code expression IS [NOT] NULL}. */
    record IsNull(SqlExpression expression, boolean negated) implements Predicate {
    }

    /** {@code expression [NOT] LIKE pattern [ESCAPE c]}. */
    record Like(SqlExpression expression, SqlExpression pattern, boolean negated, Optional<Character> escape)
            implements Predicate {
    }

    /** {@code expression [NOT] BETWEEN low AND high}. */
    record Between(SqlExpression expression, SqlExpression low, SqlExpression high, boolean negated)
            implements Predicate {
    }

    /** Negation: {@code NOT (operand)}. */
    record Not(Predicate operand) implements Predicate {
    }

    /**
     * An n-ary boolean connective over operands — {@link And} or {@link Or}. Lets
     * the renderer (and any predicate normalizer) treat both uniformly via one
     * {@code instanceof Connective}.
     */
    sealed interface Connective extends Predicate permits And, Or {
        List<Predicate> operands();
    }

    /**
     * Conjunction of operands ({@code AND}). An empty list is treated as
     * always-true.
     */
    record And(List<Predicate> operands) implements Connective {
    }

    /**
     * Disjunction of operands ({@code OR}). An empty list is treated as
     * always-false.
     */
    record Or(List<Predicate> operands) implements Connective {
    }

    /**
     * An {@code [NOT] EXISTS (subquery)} test. The subquery renders compact (like a
     * CTE body or a derived table) and may be correlated — it can reference outer
     * table aliases freely. Any bind parameters inside the subquery accumulate with
     * the enclosing statement's, in placeholder order.
     *
     * @param query   the subquery whose row existence is tested
     * @param negated {@code true} for {@code NOT EXISTS}
     */
    record Exists(SelectStatement query, boolean negated) implements Predicate {
    }

    /**
     * A constant truth value, rendered with the dialect-neutral spelling
     * {@code 1 = 1} / {@code 1 = 0} (the same spelling an empty
     * {@link And}/{@link Or} renders to), without parentheses. Useful as a neutral
     * seed when folding predicates. Obtain via {@code Predicates.alwaysTrue()} /
     * {@code Predicates.alwaysFalse()}.
     *
     * @param value the truth value
     */
    record Constant(boolean value) implements Predicate {
    }

    /** A pre-rendered predicate fragment, emitted verbatim. */
    record Raw(String sql) implements Predicate {
    }

    /**
     * A dialect-rendered regular-expression match
     * ({@code <source> MATCHES <pattern>}). The whole SQL fragment — null-guard,
     * optional case-fold, and the dialect's regex operator — is produced at render
     * time by
     * {@code dialect.regexGenerator().generateRegularExpression(renderedSource, pattern)},
     * so the node stays dialect-free (the regex spelling differs per database:
     * {@code REGEXP}, {@code RLIKE}, {@code ~}, {@code regexp_like(...)}).
     * {@code pattern} is the Java/MDX regex string. When the dialect
     * {@code requiresHavingAlias()} the renderer resolves {@code source} to the
     * matching SELECT alias.
     */
    record Regexp(SqlExpression source, String pattern, boolean negated) implements Predicate {
    }
}
