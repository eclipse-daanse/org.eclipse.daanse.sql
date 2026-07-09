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
import static org.junit.jupiter.api.Assertions.assertSame;

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * The constant predicates {@code alwaysTrue()} / {@code alwaysFalse()}: they render with the same
 * {@code 1 = 1} / {@code 1 = 0} spelling as an empty connective, without parentheses.
 */
class ConstantPredicateTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    @Test
    void constants_renderWithoutParens() {
        assertEquals("1 = 1", renderer.renderPredicate(Predicates.alwaysTrue()));
        assertEquals("1 = 0", renderer.renderPredicate(Predicates.alwaysFalse()));
    }

    @Test
    void factories_returnSingletons() {
        assertSame(Predicates.alwaysTrue(), Predicates.alwaysTrue());
        assertSame(Predicates.alwaysFalse(), Predicates.alwaysFalse());
    }

    @Test
    void constant_insideAnd() {
        String sql = renderer.renderPredicate(Predicates.and(Predicates.alwaysTrue(),
                Predicates.eq(Expressions.column("id"), Expressions.literal(1, Datatype.INTEGER))));
        assertEquals("(1 = 1 and \"id\" = 1)", sql);
    }

    @Test
    void notAlwaysFalse() {
        assertEquals("not (1 = 0)", renderer.renderPredicate(Predicates.not(Predicates.alwaysFalse())));
    }

    @Test
    void emptyConnectives_useTheSameSpelling() {
        // The empty-connective case and Constant share one helper — pin both spellings.
        assertEquals("1 = 1", renderer.renderPredicate(Predicates.and()));
        assertEquals("1 = 0", renderer.renderPredicate(Predicates.or()));
    }

    @Test
    void constant_inWhereOfFullStatement() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias t = TableAlias.of("t");
        q.from(From.table("fact", t));
        q.project(Expressions.column(t, "id"), BestFitColumnType.INT);
        q.where(Predicates.alwaysFalse());
        assertEquals("""
                select "t"."id" as "c0" from "fact" as "t" where 1 = 0\
                """,
                renderer.render(q.build()).sql());
    }
}
