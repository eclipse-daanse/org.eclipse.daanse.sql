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

import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.junit.jupiter.api.Test;

/**
 * An {@code avg} aggregate asks the dialect for its operand: an engine that averages whole numbers to a whole number
 * (SQL Server) reads it otherwise; every other aggregate and every other dialect render as written.
 */
class AverageRenderingTest {

    private static String avgOver(AnsiDialect dialect, boolean distinct) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        var column = Expressions.column(TableAlias.of("f"), "x");
        q.project(distinct ? Expressions.aggregateDistinct("avg", column) : Expressions.aggregate("avg", column), null);
        q.project(Expressions.aggregate("sum", column), null);
        q.from(From.raw("select 1 as x", TableAlias.of("f")));
        return new DialectSqlRenderer(dialect).render(q.build()).sql();
    }

    private static final AnsiDialect CASTING = new AnsiDialect() {
        @Override
        public CharSequence averageOperand(CharSequence operand) {
            return "CAST(" + operand + " AS float)";
        }
    };

    @Test
    void anAverageRendersAsWrittenByDefault() {
        assertEquals("select avg(\"f\".\"x\") as \"c0\", sum(\"f\".\"x\") as \"c1\" from (select 1 as x) as \"f\"",
                avgOver(new AnsiDialect(), false));
    }

    @Test
    void anAverageTakesTheOperandTheDialectNames() {
        assertEquals("select avg(CAST(\"f\".\"x\" AS float)) as \"c0\", sum(\"f\".\"x\") as \"c1\" from (select 1 as x)"
                + " as \"f\"", avgOver(CASTING, false));
    }

    @Test
    void aDistinctAverageKeepsItsDistinct() {
        assertEquals("select avg(distinct CAST(\"f\".\"x\" AS float)) as \"c0\", sum(\"f\".\"x\") as \"c1\" from"
                + " (select 1 as x) as \"f\"", avgOver(CASTING, true));
    }
}
