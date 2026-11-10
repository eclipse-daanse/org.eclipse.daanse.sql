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

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@code Aggregate(name, distinct, args)} expression node: plain aggregate, single
 * {@code DISTINCT}, and compound {@code COUNT(DISTINCT a, b)} — the shapes ROLAP's measure and
 * statistics queries need.
 */
class AggregateTest {

    private final Dialect ansi = new AnsiDialect();

    @Test
    void plainAggregateAndGroupBy() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias f = TableAlias.of("f");
        q.from(From.table("sales", f));
        ProjectionRef region = q.project(Expressions.column(f, "region"), BestFitColumnType.STRING);
        q.groupOn(region);
        q.project(Expressions.aggregate("SUM", Expressions.column(f, "amount")), BestFitColumnType.DECIMAL);
        String sql = new DialectSqlRenderer(ansi).render(q.build()).sql();
        assertEquals("""
                select "f"."region" as "c0", SUM("f"."amount") as "c1" \
                from "sales" as "f" group by "f"."region"\
                """, sql);
    }

    @Test
    void singleColumnCountDistinct() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias f = TableAlias.of("f");
        q.from(From.table("sales", f));
        q.project(Expressions.countDistinct(Expressions.column(f, "customer_id")), BestFitColumnType.INT);
        String sql = new DialectSqlRenderer(ansi).render(q.build()).sql();
        assertEquals("""
                select COUNT(distinct "f"."customer_id") as "c0" from "sales" as "f"\
                """, sql);
    }

    @Test
    void compoundCountDistinct() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias f = TableAlias.of("f");
        q.from(From.table("sales", f));
        q.project(Expressions.countDistinct(Expressions.column(f, "a"), Expressions.column(f, "b")),
                BestFitColumnType.INT);
        String sql = new DialectSqlRenderer(ansi).render(q.build()).sql();
        assertEquals("""
                select COUNT(distinct "f"."a", "f"."b") as "c0" from "sales" as "f"\
                """, sql);
    }
}
