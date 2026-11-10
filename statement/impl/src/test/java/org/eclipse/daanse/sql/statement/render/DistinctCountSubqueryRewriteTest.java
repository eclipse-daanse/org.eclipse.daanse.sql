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

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.ColumnAlias;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.junit.jupiter.api.Test;

/**
 * Pins the count-distinct SUBQUERY rewrite the {@link DialectSqlRenderer} performs (P2b — relocated
 * from the core {@code AbstractQuerySpec.distinctGenerateSql}). The builder always emits the
 * canonical flat {@code count(distinct col)}; a dialect that cannot execute it inline
 * ({@code !allowsCountDistinct}, or {@code !allowsMultipleCountDistinct} for two or more) has it
 * degraded into the nested {@code dummyname} subquery form. Because the degraded branch never fires
 * on any corpus dialect, these pins ARE the contract; the byte-for-byte equivalence with the legacy
 * recorder output is proved separately by the rolap-side oracle test.
 */
class DistinctCountSubqueryRewriteTest {

    private static final String FROM =
            """
                     from "fact" as "fact" join "dim" as "dim" on "fact"."dim_id" = "dim"."id"\
                    """;

    /** A dialect without count-distinct support (else identical to ANSI). */
    private static Dialect noCountDistinct() {
        return new AnsiDialect() {
            @Override
            public boolean allowsCountDistinct() {
                return false;
            }
        };
    }

    /** As {@link #noCountDistinct()} but also forbidding DISTINCT in the inner subquery (Greenplum). */
    private static Dialect noCountDistinctNoInnerDistinct() {
        return new AnsiDialect() {
            @Override
            public boolean allowsCountDistinct() {
                return false;
            }

            @Override
            public boolean allowsInnerDistinct() {
                return false;
            }
        };
    }

    /** A flat aggregate: one grouped dim column + one {@code count(distinct measCol)} measure (m0). */
    private static SelectStatement flatDistinctCount() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias fact = TableAlias.of("fact");
        TableAlias dim = TableAlias.of("dim");
        q.from(From.table("fact", fact));
        q.innerJoin(From.table("dim", dim), org.eclipse.daanse.sql.statement.api.Predicates.comparison(
                Expressions.column(fact, "dim_id"),
                org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
                Expressions.column(dim, "id")));
        ProjectionRef ref = q.project(Expressions.column(dim, "name"), BestFitColumnType.STRING);
        q.groupOn(ref);
        q.project(Expressions.aggregateDistinct("count", Expressions.column(fact, "sales")),
                BestFitColumnType.INT, ColumnAlias.of("m0"));
        return q.build();
    }

    @Test
    void permissiveDialect_rendersFlatCountDistinct() {
        String sql = new DialectSqlRenderer(new AnsiDialect()).render(flatDistinctCount()).sql();
        assertEquals("select \"dim\".\"name\" as \"c0\", count(distinct \"fact\".\"sales\") as \"m0\""
                + FROM + " group by \"dim\".\"name\"", sql);
    }

    @Test
    void restrictiveDialect_rewritesToDistinctSubquery() {
        String sql = new DialectSqlRenderer(noCountDistinct()).render(flatDistinctCount()).sql();
        assertEquals(
                "select \"d0\" as \"c0\", count(\"m0\") as \"c1\" from ("
                        + "select distinct \"dim\".\"name\" as \"d0\", \"fact\".\"sales\" as \"m0\""
                        + FROM + ") as \"dummyname\" group by \"d0\"",
                sql);
    }

    @Test
    void restrictiveNoInnerDistinct_rewritesWithInnerGroupBy() {
        String sql = new DialectSqlRenderer(noCountDistinctNoInnerDistinct()).render(flatDistinctCount()).sql();
        assertEquals(
                "select \"d0\" as \"c0\", count(\"m0\") as \"c1\" from ("
                        + "select \"dim\".\"name\" as \"d0\", \"fact\".\"sales\" as \"m0\""
                        + FROM + " group by \"dim\".\"name\", \"fact\".\"sales\") as \"dummyname\" group by \"d0\"",
                sql);
    }
}
