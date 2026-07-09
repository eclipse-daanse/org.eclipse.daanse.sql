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
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Scalar subqueries {@code (select ...)}: in the {@code SELECT} list, as a comparison operand, and
 * bind-parameter accumulation in placeholder order.
 */
class ScalarSubqueryTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    /** {@code select AVG(t.total) from orders t [where t.year = ?param]}. */
    private static SelectStatement avgTotal(Object param) {
        SelectStatementBuilder sub = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        sub.from(From.table("orders", t));
        sub.project(Expressions.aggregate("AVG", Expressions.column(t, "total")), BestFitColumnType.DECIMAL);
        if (param != null) {
            sub.where(Predicates.eq(Expressions.column(t, "year"), Expressions.param(param, Datatype.INTEGER)));
        }
        return sub.build();
    }

    @Test
    void scalarSubquery_inSelectList_isParenthesizedAndAliased() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias o = TableAlias.of("o");
        q.from(From.table("orders", o));
        q.project(Expressions.column(o, "id"), BestFitColumnType.INT);
        q.project(Expressions.scalarSubquery(avgTotal(null)), BestFitColumnType.DECIMAL);
        String sql = renderer.render(q.build()).sql();
        assertEquals("""
                select "o"."id" as "c0", \
                (select AVG("t"."total") as "c0" from "orders" as "t") as "c1" \
                from "orders" as "o"\
                """, sql);
    }

    @Test
    void scalarSubquery_asComparisonRightSide() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias o = TableAlias.of("o");
        q.from(From.table("orders", o));
        q.project(Expressions.column(o, "id"), BestFitColumnType.INT);
        q.where(Predicates.comparison(Expressions.column(o, "total"), ComparisonOperator.GT,
                Expressions.scalarSubquery(avgTotal(null))));
        String sql = renderer.render(q.build()).sql();
        assertEquals("""
                select "o"."id" as "c0" from "orders" as "o" \
                where "o"."total" > (select AVG("t"."total") as "c0" from "orders" as "t")\
                """, sql);
    }

    @Test
    void scalarSubquery_parametersKeepPlaceholderOrder() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias o = TableAlias.of("o");
        q.from(From.table("orders", o));
        q.project(Expressions.column(o, "id"), BestFitColumnType.INT);
        // subquery param renders first (projection list precedes WHERE), then the outer WHERE param
        q.project(Expressions.scalarSubquery(avgTotal(1997)), BestFitColumnType.DECIMAL);
        q.where(Predicates.eq(Expressions.column(o, "region"), Expressions.param("EMEA", Datatype.VARCHAR)));
        RenderedSql sql = renderer.render(q.build());

        assertEquals("""
                select "o"."id" as "c0", \
                (select AVG("t"."total") as "c0" from "orders" as "t" where "t"."year" = ?) \
                as "c1" from "orders" as "o" where "o"."region" = ?\
                """, sql.sql());
        assertEquals(List.of(1997, "EMEA"),
                sql.parameters().stream().map(bp -> bp.value()).toList());
    }
}
