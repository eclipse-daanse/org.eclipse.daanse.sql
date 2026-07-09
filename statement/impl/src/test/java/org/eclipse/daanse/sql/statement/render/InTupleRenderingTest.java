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
package org.eclipse.daanse.sql.statement.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.jdbc.db.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.junit.jupiter.api.Test;

/**
 * Pins the two dialect shapes of {@link Predicate.InTuple} to exact SQL strings.
 *
 * The tuple is built child-first (most specific column first, e.g. {@code (quarter, the_year)}).
 * On a dialect with multi-value IN (MySQL) it renders as a row-value IN in tuple order. On a
 * dialect without (ANSI/H2) it degrades to OR-of-ANDs whose pairs read parent-first (reverse
 * tuple order), with exactly one parenthesis per AND group and no outer parenthesis of its own —
 * the enclosing connective (here the single-operand And wrap used by callers) supplies the one
 * grouping layer around the OR chain.
 */
class InTupleRenderingTest {

    private final Dialect ansi = new AnsiDialect();
    private final Dialect mysql = new MySqlDialect();

    /** Child-first tuple over time_by_day: (quarter, the_year) in (('Q1', 1997), ('Q2', 1998)). */
    private static SelectStatement timeQuery() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("time_by_day");
        q.from(From.table("time_by_day", t));
        q.project(Expressions.column(t, "the_year"), BestFitColumnType.INT);
        Predicate tupleIn = Predicates.inTuple(
                List.of(Expressions.column(t, "quarter"), Expressions.column(t, "the_year")),
                List.of(
                        List.of(Expressions.literal("Q1", Datatype.VARCHAR),
                                Expressions.literal(1997, Datatype.INTEGER)),
                        List.of(Expressions.literal("Q2", Datatype.VARCHAR),
                                Expressions.literal(1998, Datatype.INTEGER))));
        // Callers wrap the tuple IN in a single-operand And; the connective's parenthesis is the
        // one grouping layer around both dialect shapes.
        q.where(Predicates.and(List.of(tupleIn)));
        return q.build();
    }

    @Test
    void multiValueInDialect_rendersRowValueInInTupleOrder() {
        String sql = new DialectSqlRenderer(mysql).render(timeQuery()).sql();
        assertEquals("""
                select `time_by_day`.`the_year` as `c0` from `time_by_day` as `time_by_day` \
                where ((`time_by_day`.`quarter`, `time_by_day`.`the_year`) \
                in (('Q1', 1997), ('Q2', 1998)))\
                """, sql);
    }

    @Test
    void degradingDialect_rendersParentFirstOrOfAnds() {
        String sql = new DialectSqlRenderer(ansi).render(timeQuery()).sql();
        assertEquals("""
                select "time_by_day"."the_year" as "c0" from "time_by_day" as "time_by_day" \
                where (("time_by_day"."the_year" = 1997 and "time_by_day"."quarter" = 'Q1') \
                or ("time_by_day"."the_year" = 1998 and "time_by_day"."quarter" = 'Q2'))\
                """, sql);
    }

    @Test
    void degradingDialect_parentFirstAcrossStringColumns() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias p = TableAlias.of("product_class");
        q.from(From.table("product_class", p));
        q.project(Expressions.column(p, "product_family"), BestFitColumnType.STRING);
        Predicate tupleIn = Predicates.inTuple(
                List.of(Expressions.column(p, "product_department"), Expressions.column(p, "product_family")),
                List.of(
                        List.of(Expressions.literal("Alcoholic Beverages", Datatype.VARCHAR),
                                Expressions.literal("Drink", Datatype.VARCHAR)),
                        List.of(Expressions.literal("Baking Goods", Datatype.VARCHAR),
                                Expressions.literal("Food", Datatype.VARCHAR))));
        q.where(Predicates.and(List.of(tupleIn)));
        String sql = new DialectSqlRenderer(ansi).render(q.build()).sql();
        assertEquals("""
                select "product_class"."product_family" as "c0" \
                from "product_class" as "product_class" \
                where (("product_class"."product_family" = 'Drink' \
                and "product_class"."product_department" = 'Alcoholic Beverages') \
                or ("product_class"."product_family" = 'Food' \
                and "product_class"."product_department" = 'Baking Goods'))\
                """, sql);
    }

    @Test
    void degradedChainNextToSiblingConjunct_getsGuardParenthesis() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        q.from(From.table("t", t));
        q.project(Expressions.column(t, "a"), BestFitColumnType.INT);
        q.where(Predicates.eq(Expressions.column(t, "x"), Expressions.literal(1, Datatype.INTEGER)));
        q.where(Predicates.inTuple(
                List.of(Expressions.column(t, "b"), Expressions.column(t, "a")),
                List.of(
                        List.of(Expressions.literal("p", Datatype.VARCHAR),
                                Expressions.literal(2, Datatype.INTEGER)),
                        List.of(Expressions.literal("q", Datatype.VARCHAR),
                                Expressions.literal(3, Datatype.INTEGER)))));
        String sql = new DialectSqlRenderer(ansi).render(q.build()).sql();
        // The bare (unwrapped) chain sits next to an and-joined sibling: the join site must add
        // the grouping parenthesis, or the OR would capture the sibling.
        assertEquals("""
                select "t"."a" as "c0" from "t" as "t" \
                where "t"."x" = 1 \
                and (("t"."a" = 2 and "t"."b" = 'p') or ("t"."a" = 3 and "t"."b" = 'q'))\
                """, sql);
    }

    @Test
    void degradedChainAsSoleConjunct_hasNoOuterParenthesis() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        q.from(From.table("t", t));
        q.project(Expressions.column(t, "a"), BestFitColumnType.INT);
        q.where(Predicates.inTuple(
                List.of(Expressions.column(t, "b"), Expressions.column(t, "a")),
                List.of(
                        List.of(Expressions.literal("p", Datatype.VARCHAR),
                                Expressions.literal(2, Datatype.INTEGER)),
                        List.of(Expressions.literal("q", Datatype.VARCHAR),
                                Expressions.literal(3, Datatype.INTEGER)))));
        String sql = new DialectSqlRenderer(ansi).render(q.build()).sql();
        assertEquals("""
                select "t"."a" as "c0" from "t" as "t" \
                where ("t"."a" = 2 and "t"."b" = 'p') or ("t"."a" = 3 and "t"."b" = 'q')\
                """, sql);
    }
}
