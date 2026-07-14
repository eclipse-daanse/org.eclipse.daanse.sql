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

import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;

/**
 * The {@code FROM} clause as a tree of table references, sub-queries and joins.
 * <p>
 * Whether a {@link FromJoin} renders as an ANSI {@code JOIN ... ON} or as an
 * old-style comma join with the condition pushed into {@code WHERE} is decided
 * by the renderer from the dialect's capabilities — there is a single
 * representation here.
 */
public sealed interface FromClause {

    /**
     * A FROM item that carries a single query-local table {@link TableAlias} — a
     * leaf table, sub-query, or pre-rendered/variant fragment — as opposed to
     * {@link FromJoin}/{@link FromProduct}, which compose other items and have no
     * single alias of their own. Lets callers resolve a from-item's alias with one
     * {@code instanceof Aliased} rather than enumerating every leaf variant.
     */
    sealed interface Aliased extends FromClause
            permits FromTable, FromSubquery, FromSet, FromRaw, FromVariant, FromInline {
        TableAlias alias();
    }

    /**
     * A base table reference.
     *
     * @param table   the (optionally schema-qualified) table, as the shared sql.model
     *                identifier
     * @param alias   the query-local table alias
     * @param filter  an optional per-table filter to add to {@code WHERE}
     * @param hints   optimizer hints (dialect-specific; may be empty)
     * @param comment an optional explanatory comment (rollup provenance — why this
     *                table anchors the FROM, e.g. the level relation or the fact
     *                table), emitted only when the renderer is asked to emit
     *                comments; never part of the executed SQL. The {@link FromJoin}
     *                comment covers joined tables; this slot covers the BASE table,
     *                which no join comment can reach.
     */
    record FromTable(TableReference table, TableAlias alias, Optional<Predicate> filter, Map<String, String> hints,
            Optional<String> comment) implements Aliased {

        /** Backwards-compatible form without a comment. */
        public FromTable(TableReference table, TableAlias alias, Optional<Predicate> filter,
                Map<String, String> hints) {
            this(table, alias, filter, hints, Optional.empty());
        }

        /** A copy of this table reference carrying the given provenance comment. */
        public FromTable withComment(String comment) {
            return new FromTable(table, alias, filter, hints, Optional.ofNullable(comment));
        }
    }

    /**
     * A derived table (sub-query) reference.
     *
     * @param query the sub-query
     * @param alias the derived-table alias
     */
    record FromSubquery(SelectStatement query, TableAlias alias) implements Aliased {
    }

    /**
     * A parenthesized set operation (e.g. {@code UNION}) as a derived table — the
     * {@link SetOperation} sibling of {@link FromSubquery}, rendered as
     * {@code (input1 union input2 ...) as alias}.
     *
     * @param set   the set operation forming the derived-table body
     * @param alias the derived-table alias
     */
    record FromSet(SetOperation set, TableAlias alias) implements Aliased {
    }

    /**
     * A derived table from a pre-rendered, dialect-specific SQL fragment (e.g. a
     * mapping view's chosen SQL, or an inline {@code VALUES} table). Unlike
     * {@link FromSubquery} the body is not a structured {@link SelectStatement} —
     * it is the SQL the source already produces for the target dialect, wrapped as
     * {@code (sql) as alias}.
     *
     * @param sql   the derived-table body SQL (without surrounding parentheses)
     * @param alias the derived-table alias
     */
    record FromRaw(String sql, TableAlias alias) implements Aliased {
    }

    /**
     * The multi-variant sibling of {@link FromRaw}: a derived table whose body SQL
     * is chosen per dialect at render time. Carries the whole
     * {@code dialect-name -> pre-rendered SQL} map (author-written per-engine
     * fragments — e.g. a mapping view with several {@code <SQL dialect="..."/>}
     * entries) instead of a single already-picked string, so the {@code Statement}
     * no longer encodes a dialect choice and stays cache-safe. The renderer
     * resolves the map ({@code dialect.name()} else {@code "generic"}) and renders
     * it exactly like {@link FromRaw}.
     *
     * @param byDialectName the {@code dialect-name -> SQL} variants (must contain
     *                      the live dialect or {@code "generic"})
     * @param alias         the derived-table alias
     */
    record FromVariant(Map<String, String> byDialectName, TableAlias alias) implements Aliased {
    }

    /**
     * A derived table from an inline {@code VALUES} table: the structured data
     * (column names, column type names, and rows of raw string values) carried
     * as-is, with the dialect-specific {@code VALUES} / {@code SELECT … UNION ALL}
     * SQL generated at RENDER time by
     * {@code dialect.sqlGenerator().generateInline(...)}. Unlike
     * {@link FromVariant} (a schema-carried per-dialect map), inline-table SQL is
     * dialect-GENERATED, so the single dialect touch is deferred to the renderer
     * rather than resolved while building.
     *
     * @param columnNames the inline column names, in order
     * @param columnTypes the inline column type names (matching
     *                    {@code columnNames})
     * @param rows        the rows of raw string cell values (each {@code String[]}
     *                    matching {@code columnNames})
     * @param alias       the derived-table alias
     */
    record FromInline(List<String> columnNames, List<String> columnTypes, List<String[]> rows, TableAlias alias)
            implements Aliased {
    }

    /**
     * A join of two from-clauses.
     *
     * @param left    the left input
     * @param kind    the join kind
     * @param right   the right input
     * @param on      the join condition (ignored for {@link JoinKind#CROSS})
     * @param comment an optional explanatory comment (rollup provenance — why this
     *                join exists), emitted only when the renderer is asked to emit
     *                comments; never part of the executed SQL
     */
    record FromJoin(FromClause left, JoinKind kind, FromClause right, Predicate on, Optional<String> comment)
            implements FromClause {

        /** Backwards-compatible form without a comment. */
        public FromJoin(FromClause left, JoinKind kind, FromClause right, Predicate on) {
            this(left, kind, right, on, Optional.empty());
        }
    }

    /**
     * A comma-separated product of from-clauses with <em>no</em> join predicate of
     * its own — the caller is responsible for adding the join conditions to
     * {@code WHERE} (via the builder's {@code where(...)}). This lets a mapper
     * reproduce the exact {@code WHERE}-conjunct order of a legacy query (where
     * join and constraint predicates were interleaved by call order), which a
     * {@link FromJoin}'s deferred predicate-push cannot.
     *
     * @param items the comma-joined inputs (at least two)
     */
    record FromProduct(List<FromClause> items) implements FromClause {
        public FromProduct {
            if (items == null || items.size() < 2) {
                throw new IllegalArgumentException("FromProduct requires at least two items");
            }
            items = List.copyOf(items);
        }
    }
}
