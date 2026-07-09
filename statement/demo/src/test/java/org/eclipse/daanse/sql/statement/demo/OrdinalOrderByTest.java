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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.NullOrder;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Ordinal {@code ORDER BY} positions ({@code order by 1, 2}): rendered as the bare 1-based number,
 * passing unchanged through the dialect's null-ordering machinery.
 */
class OrdinalOrderByTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    private static SelectStatementBuilder twoColumns() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        q.from(From.table("fact", t));
        q.project(Expressions.column(t, "name"), BestFitColumnType.STRING);
        q.project(Expressions.column(t, "amount"), BestFitColumnType.DECIMAL);
        return q;
    }

    @Test
    void orderByOrdinals_ascAndDesc() {
        SelectStatementBuilder q = twoColumns();
        q.orderOn(Expressions.ordinal(1), SortSpec.asc());
        q.orderOn(Expressions.ordinal(2), SortSpec.desc());
        assertEquals("""
                select "t"."name" as "c0", "t"."amount" as "c1" from "fact" as "t" \
                order by 1 ASC, 2 DESC\
                """, renderer.render(q.build()).sql());
    }

    /**
     * A requested null order is dropped on an ordinal: the dialect's CASE emulation would test the
     * literal {@code 1} rather than the column it designates. Order on the expression instead.
     */
    @Test
    void orderByOrdinal_nullOrderDropped() {
        SelectStatementBuilder q = twoColumns();
        q.orderOn(Expressions.ordinal(1), SortSpec.asc().withNulls(NullOrder.LAST));
        String sql = renderer.render(q.build()).sql();
        assertTrue(sql.endsWith(" order by 1 ASC"), sql);
    }

    @Test
    void ordinal_isOneBased() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> Expressions.ordinal(0));
        assertTrue(e.getMessage().contains("1-based"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new SqlExpression.Ordinal(-3));
    }
}
