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

import java.util.List;

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * {@code [NOT] EXISTS (subquery)}: plain, negated, correlated, and bind-parameter accumulation in
 * placeholder order across the outer statement and the subquery.
 */
class ExistsPredicateTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    /** {@code select l.order_id from line l} (optionally correlated to the outer alias). */
    private static SelectStatement lines(org.eclipse.daanse.sql.statement.api.expression.Predicate... filters) {
        SelectStatementBuilder sub = SelectStatementBuilder.create();
        TableAlias l = TableAlias.of("l");
        sub.from(From.table("line", l));
        sub.project(Expressions.column(l, "order_id"), BestFitColumnType.INT);
        for (org.eclipse.daanse.sql.statement.api.expression.Predicate f : filters) {
            sub.where(f);
        }
        return sub.build();
    }

    private static SelectStatementBuilder ordersQuery(TableAlias o) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.table("orders", o));
        q.project(Expressions.column(o, "id"), BestFitColumnType.INT);
        return q;
    }

    @Test
    void exists_plain() {
        TableAlias o = TableAlias.of("o");
        SelectStatementBuilder q = ordersQuery(o);
        q.where(Predicates.exists(lines()));
        String sql = renderer.render(q.build()).sql();
        assertEquals("""
                select "o"."id" as "c0" from "orders" as "o" \
                where exists (select "l"."order_id" as "c0" from "line" as "l")\
                """, sql);
    }

    @Test
    void notExists_negated() {
        TableAlias o = TableAlias.of("o");
        SelectStatementBuilder q = ordersQuery(o);
        q.where(Predicates.notExists(lines()));
        String sql = renderer.render(q.build()).sql();
        assertEquals("""
                select "o"."id" as "c0" from "orders" as "o" \
                where not exists (select "l"."order_id" as "c0" from "line" as "l")\
                """, sql);
    }

    @Test
    void exists_correlated_referencesOuterAlias() {
        TableAlias o = TableAlias.of("o");
        TableAlias l = TableAlias.of("l");
        SelectStatementBuilder q = ordersQuery(o);
        q.where(Predicates.exists(
                lines(Predicates.eq(Expressions.column(l, "order_id"), Expressions.column(o, "id")))));
        String sql = renderer.render(q.build()).sql();
        assertEquals("""
                select "o"."id" as "c0" from "orders" as "o" \
                where exists (select "l"."order_id" as "c0" from "line" as "l" \
                where "l"."order_id" = "o"."id")\
                """, sql);
    }

    @Test
    void exists_parametersKeepPlaceholderOrder_outerBeforeAndAfter() {
        TableAlias o = TableAlias.of("o");
        TableAlias l = TableAlias.of("l");
        SelectStatementBuilder q = ordersQuery(o);
        // outer param BEFORE the subquery, one param INSIDE it, and an outer param AFTER it
        q.where(Predicates.eq(Expressions.column(o, "region"), Expressions.param("EMEA", Datatype.VARCHAR)));
        q.where(Predicates.exists(lines(
                Predicates.eq(Expressions.column(l, "qty"), Expressions.param(5, Datatype.INTEGER)))));
        q.where(Predicates.eq(Expressions.column(o, "year"), Expressions.param(1997, Datatype.INTEGER)));
        RenderedSql sql = renderer.render(q.build());

        assertEquals("""
                select "o"."id" as "c0" from "orders" as "o" \
                where "o"."region" = ? \
                and exists (select "l"."order_id" as "c0" from "line" as "l" where "l"."qty" = ?) \
                and "o"."year" = ?\
                """, sql.sql());
        assertEquals(List.of("EMEA", 5, 1997),
                sql.parameters().stream().map(bp -> bp.value()).toList());
    }
}
