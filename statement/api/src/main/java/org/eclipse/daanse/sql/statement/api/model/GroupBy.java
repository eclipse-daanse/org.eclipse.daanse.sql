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
package org.eclipse.daanse.sql.statement.api.model;

import java.util.List;

import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

/**
 * The {@code GROUP BY} part of a select: plain grouping keys, optional grouping
 * sets, and optional {@code GROUPING(...)} super-aggregate columns.
 *
 * @param keys              the grouping keys (each either a projection handle
 *                          or an expression)
 * @param groupingSets      grouping sets (rendered only when the dialect
 *                          supports them)
 * @param groupingFunctions {@code GROUPING(expr)} columns added to the select
 *                          list
 * @param completeNonAggregates when {@code true}, dialects that do NOT allow non-aggregate
 *                          select columns outside {@code GROUP BY}
 *                          ({@code !allowsSelectNotInGroupBy()}) have every non-aggregate
 *                          projection not already a key appended to the group by at render time.
 *                          The canonical (permissive-dialect) form groups only the keys the
 *                          builder placed; the renderer completes it per dialect.
 */
public record GroupBy(List<GroupKey> keys, List<GroupingSet> groupingSets, List<GroupingFunction> groupingFunctions,
        boolean completeNonAggregates) {

    /** Backward-compatible: no dialect-completed grouping. */
    public GroupBy(List<GroupKey> keys, List<GroupingSet> groupingSets, List<GroupingFunction> groupingFunctions) {
        this(keys, groupingSets, groupingFunctions, false);
    }

    /** True if there is nothing to group by. */
    public boolean isEmpty() {
        return keys.isEmpty() && groupingSets.isEmpty() && groupingFunctions.isEmpty();
    }

    /**
     * A grouping key: either a reference to a projection, or a standalone
     * expression.
     */
    public sealed interface GroupKey {

        /**
         * Group by a projection (the renderer decides alias-vs-expression per dialect).
         */
        record Ref(ProjectionRef projection) implements GroupKey {
        }

        /** Group by a standalone expression. */
        record Expr(SqlExpression expression) implements GroupKey {
        }
    }

    /** One grouping set: a list of grouping-key expressions. */
    public record GroupingSet(List<SqlExpression> keys) {
    }

    /** A {@code GROUPING(expr)} super-aggregate column. */
    public record GroupingFunction(SqlExpression argument) {
    }
}
