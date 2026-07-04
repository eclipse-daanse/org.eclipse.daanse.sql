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

import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;

/** Factory methods for {@link Predicate}s. */
public final class Predicates {

    private static final Predicate ALWAYS_TRUE = new Predicate.Constant(true);
    private static final Predicate ALWAYS_FALSE = new Predicate.Constant(false);

    private Predicates() {
    }

    public static Predicate comparison(SqlExpression left, ComparisonOperator operator, SqlExpression right) {
        return new Predicate.Comparison(left, operator, right);
    }

    public static Predicate eq(SqlExpression left, SqlExpression right) {
        return comparison(left, ComparisonOperator.EQ, right);
    }

    public static Predicate gt(SqlExpression left, SqlExpression right) {
        return comparison(left, ComparisonOperator.GT, right);
    }

    public static Predicate in(SqlExpression expression, List<SqlExpression> values) {
        return new Predicate.In(expression, List.copyOf(values));
    }

    public static Predicate in(SqlExpression expression, SqlExpression... values) {
        return new Predicate.In(expression, List.of(values));
    }

    /**
     * Row-value / tuple {@code IN}:
     * {@code (c1, c2, ...) IN ((v11, ...), (v21, ...), ...)}. Every row must match
     * {@code columns} in arity. Gate at the call site on
     * {@code dialect.supportsMultiValueInExpr()}.
     */
    public static Predicate inTuple(List<SqlExpression> columns, List<List<SqlExpression>> rows) {
        List<List<SqlExpression>> copy = new java.util.ArrayList<>(rows.size());
        for (List<SqlExpression> row : rows) {
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException(
                        "inTuple row arity " + row.size() + " != column count " + columns.size());
            }
            copy.add(List.copyOf(row));
        }
        return new Predicate.InTuple(List.copyOf(columns), List.copyOf(copy));
    }

    public static Predicate isNull(SqlExpression expression) {
        return new Predicate.IsNull(expression, false);
    }

    public static Predicate isNotNull(SqlExpression expression) {
        return new Predicate.IsNull(expression, true);
    }

    public static Predicate like(SqlExpression expression, SqlExpression pattern) {
        return new Predicate.Like(expression, pattern, false, Optional.empty());
    }

    public static Predicate like(SqlExpression expression, SqlExpression pattern, char escape) {
        return new Predicate.Like(expression, pattern, false, Optional.of(escape));
    }

    public static Predicate notLike(SqlExpression expression, SqlExpression pattern) {
        return new Predicate.Like(expression, pattern, true, Optional.empty());
    }

    public static Predicate between(SqlExpression expression, SqlExpression low, SqlExpression high) {
        return new Predicate.Between(expression, low, high, false);
    }

    public static Predicate notBetween(SqlExpression expression, SqlExpression low, SqlExpression high) {
        return new Predicate.Between(expression, low, high, true);
    }

    public static Predicate not(Predicate operand) {
        return new Predicate.Not(operand);
    }

    public static Predicate and(List<Predicate> operands) {
        return new Predicate.And(List.copyOf(operands));
    }

    public static Predicate and(Predicate... operands) {
        return new Predicate.And(List.of(operands));
    }

    public static Predicate or(List<Predicate> operands) {
        return new Predicate.Or(List.copyOf(operands));
    }

    public static Predicate or(Predicate... operands) {
        return new Predicate.Or(List.of(operands));
    }

    /**
     * {@code EXISTS (query)}. The subquery may be correlated (reference outer
     * aliases).
     */
    public static Predicate exists(SelectStatement query) {
        return new Predicate.Exists(query, false);
    }

    /**
     * {@code NOT EXISTS (query)}. The subquery may be correlated (reference outer
     * aliases).
     */
    public static Predicate notExists(SelectStatement query) {
        return new Predicate.Exists(query, true);
    }

    /**
     * The always-true constant predicate, rendered {@code 1 = 1} (no parentheses).
     */
    public static Predicate alwaysTrue() {
        return ALWAYS_TRUE;
    }

    /**
     * The always-false constant predicate, rendered {@code 1 = 0} (no parentheses).
     */
    public static Predicate alwaysFalse() {
        return ALWAYS_FALSE;
    }

    public static Predicate raw(String sql) {
        return new Predicate.Raw(sql);
    }
}
