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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.statement.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.junit.jupiter.api.Test;

/**
 * An expression written once for every database beside the texts some dialects have: the dialect's own text wins, else
 * the tree is rendered in that dialect, else the generic text is taken.
 */
class PortableRenderingTest {

    private static String render(AnsiDialect dialect, SqlExpression expression) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.project(expression, null);
        q.from(From.raw("select 'a' as x", TableAlias.of("f")));
        return new DialectSqlRenderer(dialect).render(q.build()).sql();
    }

    private static final SqlExpression TREE = new SqlExpression.KnownCall(KnownFunction.TO_TEXT,
            List.of(Expressions.column(TableAlias.of("f"), "x")));

    @Test
    void theDialectsOwnTextWins() {
        AnsiDialect ansi = new AnsiDialect();
        assertEquals("select own(x) as \"c0\" from (select 'a' as x) as \"f\"",
                render(ansi, Expressions.portable(TREE, Map.of(ansi.name(), "own(x)", "mssql", "CONVERT(varchar, x)"))));
    }

    @Test
    void elseTheTreeIsRenderedInTheDialect() {
        assertEquals("select CAST(\"f\".\"x\" AS VARCHAR(4000)) as \"c0\" from (select 'a' as x) as \"f\"",
                render(new AnsiDialect(), Expressions.portable(TREE, Map.of("mssql", "CONVERT(varchar, x)"))));
    }

    @Test
    void withoutATreeTheGenericTextIsTaken() {
        assertEquals("select generic(x) as \"c0\" from (select 'a' as x) as \"f\"",
                render(new AnsiDialect(), Expressions.portable(null, Map.of("generic", "generic(x)"))));
    }

    private static String renderFrom(AnsiDialect dialect, FromClause from) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.project(Expressions.column(TableAlias.of("v"), "x"), null);
        q.from(from);
        return new DialectSqlRenderer(dialect).render(q.build()).sql();
    }

    /** A derived table: the dialect's own text, else the tree, else the generic text. */
    @Test
    void aDerivedTableTakesItsTextOrItsTree() {
        AnsiDialect ansi = new AnsiDialect();
        SelectStatementBuilder inner = SelectStatementBuilder.create();
        inner.project(Expressions.column(TableAlias.of("t"), "x"), null,
                org.eclipse.daanse.sql.statement.api.model.ColumnAlias.of("x"));
        inner.from(From.table("t", TableAlias.of("t")));
        FromClause.Aliased tree = From.subquery(inner.build(), TableAlias.of("v"));
        assertEquals("select \"v\".\"x\" as \"c0\" from (select own) as \"v\"", renderFrom(ansi,
                new FromClause.FromPortable(tree, Map.of(ansi.name(), "select own"), TableAlias.of("v"))));
        assertEquals("select \"v\".\"x\" as \"c0\" from (select \"t\".\"x\" as \"x\" from \"t\" as \"t\") as \"v\"",
                renderFrom(ansi, new FromClause.FromPortable(tree, Map.of("mssql", "select x from t"),
                        TableAlias.of("v"))));
        assertEquals("select \"v\".\"x\" as \"c0\" from (select generic) as \"v\"", renderFrom(ansi,
                new FromClause.FromPortable(null, Map.of("generic", "select generic"), TableAlias.of("v"))));
    }
}
