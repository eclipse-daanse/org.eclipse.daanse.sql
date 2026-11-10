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
import org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.junit.jupiter.api.Test;

/** A division asks the dialect for its dividend; every other operator and the default dialect render as written. */
class QuotientRenderingTest {

    private static String render(AnsiDialect dialect, ArithmeticOperator operator) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        SqlExpression a = Expressions.column(TableAlias.of("f"), "a");
        SqlExpression b = Expressions.column(TableAlias.of("f"), "b");
        q.project(new SqlExpression.Binary(a, operator, b), null);
        q.from(From.raw("select 1 as a, 2 as b", TableAlias.of("f")));
        return new DialectSqlRenderer(dialect).render(q.build()).sql();
    }

    private static final AnsiDialect CASTING = new AnsiDialect() {
        @Override
        public CharSequence quotientDividend(CharSequence dividend) {
            return "CAST(" + dividend + " AS DOUBLE)";
        }
    };

    @Test
    void aQuotientRendersAsWrittenByDefault() {
        assertEquals("select (\"f\".\"a\" / \"f\".\"b\") as \"c0\" from (select 1 as a, 2 as b) as \"f\"",
                render(new AnsiDialect(), ArithmeticOperator.DIVIDE));
    }

    @Test
    void aQuotientTakesTheDividendTheDialectNames() {
        assertEquals("select (CAST(\"f\".\"a\" AS DOUBLE) / \"f\".\"b\") as \"c0\" from (select 1 as a, 2 as b) as \"f\"",
                render(CASTING, ArithmeticOperator.DIVIDE));
    }

    @Test
    void otherOperatorsAreLeftAlone() {
        assertEquals("select (\"f\".\"a\" - \"f\".\"b\") as \"c0\" from (select 1 as a, 2 as b) as \"f\"",
                render(CASTING, ArithmeticOperator.SUBTRACT));
    }
}
