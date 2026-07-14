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

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.InsertStatementBuilder;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.SetOperation;
import org.eclipse.daanse.sql.statement.api.model.Statement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * The footer comment: appended as a {@code /* ... *}{@code /} block on its own line at the very end
 * of the rendered statement (compact mode included), only when comments are on; multi-line text
 * stays multi-line; {@code *}{@code /} is neutralized; comments-off output is byte-identical.
 */
class FooterCommentTest {

    private static final String NL = System.lineSeparator();

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    private static SelectStatement plainRead(String footer) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        q.from(From.table("fact", t));
        q.project(Expressions.column(t, "id"), BestFitColumnType.INT);
        if (footer != null) {
            q.footerComment(footer);
        }
        return q.build();
    }

    @Test
    void compact_footerOnItsOwnLine_atTheVeryEnd() {
        String sql = renderer.render(plainRead("emitted-by=TupleSqlMapper"),
                RenderOptions.compact().withComments(true)).sql();
        assertEquals("select \"t\".\"id\" as \"c0\" from \"fact\" as \"t\""
                + NL + "/* emitted-by=TupleSqlMapper */", sql);
    }

    @Test
    void formatted_footerOnItsOwnLine_atTheVeryEnd() {
        String sql = renderer.render(plainRead("emitted-by=TupleSqlMapper"),
                RenderOptions.multiLine().withComments(true)).sql();
        assertTrue(sql.endsWith(NL + "/* emitted-by=TupleSqlMapper */"), sql);
    }

    @Test
    void multiLineFooter_staysMultiLineInsideTheBlock() {
        String sql = renderer.render(plainRead("request=[Sales]\nslicer=[Time].[1997]"),
                RenderOptions.compact().withComments(true)).sql();
        assertTrue(sql.endsWith(NL + "/* request=[Sales]\nslicer=[Time].[1997] */"), sql);
    }

    @Test
    void closingMarkerInFooter_isNeutralized() {
        String sql = renderer.render(plainRead("evil */ drop table x"),
                RenderOptions.compact().withComments(true)).sql();
        assertTrue(sql.endsWith(NL + "/* evil * / drop table x */"), sql);
        assertFalse(sql.contains("*/ drop"), sql);
    }

    @Test
    void commentsOff_isByteIdentical_withAndWithoutFooter() {
        String withFooter = renderer.render(plainRead("any footer"), RenderOptions.compact()).sql();
        String withoutFooter = renderer.render(plainRead(null), RenderOptions.compact()).sql();
        assertEquals(withoutFooter, withFooter);
        String formattedWith = renderer.render(plainRead("any footer"), RenderOptions.multiLine()).sql();
        String formattedWithout = renderer.render(plainRead(null), RenderOptions.multiLine()).sql();
        assertEquals(formattedWithout, formattedWith);
    }

    @Test
    void footer_onSetOperation() {
        Statement union = new SetOperation(SetOperation.SetOp.UNION_ALL,
                List.of(plainRead(null), plainRead(null)), List.of(), Optional.empty(),
                Optional.of("set-footer"));
        String sql = renderer.render(union, RenderOptions.compact().withComments(true)).sql();
        assertTrue(sql.endsWith(" union all select \"t\".\"id\" as \"c0\" from \"fact\" as \"t\""
                + NL + "/* set-footer */"), sql);
        // the inputs' own footers never leak into a nested render
        Statement unionOfFootered = new SetOperation(SetOperation.SetOp.UNION_ALL,
                List.of(plainRead("inner"), plainRead("inner")), List.of(), Optional.empty(),
                Optional.empty());
        assertFalse(renderer.render(unionOfFootered, RenderOptions.compact().withComments(true)).sql()
                .contains("inner"));
    }

    @Test
    void footer_onInsert() {
        String sql = renderer.render(InsertStatementBuilder.create().into("fact").columns("id")
                .addRow(Expressions.literal(1, Datatype.INTEGER))
                .footerComment("load-batch=42").build(),
                RenderOptions.compact().withComments(true)).sql();
        assertEquals("insert into \"fact\" (\"id\") values (1)" + NL + "/* load-batch=42 */", sql);
    }
}
