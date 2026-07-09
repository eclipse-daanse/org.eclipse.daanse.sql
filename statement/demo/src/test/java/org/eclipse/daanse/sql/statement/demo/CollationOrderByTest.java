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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.NullOrder;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * {@code ORDER BY expr COLLATE name}: the collation binds to the key expression (before the
 * dialect's direction/null-order machinery) and the name is emitted verbatim.
 */
class CollationOrderByTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    private static SelectStatementBuilder oneColumn() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        q.from(From.table("fact", t));
        q.project(Expressions.column(t, "name"), BestFitColumnType.STRING);
        return q;
    }

    @Test
    void orderBy_withCollation() {
        SelectStatementBuilder q = oneColumn();
        TableAlias t = TableAlias.of("t");
        q.orderOn(Expressions.column(t, "name"), SortSpec.asc().withCollation("utf8mb4_bin"));
        assertEquals("""
                select "t"."name" as "c0" from "fact" as "t" \
                order by "t"."name" collate utf8mb4_bin ASC\
                """, renderer.render(q.build()).sql());
    }

    /** Collation composes with null-ordering: it wraps the expression, not the direction. */
    @Test
    void orderBy_collationWithNullOrder() {
        SelectStatementBuilder q = oneColumn();
        TableAlias t = TableAlias.of("t");
        q.orderOn(Expressions.column(t, "name"),
                SortSpec.desc().withNulls(NullOrder.LAST).withCollation("Latin1_General_CS_AS"));
        String sql = renderer.render(q.build()).sql();
        assertTrue(sql.contains("""
                "t"."name" collate Latin1_General_CS_AS\
                """), sql);
        assertTrue(sql.endsWith("DESC"), sql);
    }

    /** Without a collation the output is unchanged (compat constructors default to empty). */
    @Test
    void orderBy_noCollation_unchanged() {
        SelectStatementBuilder q = oneColumn();
        TableAlias t = TableAlias.of("t");
        q.orderOn(Expressions.column(t, "name"), SortSpec.asc());
        assertEquals("""
                select "t"."name" as "c0" from "fact" as "t" \
                order by "t"."name" ASC\
                """, renderer.render(q.build()).sql());
    }
}
