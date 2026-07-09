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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.api.sql.BitOperation;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.ColumnAlias;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * {@link SqlExpression.ExtraAggregate} carries dialect-free parameters and the renderer generates the SQL
 * at render time via {@code dialect.aggregationGenerator()} — the same call the (now node-producing) extra
 * aggregators used to make at build time, so the rendered SQL is byte-identical. This checks the renderer
 * dispatches to the right generator method with the rendered operand.
 */
class ExtraAggregateTest {

    private final Dialect mysql = new MySqlDialect();

    private String render(SqlExpression projection) {
        TableAlias t = TableAlias.of("t");
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.table("t", t));
        q.project(projection, BestFitColumnType.LONG, ColumnAlias.of("m0"));
        return new DialectSqlRenderer(mysql).render(q.build()).sql();
    }

    @Test
    void bitAggregation_dispatchesToGeneratorWithRenderedOperand() {
        TableAlias t = TableAlias.of("t");
        SqlExpression operand = Expressions.column(t, "flags");
        // the operand SQL exactly as the renderer produces it
        String operandSql = render(operand);
        operandSql = operandSql.substring(operandSql.indexOf(' ') + 1, operandSql.indexOf(" as "));

        String sql = render(new SqlExpression.ExtraAggregate(Optional.of(operand),
                new SqlExpression.ExtraAggregate.Spec.BitAggregation(BitOperation.AND)));

        String expected = mysql.aggregationGenerator().generateBitAggregation(BitOperation.AND, operandSql)
                .orElseThrow(() -> new AssertionError("MySql does not support bit aggregation"));
        assertTrue(sql.contains(expected), "rendered=" + sql + "  expected fragment=" + expected);
    }

    @Test
    void caseFold_rendersViaDialectUpperCaseFunction() {
        TableAlias t = TableAlias.of("t");
        SqlExpression col = Expressions.column(t, "name");
        String colSql = render(col);
        colSql = colSql.substring(colSql.indexOf(' ') + 1, colSql.indexOf(" as "));

        String sql = render(new SqlExpression.CaseFold(col));

        String expected = mysql.functionGenerator().wrapIntoSqlUpperCaseFunction(colSql).toString();
        assertTrue(sql.contains(expected), "rendered=" + sql + "  expected fragment=" + expected);
    }
}
