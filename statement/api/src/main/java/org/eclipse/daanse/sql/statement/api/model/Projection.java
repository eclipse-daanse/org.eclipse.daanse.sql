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

import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

/**
 * One item of the {@code SELECT} list: an expression, the JDBC column type to
 * read it back as, and an optional explicit alias.
 * <p>
 * The projection carries its own column type, so the rendered SQL and the
 * column-type list stay in lock-step by construction (no separate parallel type
 * list).
 *
 * @param expression the projected expression
 * @param columnType the type to read the result column as (may be {@code null}
 *                   if unknown)
 * @param alias      an explicit column alias, or empty to let the renderer
 *                   decide
 * @param comment    an optional explanatory comment (rollup provenance),
 *                   emitted only when the renderer is asked to emit comments;
 *                   never part of the executed SQL
 * @param groupByCompletionExempt when {@code true}, the dialect GROUP-BY
 *                   completion (which adds every non-aggregate projection to
 *                   GROUP BY for engines that require it) skips this projection.
 *                   Set for a projection that is semantically an aggregate the
 *                   renderer cannot recognise structurally — e.g. an arithmetic
 *                   expression WRAPPING aggregates (a native TopCount/Order
 *                   measure like {@code (sum(a)-sum(b))/sum(b)}), which must not
 *                   be grouped. Never affects permissive dialects.
 */
public record Projection(SqlExpression expression, BestFitColumnType columnType, Optional<ColumnAlias> alias,
        Optional<String> comment, boolean groupByCompletionExempt) {

    /** Backwards-compatible form without the completion-exempt flag (defaults to {@code false}). */
    public Projection(SqlExpression expression, BestFitColumnType columnType, Optional<ColumnAlias> alias,
            Optional<String> comment) {
        this(expression, columnType, alias, comment, false);
    }

    /** Backwards-compatible form without a comment. */
    public Projection(SqlExpression expression, BestFitColumnType columnType, Optional<ColumnAlias> alias) {
        this(expression, columnType, alias, Optional.empty(), false);
    }
}
