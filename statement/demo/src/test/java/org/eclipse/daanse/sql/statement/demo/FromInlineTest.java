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

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.jdbc.db.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * {@link org.eclipse.daanse.sql.statement.api.model.FromClause.FromInline} carries inline-{@code VALUES}
 * data and the renderer generates the dialect-specific SQL at render time via
 * {@code dialect.sqlGenerator().generateInline(...)}. This is the per-dialect byte-check the TCK cannot
 * provide (no test schema uses inline tables): the rendered FROM must equal the same
 * {@code generateInline} output wrapped as a derived table — byte-identical to the legacy
 * {@code convertInlineTableToRelation} embedding.
 */
class FromInlineTest {

    private static final List<String> NAMES = List.of("id", "label");
    private static final List<String> TYPES = List.of("INTEGER", "VARCHAR");
    private static final List<String[]> ROWS = List.of(new String[] { "1", "Mon" }, new String[] { "2", "Tue" });

    @Test
    void fromInline_rendersDialectGeneratedValuesWrappedAsDerivedTable() {
        TableAlias alias = TableAlias.of("days");
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.inline(NAMES, TYPES, ROWS, alias));
        q.project(Expressions.column(alias, "label"), BestFitColumnType.STRING);
        var statement = q.build();

        for (Dialect dialect : List.of(new MySqlDialect(), new AnsiDialect())) {
            String sql = new DialectSqlRenderer(dialect).render(statement).sql();
            String inline = dialect.sqlGenerator().generateInline(NAMES, TYPES, ROWS).toString();
            String expectedFrom = "(" + inline + ")"
                    + (dialect.allowsFromAlias() ? " as " : " ") + dialect.quoteIdentifier("days");
            assertTrue(sql.contains(expectedFrom),
                    dialect.getClass().getSimpleName() + " FROM mismatch:\n" + sql + "\nexpected FROM:\n" + expectedFrom);
        }
    }
}
