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

import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.ColumnAlias;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * The structured whole-row projection {@code Star}: bare {@code *}, qualified {@code t.*} (neither
 * ever gets an {@code as cN} alias), and the {@code countStar()} text pin after its internals moved
 * from {@code Raw("*")} to {@code Star}.
 */
class StarProjectionTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    @Test
    void selectStar_noAliasAppended() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        q.from(From.table("fact", t));
        q.project(Expressions.star(), null);
        assertEquals("select * from \"fact\" as \"t\"", renderer.render(q.build()).sql());
    }

    @Test
    void selectQualifiedStar_noAliasAppended() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        q.from(From.table("fact", t));
        q.project(Expressions.star(t), null);
        assertEquals("select \"t\".* from \"fact\" as \"t\"", renderer.render(q.build()).sql());
    }

    @Test
    void qualifiedStar_notAliased_evenWithExplicitAlias() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        q.from(From.table("fact", t));
        q.project(Expressions.star(t), null, ColumnAlias.of("x"));
        // Star suppresses ANY alias — "select t.* as x" would be invalid SQL.
        assertEquals("select \"t\".* from \"fact\" as \"t\"", renderer.render(q.build()).sql());
    }

    /** {@code countStar()} must render exactly as before the internal Raw("*") -> Star retarget. */
    @Test
    void countStar_textPin() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("fact");
        q.from(From.table("fact", t));
        q.project(Expressions.countStar(), BestFitColumnType.LONG, ColumnAlias.of("cnt"));
        assertEquals("""
                select COUNT(*) as "cnt" from "fact" as "fact"\
                """, renderer.render(q.build()).sql());
    }
}
