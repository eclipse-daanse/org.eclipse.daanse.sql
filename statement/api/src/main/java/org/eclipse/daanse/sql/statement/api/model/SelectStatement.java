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
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.generator.StatementHint;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;

/**
 * An immutable {@code SELECT} statement. Holds <em>structure</em> only — no
 * dialect, no pre-quoted SQL — so it has value semantics and is safe to use as
 * a cache key. Render it with a {@code SqlRenderer} to obtain dialect-specific
 * SQL.
 *
 * @param distinct       whether {@code DISTINCT} is set
 * @param projections    the {@code SELECT} list (also carries the column types,
 *                       in order)
 * @param from           the {@code FROM} clause, if any
 * @param filters        {@code WHERE} predicates, combined with {@code AND}
 * @param groupBy        the {@code GROUP BY} part (may be empty)
 * @param having         {@code HAVING} predicates, combined with {@code AND}
 * @param orderKeys      the {@code ORDER BY} items
 * @param rowLimit       the row limit/offset, if any
 * @param headerComment  an optional statement-level explanatory comment (e.g.
 *                       the originating MDX request / cube), emitted only when
 *                       the renderer is asked to emit comments; never part of
 *                       the executed SQL
 * @param footerComment  an optional trailing explanatory comment, appended on
 *                       its own line at the very end of the rendered statement
 *                       — only when the renderer is asked to emit comments;
 *                       never part of the executed SQL
 * @param statementHints statement-level optimizer hint intents, spelled (or
 *                       silently ignored) by the dialect at render time —
 *                       always emitted, they are semantic for the DBMS. A
 *                       {@code SELECT}-only concern for now; extending hints to
 *                       DML or set operations is a possible future extension
 */
public record SelectStatement(boolean distinct, List<Projection> projections, Optional<FromClause> from,
        List<Predicate> filters, GroupBy groupBy, List<Predicate> having, List<OrderKey> orderKeys,
        Optional<RowLimit> rowLimit, Optional<String> headerComment, Map<Predicate, String> filterComments,
        Optional<String> footerComment, List<StatementHint> statementHints) implements Statement {

    /**
     * Backwards-compatible form without a header comment, filter comments, a footer
     * comment or hints.
     */
    public SelectStatement(boolean distinct, List<Projection> projections, Optional<FromClause> from,
            List<Predicate> filters, GroupBy groupBy, List<Predicate> having, List<OrderKey> orderKeys,
            Optional<RowLimit> rowLimit) {
        this(distinct, projections, from, filters, groupBy, having, orderKeys, rowLimit, Optional.empty(), Map.of(),
                Optional.empty(), List.of());
    }

    /**
     * Backwards-compatible form without filter comments, a footer comment or hints.
     */
    public SelectStatement(boolean distinct, List<Projection> projections, Optional<FromClause> from,
            List<Predicate> filters, GroupBy groupBy, List<Predicate> having, List<OrderKey> orderKeys,
            Optional<RowLimit> rowLimit, Optional<String> headerComment) {
        this(distinct, projections, from, filters, groupBy, having, orderKeys, rowLimit, headerComment, Map.of(),
                Optional.empty(), List.of());
    }

    /** Backwards-compatible form without a footer comment or hints. */
    public SelectStatement(boolean distinct, List<Projection> projections, Optional<FromClause> from,
            List<Predicate> filters, GroupBy groupBy, List<Predicate> having, List<OrderKey> orderKeys,
            Optional<RowLimit> rowLimit, Optional<String> headerComment, Map<Predicate, String> filterComments) {
        this(distinct, projections, from, filters, groupBy, having, orderKeys, rowLimit, headerComment, filterComments,
                Optional.empty(), List.of());
    }

    /** Backwards-compatible form without hints. */
    public SelectStatement(boolean distinct, List<Projection> projections, Optional<FromClause> from,
            List<Predicate> filters, GroupBy groupBy, List<Predicate> having, List<OrderKey> orderKeys,
            Optional<RowLimit> rowLimit, Optional<String> headerComment, Map<Predicate, String> filterComments,
            Optional<String> footerComment) {
        this(distinct, projections, from, filters, groupBy, having, orderKeys, rowLimit, headerComment, filterComments,
                footerComment, List.of());
    }
}
