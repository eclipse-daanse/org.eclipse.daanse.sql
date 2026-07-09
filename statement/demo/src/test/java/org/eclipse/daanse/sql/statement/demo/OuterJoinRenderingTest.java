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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.JoinKind;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * RIGHT/FULL outer joins always render the ANSI keyword — there is no comma fallback (that remains
 * INNER-only) and no emulation: a dialect without support fails at the database.
 */
class OuterJoinRenderingTest {

    private final Dialect ansi = new AnsiDialect();

    /** An ANSI dialect that disallows {@code JOIN ... ON}, forcing the INNER comma fallback. */
    private final Dialect commaOnly = new AnsiDialect() {
        @Override
        public boolean allowsJoinOn() {
            return false;
        }
    };

    private static SelectStatement joined(JoinKind kind) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias a = TableAlias.of("a");
        TableAlias b = TableAlias.of("b");
        q.from(From.table("alpha", a));
        q.join(kind, From.table("beta", b),
                Predicates.eq(Expressions.column(a, "id"), Expressions.column(b, "alpha_id")));
        q.project(Expressions.column(a, "id"), BestFitColumnType.INT);
        return q.build();
    }

    @Test
    void rightJoin_ansiKeyword() {
        assertEquals("""
                select "a"."id" as "c0" from "alpha" as "a" \
                right join "beta" as "b" on "a"."id" = "b"."alpha_id"\
                """,
                new DialectSqlRenderer(ansi).render(joined(JoinKind.RIGHT)).sql());
    }

    @Test
    void fullJoin_ansiKeyword() {
        assertEquals("""
                select "a"."id" as "c0" from "alpha" as "a" \
                full join "beta" as "b" on "a"."id" = "b"."alpha_id"\
                """,
                new DialectSqlRenderer(ansi).render(joined(JoinKind.FULL)).sql());
    }

    @Test
    void innerJoin_stillCommaFallsBack_whenJoinOnDisallowed() {
        assertEquals("""
                select "a"."id" as "c0" from "alpha" as "a", "beta" as "b" \
                where "a"."id" = "b"."alpha_id"\
                """,
                new DialectSqlRenderer(commaOnly).render(joined(JoinKind.INNER)).sql());
    }

    @Test
    void rightAndFullJoin_neverCommaFallBack() {
        assertEquals("""
                select "a"."id" as "c0" from "alpha" as "a" \
                right join "beta" as "b" on "a"."id" = "b"."alpha_id"\
                """,
                new DialectSqlRenderer(commaOnly).render(joined(JoinKind.RIGHT)).sql());
        assertEquals("""
                select "a"."id" as "c0" from "alpha" as "a" \
                full join "beta" as "b" on "a"."id" = "b"."alpha_id"\
                """,
                new DialectSqlRenderer(commaOnly).render(joined(JoinKind.FULL)).sql());
    }
}
