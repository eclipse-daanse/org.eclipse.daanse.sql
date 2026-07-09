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

import java.util.Map;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.jdbc.db.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * {@link FromClause.FromVariant} must render identically to a {@link FromClause.FromRaw} built from the
 * dialect-resolved fragment — i.e. the renderer's {@code chooseVariant} pick reproduces the legacy
 * {@code ViewCodeSet.chooseQuery} selection (live dialect, else {@code "generic"}) byte-for-byte. This is the
 * primary byte guard for the doc-04 FROM-side variant node (there is no integration view fixture).
 */
class FromVariantTest {

    private static final TableAlias V = TableAlias.of("v");
    private static final String GENERIC = "select 1 as x";
    private static final String MYSQL = "select 2 as x";
    private static final Map<String, String> VARIANTS = Map.of("generic", GENERIC, "mysql", MYSQL);

    private String renderVariant(Dialect d) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(new FromClause.FromVariant(VARIANTS, V));
        q.project(Expressions.column(V, "x"), BestFitColumnType.STRING);
        return new DialectSqlRenderer(d).render(q.build()).sql();
    }

    private String renderRaw(Dialect d, String sql) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.raw(sql, V));
        q.project(Expressions.column(V, "x"), BestFitColumnType.STRING);
        return new DialectSqlRenderer(d).render(q.build()).sql();
    }

    @Test
    void variantFallsBackToGenericForNonMatchingDialect() {
        Dialect ansi = new AnsiDialect();
        assertEquals(renderRaw(ansi, GENERIC), renderVariant(ansi));
    }

    @Test
    void variantPicksDialectSpecificForMysql() {
        Dialect mysql = new MySqlDialect();
        assertEquals(renderRaw(mysql, MYSQL), renderVariant(mysql));
    }

    // ---- RawVariant (SELECT-side computed-column expression) -----------------------------------------

    private String renderProjVariant(Dialect d) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.table("t", V));
        q.project(Expressions.rawVariant(VARIANTS), BestFitColumnType.STRING);
        return new DialectSqlRenderer(d).render(q.build()).sql();
    }

    private String renderProjRaw(Dialect d, String sql) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.table("t", V));
        q.project(Expressions.raw(sql), BestFitColumnType.STRING);
        return new DialectSqlRenderer(d).render(q.build()).sql();
    }

    @Test
    void rawVariantFallsBackToGenericForNonMatchingDialect() {
        Dialect ansi = new AnsiDialect();
        assertEquals(renderProjRaw(ansi, GENERIC), renderProjVariant(ansi));
    }

    @Test
    void rawVariantPicksDialectSpecificForMysql() {
        Dialect mysql = new MySqlDialect();
        assertEquals(renderProjRaw(mysql, MYSQL), renderProjVariant(mysql));
    }
}
