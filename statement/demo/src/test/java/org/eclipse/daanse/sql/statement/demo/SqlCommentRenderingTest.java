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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.ColumnAlias;
import org.eclipse.daanse.sql.statement.api.model.JoinKind;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions.CommentStyle;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Validates the optional explanatory-comment rendering: a statement-level header, per-column and
 * per-join comments are emitted only when {@link RenderOptions#comments()} is on, the executed
 * (comments-off) SQL is unchanged, and an embedded {@code *}{@code /} cannot terminate a block comment early.
 */
class SqlCommentRenderingTest {

    private final Dialect ansi = new AnsiDialect();

    /** The Gender level-members read: header + a commented join + a commented column (with a tricky {@code *}{@code /}). */
    private static SelectStatement genderRead() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias customer = TableAlias.of("customer");
        TableAlias fact = TableAlias.of("sales_fact_1997");
        q.header("request=[Customer].[Gender].MEMBERS\ncube=[Sales]");
        q.from(From.table("customer", customer));
        q.join(JoinKind.INNER, From.table("sales_fact_1997", fact),
                Predicates.eq(Expressions.column(fact, "customer_id"), Expressions.column(customer, "customer_id")),
                "dim=[Customer] reason=NonEmpty */ injection");
        q.project(Expressions.column(customer, "gender"), BestFitColumnType.STRING, ColumnAlias.of("c0"),
                "lvl=[Customer].[Gender] role=key");
        return q.build();
    }

    @Test
    void commentsOff_isPlainExecutableSql() {
        String sql = new DialectSqlRenderer(ansi).render(genderRead(), RenderOptions.multiLine()).sql();
        assertFalse(sql.contains("/*"), sql);
        assertFalse(sql.contains("--"), sql);
        assertFalse(sql.contains("[Customer]"), sql);
        // compact form is byte-exact (no comment artifacts at all)
        String compact = new DialectSqlRenderer(ansi).render(genderRead(), RenderOptions.compact()).sql();
        assertEquals("""
                select "customer"."gender" as "c0" from "customer" as "customer" \
                join "sales_fact_1997" as "sales_fact_1997" \
                on "sales_fact_1997"."customer_id" = "customer"."customer_id"\
                """, compact);
    }

    @Test
    void commentsOn_emitsHeaderColumnAndJoin_andNeutralizesClosingMarker() {
        String sql = new DialectSqlRenderer(ansi)
                .render(genderRead(), RenderOptions.multiLine().withComments(true)).sql();
        // header rendered as -- lines (own lines, safe)
        assertTrue(sql.contains("-- request=[Customer].[Gender].MEMBERS"), sql);
        assertTrue(sql.contains("-- cube=[Sales]"), sql);
        // per-column comment
        assertTrue(sql.contains("/* lvl=[Customer].[Gender] role=key */"), sql);
        // per-join comment with the embedded */ neutralized to "* /" so it cannot close the comment early
        assertTrue(sql.contains("/* dim=[Customer] reason=NonEmpty * / injection */"), sql);
        assertFalse(sql.contains("*/ injection"), sql);
        // and the actual SQL structure is still intact after the comments
        assertTrue(sql.contains("join "), sql);
        assertTrue(sql.contains("\"customer\".\"gender\""), sql);
    }

    /** Gender read with GROUP BY + ORDER BY referencing the commented column. */
    private static SelectStatement genderReadGrouped() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias customer = TableAlias.of("customer");
        TableAlias fact = TableAlias.of("sales_fact_1997");
        q.header("request=[Customer].[Gender].MEMBERS\ncube=[Sales]");
        q.from(From.table("customer", customer));
        q.join(JoinKind.INNER, From.table("sales_fact_1997", fact),
                Predicates.eq(Expressions.column(fact, "customer_id"), Expressions.column(customer, "customer_id")),
                "dim=[Customer] reason=NonEmpty");
        ProjectionRef gender = q.project(Expressions.column(customer, "gender"), BestFitColumnType.STRING,
                ColumnAlias.of("c0"), "lvl=[Customer].[Gender] role=key");
        q.groupOn(gender);
        q.orderOn(gender, SortSpec.asc());
        return q.build();
    }

    @Test
    void lineStyle_commentsBeforeEachElement_reusedInGroupAndOrder() {
        String sql = new DialectSqlRenderer(ansi)
                .render(genderReadGrouped(), RenderOptions.multiLine().withComments(true, CommentStyle.LINE)).sql();
        // LINE style uses -- and never /* */
        assertFalse(sql.contains("/*"), sql);
        // header
        assertTrue(sql.contains("-- request=[Customer].[Gender].MEMBERS"), sql);
        // join rendered on its own line, with its comment on the line above
        assertTrue(java.util.regex.Pattern.compile("(?m)^\\s*-- dim=\\[Customer\\] reason=NonEmpty$").matcher(sql)
                .find(), sql);
        assertTrue(java.util.regex.Pattern.compile("(?m)^\\s*join \"sales_fact_1997\"").matcher(sql).find(), sql);
        // the level comment is reused in SELECT, GROUP BY and ORDER BY (>=3 occurrences)
        int n = sql.split(java.util.regex.Pattern.quote("-- lvl=[Customer].[Gender] role=key"), -1).length - 1;
        assertTrue(n >= 3, "expected the lvl comment in select+group+order, got " + n + "\n" + sql);
    }

    /** Gender read whose BASE FROM table carries a provenance comment (From.commentBase). */
    private static SelectStatement genderReadWithBaseComment() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias customer = TableAlias.of("customer");
        TableAlias fact = TableAlias.of("sales_fact_1997");
        q.from(From.commentBase(From.table("customer", customer),
                "dimension [Customer] level table [Customer].[Gender]"));
        q.join(JoinKind.INNER, From.table("sales_fact_1997", fact),
                Predicates.eq(Expressions.column(fact, "customer_id"), Expressions.column(customer, "customer_id")),
                "fact join (nonempty)");
        q.project(Expressions.column(customer, "gender"), BestFitColumnType.STRING, ColumnAlias.of("c0"));
        return q.build();
    }

    @Test
    void baseTableComment_lineStyle_ownLineBeforeBaseTable() {
        String sql = new DialectSqlRenderer(ansi)
                .render(genderReadWithBaseComment(), RenderOptions.multiLine().withComments(true, CommentStyle.LINE))
                .sql();
        // the base-table comment sits on its own line directly above the base table
        assertTrue(java.util.regex.Pattern.compile(
                """
                        (?m)^\\s*-- dimension \\[Customer\\] level table \\[Customer\\]\\.\\[Gender\\]$\
                        \\R\\s*"customer" as "customer"\
                        """).matcher(sql).find(), sql);
        // the join comment still sits above the join line (both provenances coexist)
        assertTrue(java.util.regex.Pattern.compile("(?m)^\\s*-- fact join \\(nonempty\\)$").matcher(sql).find(), sql);
    }

    @Test
    void baseTableComment_blockStyle_inlineBeforeBaseTable() {
        String sql = new DialectSqlRenderer(ansi)
                .render(genderReadWithBaseComment(), RenderOptions.compact().withComments(true)).sql();
        assertTrue(sql.contains(
                """
                        from /* dimension [Customer] level table [Customer].[Gender] */ "customer" as "customer"\
                        """), sql);
    }

    @Test
    void baseTableComment_commentsOff_isByteExact() {
        // With comments off, a base-table comment leaves the SQL byte-identical to a comment-free build.
        String off = new DialectSqlRenderer(ansi).render(genderReadWithBaseComment(), RenderOptions.compact()).sql();
        assertEquals("""
                select "customer"."gender" as "c0" from "customer" as "customer" \
                join "sales_fact_1997" as "sales_fact_1997" \
                on "sales_fact_1997"."customer_id" = "customer"."customer_id"\
                """, off);
    }

    /**
     * The cardinality probe ({@code select count(*) from (select distinct <keys>) init}) — a
     * commented statement with a DERIVED TABLE: outer header/footer, inner header, inner level-key
     * projection comment and inner base-table comment. Exercises the diagnostic nested-formatted
     * contract: formatted + comments render the inner SELECT formatted too (LINE comments, indent
     * stacking); compact renders stay byte-identical / comment-free in the nested context.
     */
    private static SelectStatement cardinalityProbe() {
        TableAlias store = TableAlias.of("store");
        SelectStatementBuilder inner = SelectStatementBuilder.create();
        inner.header("distinct level keys");
        inner.distinct(true);
        inner.from(From.commentBase(From.table("store", store),
                "dimension [Store] level table [Store].[Store State]"));
        inner.project(Expressions.column(store, "store_state"), BestFitColumnType.STRING, null,
                "level key [Store].[Store State]");

        SelectStatementBuilder outer = SelectStatementBuilder.create();
        outer.header("level cardinality [Store].[Store].[Store State]");
        outer.footerComment("cardinality probe (count distinct keys)");
        outer.project(Expressions.aggregate("count", Expressions.star()), null);
        outer.from(From.subquery(inner.build(), TableAlias.of("init")));
        return outer.build();
    }

    @Test
    void nestedSubquery_commentsOff_compact_isByteExact() {
        String sql = new DialectSqlRenderer(ansi).render(cardinalityProbe(), RenderOptions.compact()).sql();
        assertEquals("""
                select count(*) as "c0" from (select distinct "store"."store_state" as "c0" \
                from "store" as "store") as "init"\
                """, sql);
    }

    @Test
    void nestedSubquery_diagnosticMode_rendersInnerFormatted_withIndentStacking() {
        String nl = System.lineSeparator();
        String sql = new DialectSqlRenderer(ansi)
                .render(cardinalityProbe(), RenderOptions.multiLine().withComments(true, CommentStyle.LINE)).sql();
        assertEquals("-- level cardinality [Store].[Store].[Store State]" + nl
                + "select" + nl
                + "    count(*) as \"c0\"" + nl
                + "from" + nl
                + "    (" + nl
                + "        -- distinct level keys" + nl
                + "        select distinct" + nl
                + "            -- level key [Store].[Store State]" + nl
                + "            \"store\".\"store_state\" as \"c0\"" + nl
                + "        from" + nl
                + "            -- dimension [Store] level table [Store].[Store State]" + nl
                + "            \"store\" as \"store\"" + nl
                + "    ) as \"init\"" + nl
                + "/* cardinality probe (count distinct keys) */", sql);
    }

    @Test
    void nestedSubquery_compactWithComments_staysCompactAndCommentFree() {
        // Compact + comments (BLOCK) is NOT the diagnostic combination: the derived table renders
        // compact without comments, exactly as before; outer header/footer render as always.
        String sql = new DialectSqlRenderer(ansi)
                .render(cardinalityProbe(), RenderOptions.compact().withComments(true)).sql();
        assertTrue(sql.contains("""
                (select distinct "store"."store_state" as "c0" \
                from "store" as "store") as "init"\
                """), sql);
        assertFalse(sql.contains("level key"), sql);
        assertFalse(sql.contains("distinct level keys"), sql);
        assertTrue(sql.startsWith("-- level cardinality [Store].[Store].[Store State]"), sql);
        assertTrue(sql.endsWith("/* cardinality probe (count distinct keys) */"), sql);
    }

    @Test
    void nestedSubquery_formattedCommentsOff_isUnchangedNestedCompact() {
        String sql = new DialectSqlRenderer(ansi).render(cardinalityProbe(), RenderOptions.multiLine()).sql();
        assertTrue(sql.contains("""
                (select distinct "store"."store_state" as "c0" \
                from "store" as "store") as "init"\
                """), sql);
        assertFalse(sql.contains("--"), sql);
        assertFalse(sql.contains("/*"), sql);
    }

    @Test
    void lineStyle_commentsOff_isByteExact() {
        // LINE style with comments OFF must be identical to a plain multi-line render.
        String off = new DialectSqlRenderer(ansi)
                .render(genderReadGrouped(), RenderOptions.multiLine().withComments(false, CommentStyle.LINE)).sql();
        String plain = new DialectSqlRenderer(ansi).render(genderReadGrouped(), RenderOptions.multiLine()).sql();
        assertEquals(plain, off);
        assertFalse(off.contains("--"), off);
    }
}
