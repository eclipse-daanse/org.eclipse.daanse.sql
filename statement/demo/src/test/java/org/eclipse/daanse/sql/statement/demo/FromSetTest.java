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

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.SetOperation;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * A {@link SetOperation} as a derived table ({@code FromClause.FromSet}): the FROM item renders as
 * the parenthesized concatenation of its inputs' individual renders — byte-identical to
 * {@code "(" + inputA + " union " + inputB + ") as alias"} — with nested bind parameters
 * accumulating in placeholder order.
 */
class FromSetTest {

    private final Dialect dialect = new AnsiDialect();
    private final DialectSqlRenderer renderer = new DialectSqlRenderer(dialect);

    /** {@code select t.id from tbl as t}, optionally filtered on {@code id = ?}. */
    private static SelectStatement input(String table, String aliasName, Integer paramValue) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of(aliasName);
        q.from(From.table(table, t));
        q.project(Expressions.column(t, "id"), BestFitColumnType.INT);
        if (paramValue != null) {
            q.where(Predicates.eq(Expressions.column(t, "id"),
                    Expressions.param(paramValue, Datatype.INTEGER)));
        }
        return q.build();
    }

    /** {@code select * from (<set>) as u}, optionally {@code order by 1 ASC}. */
    private SelectStatement outer(SetOperation set, boolean orderBy) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.set(set, TableAlias.of("u")));
        q.project(Expressions.star(), null);
        if (orderBy) {
            q.orderOn(Expressions.ordinal(1), SortSpec.asc());
        }
        return q.build();
    }

    /** The byte identity R5 relies on: the FROM item IS the inputs' own renders joined by the keyword. */
    @Test
    void unionInFrom_byteIdenticalToConcatenatedInputRenders() {
        SelectStatement inA = input("t1", "a", null);
        SelectStatement inB = input("t2", "b", null);
        String a = renderer.render(inA).sql();
        String b = renderer.render(inB).sql();

        SetOperation union = new SetOperation(SetOperation.SetOp.UNION, List.of(inA, inB), List.of(),
                Optional.empty());
        String sql = renderer.render(outer(union, true)).sql();

        assertEquals("select * from (" + a + " union " + b + ") as \"u\" order by 1 ASC", sql);
    }

    @Test
    void unionAllInFrom_byteIdenticalToConcatenatedInputRenders() {
        SelectStatement inA = input("t1", "a", null);
        SelectStatement inB = input("t2", "b", null);
        String a = renderer.render(inA).sql();
        String b = renderer.render(inB).sql();

        String sql = renderer.render(outer(SetOperation.unionAll(List.of(inA, inB)), false)).sql();

        assertEquals("select * from (" + a + " union all " + b + ") as \"u\"", sql);
    }

    /** Bind parameters of both union inputs accumulate once each, in placeholder (input) order. */
    @Test
    void unionInFrom_bindParametersAccumulateInPlaceholderOrder() {
        SelectStatement inA = input("t1", "a", 7);
        SelectStatement inB = input("t2", "b", 11);
        String a = renderer.render(inA).sql();
        String b = renderer.render(inB).sql();

        SetOperation union = new SetOperation(SetOperation.SetOp.UNION, List.of(inA, inB), List.of(),
                Optional.empty());
        RenderedSql sql = renderer.render(outer(union, true));

        assertEquals("select * from (" + a + " union " + b + ") as \"u\" order by 1 ASC", sql.sql());
        assertEquals(2, sql.parameters().size());
        assertEquals(7, sql.parameters().get(0).value());
        assertEquals(11, sql.parameters().get(1).value());
    }
}
