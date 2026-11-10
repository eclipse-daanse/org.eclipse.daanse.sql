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

import java.util.List;

import org.eclipse.daanse.sql.dialect.api.generator.StatementHint;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Statement-level optimizer hints: dialect-free {@code StatementHint} intents on the
 * {@code SelectStatement}, spelled (or silently ignored) by the dialect's {@code HintGenerator}.
 * Unlike comments they are always emitted — never gated by {@code RenderOptions.comments()}.
 */
class StatementHintTest {

    private static final String NL = System.lineSeparator();

    private static SelectStatementBuilder read(StatementHint... hints) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        q.from(From.table("fact", t));
        q.project(Expressions.column(t, "id"), BestFitColumnType.INT);
        for (StatementHint hint : hints) {
            q.statementHint(hint);
        }
        return q;
    }

    @Test
    void ansi_ignoresHints_byteIdenticalToHintFree() {
        DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());
        String without = renderer.render(read().build()).sql();
        String with = renderer
                .render(read(new StatementHint("MAX_EXECUTION_TIME", List.of("1000"))).build()).sql();
        assertEquals(without, with);
        String formattedWithout = renderer.render(read().build(), RenderOptions.multiLine()).sql();
        String formattedWith = renderer.render(
                read(new StatementHint("MAX_EXECUTION_TIME", List.of("1000"))).build(),
                RenderOptions.multiLine()).sql();
        assertEquals(formattedWithout, formattedWith);
    }

    @Test
    void mysql_optimizerBlockAfterSelectKeyword() {
        DialectSqlRenderer renderer = new DialectSqlRenderer(new MySqlDialect());
        String hintFree = renderer.render(read().build()).sql();
        String sql = renderer
                .render(read(new StatementHint("MAX_EXECUTION_TIME", List.of("1000"))).build()).sql();
        assertTrue(sql.startsWith("select /*+ MAX_EXECUTION_TIME(1000) */ "), sql);
        // everything after the hint block is byte-identical to the hint-free statement
        assertEquals("select /*+ MAX_EXECUTION_TIME(1000) */ " + hintFree.substring("select ".length()), sql);
    }

    @Test
    void mysql_hintPrecedesDistinct() {
        SelectStatement s = read(new StatementHint("NO_INDEX_MERGE", List.of())).distinct(true).build();
        String sql = new DialectSqlRenderer(new MySqlDialect()).render(s).sql();
        assertTrue(sql.startsWith("select /*+ NO_INDEX_MERGE */ distinct "), sql);
    }

    @Test
    void sqlServer_trailingOptionClause() {
        DialectSqlRenderer renderer = new DialectSqlRenderer(new MicrosoftSqlServerDialect());
        String hintFree = renderer.render(read().build()).sql();
        String sql = renderer.render(read(new StatementHint("RECOMPILE", List.of())).build()).sql();
        assertEquals(hintFree + " OPTION (RECOMPILE)", sql);
    }

    @Test
    void hintsAreEmitted_evenWithCommentsOff_andWithCommentsOn() {
        // Hints are semantic for the DBMS: not gated by RenderOptions.comments().
        SelectStatement s = read(new StatementHint("RECOMPILE", List.of())).build();
        DialectSqlRenderer renderer = new DialectSqlRenderer(new MicrosoftSqlServerDialect());
        assertTrue(renderer.render(s, RenderOptions.compact()).sql().endsWith(" OPTION (RECOMPILE)"));
        assertTrue(renderer.render(s, RenderOptions.compact().withComments(true)).sql()
                .endsWith(" OPTION (RECOMPILE)"));
    }

    @Test
    void endOfStatementOrder_pagination_thenOption_thenFooter() {
        SelectStatementBuilder q = read(new StatementHint("MAXDOP", List.of("2")));
        q.rowLimit(10, 20);
        q.footerComment("emitted-by=StatementHintTest");
        String sql = new DialectSqlRenderer(new MicrosoftSqlServerDialect())
                .render(q.build(), RenderOptions.compact().withComments(true)).sql();
        assertTrue(sql.endsWith(" OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY OPTION (MAXDOP 2)"
                + NL + "/* emitted-by=StatementHintTest */"), sql);
    }
}
