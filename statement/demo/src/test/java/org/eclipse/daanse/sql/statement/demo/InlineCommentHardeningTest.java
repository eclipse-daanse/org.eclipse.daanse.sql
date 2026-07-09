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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.ColumnAlias;
import org.eclipse.daanse.sql.statement.api.model.JoinKind;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Hardening for inline BLOCK comments in COMPACT mode at every existing attachment point (header,
 * projection, filter, having, join): the SQL around the comment must stay structurally intact, an
 * embedded {@code *}{@code /} cannot close the comment early, and newlines are folded so the inline
 * comment stays on one line.
 */
class InlineCommentHardeningTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    private static final RenderOptions COMPACT_COMMENTS = RenderOptions.compact().withComments(true);

    @Test
    void compactBlock_allAttachmentPoints() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias a = TableAlias.of("a");
        TableAlias b = TableAlias.of("b");
        q.header("hdr");
        q.from(From.table("alpha", a));
        q.join(JoinKind.INNER, From.table("beta", b),
                Predicates.eq(Expressions.column(a, "id"), Expressions.column(b, "alpha_id")), "join-cmt");
        q.project(Expressions.column(a, "name"), BestFitColumnType.STRING, ColumnAlias.of("c0"), "proj-cmt");
        q.where(Predicates.gt(Expressions.column(a, "qty"), Expressions.literal(1, Datatype.INTEGER)), "where-cmt");
        q.having(Predicates.gt(Expressions.countStar(), Expressions.literal(0, Datatype.INTEGER)), "having-cmt");
        String sql = renderer.render(q.build(), COMPACT_COMMENTS).sql();

        // header lines come first, as -- lines, even in compact mode (existing behavior)
        assertTrue(sql.startsWith("-- hdr" + System.lineSeparator() + "select "), sql);
        // projection comment inline after the aliased column
        assertTrue(sql.contains("\"a\".\"name\" as \"c0\" /* proj-cmt */"), sql);
        // join comment inline between the keyword and the joined table
        assertTrue(sql.contains("""
                 join /* join-cmt */ "beta" as "b" on \
                """), sql);
        // filter and having comments inline after their conjunct
        assertTrue(sql.contains("""
                 where "a"."qty" > 1 /* where-cmt */\
                """), sql);
        assertTrue(sql.contains(" having COUNT(*) > 0 /* having-cmt */"), sql);
    }

    @Test
    void compactBlock_embeddedClosingMarker_isNeutralizedEverywhere() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias a = TableAlias.of("a");
        q.from(From.table("alpha", a));
        q.project(Expressions.column(a, "name"), BestFitColumnType.STRING, ColumnAlias.of("c0"), "p */ q");
        q.where(Predicates.gt(Expressions.column(a, "qty"), Expressions.literal(1, Datatype.INTEGER)), "w */ x");
        String sql = renderer.render(q.build(), COMPACT_COMMENTS).sql();
        assertTrue(sql.contains("/* p * / q */"), sql);
        assertTrue(sql.contains("/* w * / x */"), sql);
        assertFalse(sql.contains("*/ q"), sql);
        assertFalse(sql.contains("*/ x"), sql);
    }

    @Test
    void compactBlock_newlinesInInlineComments_areFolded() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias a = TableAlias.of("a");
        q.from(From.table("alpha", a));
        q.project(Expressions.column(a, "name"), BestFitColumnType.STRING, ColumnAlias.of("c0"),
                "line1\nline2\r\nline3");
        String sql = renderer.render(q.build(), COMPACT_COMMENTS).sql();
        assertTrue(sql.contains("/* line1 line2  line3 */"), sql);
    }

    /**
     * PIN of current (latent) behavior: a {@code Raw("t.*")} projection is NOT recognized as a
     * whole-row projection (only bare {@code Raw("*")} and the structured {@code Star} are), so the
     * generated {@code as c0} alias IS appended — producing invalid SQL if ever built. Documented
     * here so any change is deliberate; use {@code Expressions.star(alias)} for a qualified star.
     */
    @Test
    void rawQualifiedStar_currentBehavior_getsAliasAppended() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        q.from(From.table("fact", t));
        q.project(Expressions.raw("t.*"), null);
        assertEquals("""
                select t.* as "c0" from "fact" as "t"\
                """, renderer.render(q.build()).sql());
    }
}
