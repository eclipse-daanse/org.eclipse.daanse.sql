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

import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.schema.SchemaReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.SetOperation;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/** Factory methods for {@link FromClause} nodes. */
public final class From {

    private From() {
    }

    /** Builds a (optionally schema-qualified) {@link TableReference}. */
    public static TableReference tableRef(String schema, String table) {
        return schema == null ? new TableReference(table)
                : new TableReference(Optional.of(new SchemaReference(schema)), table);
    }

    public static FromClause.FromTable table(String table, TableAlias alias) {
        return table(new TableReference(table), alias);
    }

    public static FromClause.FromTable table(String schema, String table, TableAlias alias) {
        return table(tableRef(schema, table), alias);
    }

    public static FromClause.FromTable table(TableReference table, TableAlias alias) {
        return new FromClause.FromTable(table, alias, Optional.empty(), Map.of());
    }

    public static FromClause.FromTable table(String schema, String table, TableAlias alias, Predicate filter,
            Map<String, String> hints) {
        return table(tableRef(schema, table), alias, filter, hints);
    }

    public static FromClause.FromTable table(TableReference table, TableAlias alias, Predicate filter,
            Map<String, String> hints) {
        return new FromClause.FromTable(table, alias, Optional.ofNullable(filter), Map.copyOf(hints));
    }

    public static FromClause.FromSubquery subquery(SelectStatement query, TableAlias alias) {
        return new FromClause.FromSubquery(query, alias);
    }

    /**
     * A parenthesized set operation (e.g. {@code UNION}) as a derived table (see
     * {@link FromClause.FromSet}).
     */
    public static FromClause.FromSet set(SetOperation set, TableAlias alias) {
        return new FromClause.FromSet(set, alias);
    }

    /**
     * A derived table from a pre-rendered SQL fragment (view / inline
     * {@code VALUES}).
     */
    public static FromClause.FromRaw raw(String sql, TableAlias alias) {
        return new FromClause.FromRaw(sql, alias);
    }

    /**
     * A derived table from an inline {@code VALUES} table — the structured data is
     * carried as-is and the dialect-specific SQL is generated at render time (see
     * {@link FromClause.FromInline}).
     */
    public static FromClause.FromInline inline(java.util.List<String> columnNames, java.util.List<String> columnTypes,
            java.util.List<String[]> rows, TableAlias alias) {
        return new FromClause.FromInline(java.util.List.copyOf(columnNames), java.util.List.copyOf(columnTypes),
                java.util.List.copyOf(rows), alias);
    }

    /**
     * The alias of the BASE table of a FROM tree — the leftmost aliased leaf, i.e.
     * the item the renderer emits first (a join tree's leftmost input; a product's
     * first item) — or {@code null} when the tree has no aliased base
     * (empty/unsupported shapes).
     */
    public static TableAlias baseAlias(FromClause from) {
        if (from instanceof FromClause.Aliased a) {
            return a.alias();
        }
        if (from instanceof FromClause.FromJoin j) {
            return baseAlias(j.left());
        }
        if (from instanceof FromClause.FromProduct p) {
            return baseAlias(p.items().get(0));
        }
        return null;
    }

    /**
     * A copy of {@code from} whose BASE table (the leftmost
     * {@link FromClause.FromTable}) carries the given provenance comment (see
     * {@link FromClause.FromTable#comment()}). Joined tables keep their provenance
     * on the {@link FromClause.FromJoin} comment; this reaches the one item no join
     * comment can. No-op (returns {@code from} unchanged) when the base is not a
     * plain table or the comment is {@code null}/blank.
     */
    public static FromClause commentBase(FromClause from, String comment) {
        if (comment == null || comment.isBlank()) {
            return from;
        }
        if (from instanceof FromClause.FromTable t) {
            return t.withComment(comment);
        }
        if (from instanceof FromClause.FromJoin j) {
            FromClause left = commentBase(j.left(), comment);
            return left == j.left() ? from : new FromClause.FromJoin(left, j.kind(), j.right(), j.on(), j.comment());
        }
        if (from instanceof FromClause.FromProduct p) {
            FromClause first = commentBase(p.items().get(0), comment);
            if (first == p.items().get(0)) {
                return from;
            }
            java.util.List<FromClause> items = new java.util.ArrayList<>(p.items());
            items.set(0, first);
            return new FromClause.FromProduct(items);
        }
        return from;
    }

    /**
     * A comma product of two-or-more from-clauses with no join predicate of its own
     * — the caller adds the join conditions to {@code WHERE} (see
     * {@link FromClause.FromProduct}).
     */
    public static FromClause.FromProduct product(FromClause first, FromClause second, FromClause... rest) {
        java.util.List<FromClause> items = new java.util.ArrayList<>();
        items.add(first);
        items.add(second);
        java.util.Collections.addAll(items, rest);
        return new FromClause.FromProduct(items);
    }
}
