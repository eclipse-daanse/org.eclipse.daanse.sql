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

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/** Hardening tests: richer predicates, formatted output, and dialect-driven pagination. */
class RendererHardeningTest {

    private final Dialect ansi = new AnsiDialect();

    /** A query exercising LIKE (on UPPER), BETWEEN, and NOT(IS NULL). */
    private static SelectStatement predicateShowcase() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias c = TableAlias.of("c");
        q.from(From.table("customer", c));
        q.project(Expressions.column(c, "name"), BestFitColumnType.STRING);
        q.where(Predicates.like(Expressions.upper(Expressions.column(c, "name")),
                Expressions.literal("A%", Datatype.VARCHAR)));
        q.where(Predicates.between(Expressions.column(c, "age"), Expressions.literal(18, Datatype.INTEGER),
                Expressions.literal(65, Datatype.INTEGER)));
        q.where(Predicates.not(Predicates.isNull(Expressions.column(c, "name"))));
        return q.build();
    }

    @Test
    void predicates_like_between_not() {
        String sql = new DialectSqlRenderer(ansi).render(predicateShowcase()).sql();
        assertEquals("""
                select "c"."name" as "c0" from "customer" as "c" \
                where UPPER("c"."name") like 'A%' \
                and "c"."age" between 18 and 65 \
                and not ("c"."name" is null)\
                """, sql);
    }

    @Test
    void inTuple_rendersRowValueIn() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias c = TableAlias.of("c");
        q.from(From.table("customer", c));
        q.project(Expressions.column(c, "id"), BestFitColumnType.INT);
        // Tuples are built child-first: most specific column first.
        q.where(Predicates.inTuple(
                java.util.List.of(Expressions.column(c, "state"), Expressions.column(c, "country")),
                java.util.List.of(
                        java.util.List.of(Expressions.literal("CA", Datatype.VARCHAR),
                                Expressions.literal("USA", Datatype.VARCHAR)),
                        java.util.List.of(Expressions.literal("WA", Datatype.VARCHAR),
                                Expressions.literal("USA", Datatype.VARCHAR)))));
        // A dialect that supports multi-value IN (MySQL) renders the row-value IN in tuple order ...
        String mysqlSql = new DialectSqlRenderer(new MySqlDialect()).render(q.build()).sql();
        assertEquals("""
                select `c`.`id` as `c0` from `customer` as `c` \
                where (`c`.`state`, `c`.`country`) in (('CA', 'USA'), ('WA', 'USA'))\
                """, mysqlSql);
        // ... while one that does not (the ANSI default; e.g. H2 mis-unifies row-value IN types at
        // execution) degrades to the parent-first OR-of-ANDs chain. As the sole conjunct the chain
        // carries no outer parenthesis — an enclosing connective or an and-join site supplies it.
        String sql = new DialectSqlRenderer(ansi).render(q.build()).sql();
        assertEquals("""
                select "c"."id" as "c0" from "customer" as "c" \
                where ("c"."country" = 'USA' and "c"."state" = 'CA') \
                or ("c"."country" = 'USA' and "c"."state" = 'WA')\
                """, sql);
    }

    @Test
    void formatted_putsClausesOnSeparateLines() {
        String sql = new DialectSqlRenderer(ansi).render(predicateShowcase(), RenderOptions.multiLine()).sql();
        assertTrue(sql.startsWith("select\n"), sql);
        assertTrue(sql.contains("\nfrom\n"), sql);
        assertTrue(sql.contains("\"customer\" as \"c\""), sql);
        assertTrue(sql.contains("\nwhere\n"), sql);
        assertTrue(sql.contains("UPPER(\"c\".\"name\")"), sql);
    }

    @Test
    void caseAndArithmetic() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias o = TableAlias.of("o");
        q.from(From.table("orders", o));
        // CASE WHEN qty > 100 THEN 'bulk' ELSE 'normal' END
        q.project(Expressions.caseExpr(
                java.util.List.of(Expressions.when(
                        Predicates.gt(Expressions.column(o, "qty"), Expressions.literal(100, Datatype.INTEGER)),
                        Expressions.literal("bulk", Datatype.VARCHAR))),
                Expressions.literal("normal", Datatype.VARCHAR)), BestFitColumnType.STRING);
        // price * qty
        q.project(Expressions.multiply(Expressions.column(o, "price"), Expressions.column(o, "qty")),
                BestFitColumnType.DECIMAL);
        String sql = new DialectSqlRenderer(ansi).render(q.build()).sql();
        assertEquals("""
                select CASE WHEN "o"."qty" > 100 THEN 'bulk' ELSE 'normal' END as "c0", \
                ("o"."price" * "o"."qty") as "c1" from "orders" as "o"\
                """, sql);
    }

    @Test
    void pagination_isDialectDriven() {
        SelectStatement top = StatementBuilderDemo.topProducts(); // rowLimit(10)
        String ansiSql = new DialectSqlRenderer(ansi).render(top).sql();
        String mysqlSql = new DialectSqlRenderer(new MySqlDialect()).render(top).sql();
        String mssqlSql = new DialectSqlRenderer(new MicrosoftSqlServerDialect()).render(top).sql();

        assertTrue(ansiSql.endsWith(" FETCH NEXT 10 ROWS ONLY"), ansiSql);
        assertTrue(mysqlSql.endsWith(" LIMIT 10"), mysqlSql);
        assertTrue(mssqlSql.startsWith("select TOP 10 "), mssqlSql);
    }
}
