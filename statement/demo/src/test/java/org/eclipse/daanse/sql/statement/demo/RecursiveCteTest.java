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

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.InsertStatementBuilder;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.WithStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.ColumnAlias;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SetOperation;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.Statement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.model.WithStatement;
import org.eclipse.daanse.sql.statement.exec.JdbcStatementExecutor;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * {@code WITH RECURSIVE}: walk a parent-child hierarchy (all descendants of a node) and run
 * it for real on H2. Uses an ANSI {@code JOIN ... ON} dialect for the recursive member (the
 * canonical recursive-CTE shape).
 */
class RecursiveCteTest {

    /** ANSI dialect that emits {@code JOIN ... ON} (rather than the comma-join fallback). */
    private final Dialect dialect = new AnsiDialect() {
        @Override
        public boolean allowsJoinOn() {
            return true;
        }
    };
    private final DialectSqlRenderer renderer = new DialectSqlRenderer(dialect);

    private record Built(WithStatement statement, ProjectionRef nameRef) {
    }

    /**
     * {@code WITH RECURSIVE descendants AS (
     *   SELECT id, parent_id, name FROM category WHERE id = 1
     *   UNION ALL
     *   SELECT c.id, c.parent_id, c.name FROM category c JOIN descendants d ON c.parent_id = d.id)
     * SELECT name FROM descendants ORDER BY name}.
     */
    private Built descendantsOfRoot() {
        // Anchor — column aliases define the CTE's column names (id, parent_id, name).
        SelectStatementBuilder anchor = SelectStatementBuilder.create();
        TableAlias ca = TableAlias.of("ca");
        anchor.from(From.table("category", ca));
        anchor.project(Expressions.column(ca, "id"), BestFitColumnType.INT, ColumnAlias.of("id"));
        anchor.project(Expressions.column(ca, "parent_id"), BestFitColumnType.INT, ColumnAlias.of("parent_id"));
        anchor.project(Expressions.column(ca, "name"), BestFitColumnType.STRING, ColumnAlias.of("name"));
        anchor.where(Predicates.eq(Expressions.column(ca, "id"), Expressions.literal(1, Datatype.INTEGER)));

        // Recursive member — joins the table back to the CTE.
        SelectStatementBuilder rec = SelectStatementBuilder.create();
        TableAlias c = TableAlias.of("c");
        TableAlias d = TableAlias.of("d");
        rec.from(From.table("category", c));
        rec.innerJoin(From.table("descendants", d),
                Predicates.eq(Expressions.column(c, "parent_id"), Expressions.column(d, "id")));
        rec.project(Expressions.column(c, "id"), BestFitColumnType.INT);
        rec.project(Expressions.column(c, "parent_id"), BestFitColumnType.INT);
        rec.project(Expressions.column(c, "name"), BestFitColumnType.STRING);

        Statement union = SetOperation.unionAll(List.of(anchor.build(), rec.build()));

        SelectStatementBuilder body = SelectStatementBuilder.create();
        TableAlias x = TableAlias.of("x");
        body.from(From.table("descendants", x));
        ProjectionRef nameRef = body.project(Expressions.column(x, "name"), BestFitColumnType.STRING);
        body.orderOn(nameRef, SortSpec.asc());

        WithStatement w = WithStatementBuilder.create().recursive(true)
                .cte("descendants", List.of("id", "parent_id", "name"), union).body(body.build()).build();
        return new Built(w, nameRef);
    }

    @Test
    void rendersWithRecursive() {
        String sql = renderer.render(descendantsOfRoot().statement()).sql();
        assertTrue(sql.startsWith("""
                WITH RECURSIVE "descendants"("id", "parent_id", "name") AS (\
                """), sql);
        assertTrue(sql.contains(" union all "), sql);
        assertTrue(sql.contains("""
                 join "descendants" as "d" on "c"."parent_id" = "d"."id"\
                """), sql);
        assertTrue(sql.endsWith("""
                select "x"."name" as "c0" from "descendants" as "x" \
                order by "x"."name" ASC\
                """), sql);
    }

    @Test
    void recursiveCte_onH2() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:rcte;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (java.sql.Statement s = conn.createStatement()) {
                s.execute("""
                        create table "category" \
                        ("id" int primary key, "parent_id" int, "name" varchar(50))\
                        """);
            }
            JdbcStatementExecutor exec = new JdbcStatementExecutor(conn);
            exec.update(renderer.render(InsertStatementBuilder.create().into("category")
                    .columns("id", "parent_id", "name")
                    .addRow(i(1), nul(), str("Electronics"))
                    .addRow(i(2), i(1), str("Computers"))
                    .addRow(i(3), i(2), str("Laptops"))
                    .addRow(i(4), i(1), str("Phones"))
                    .addRow(i(5), nul(), str("Food"))
                    .build()));

            Built built = descendantsOfRoot();
            List<String> names = exec.query(renderer.render(built.statement()),
                    row -> row.getString(built.nameRef()));
            // All descendants of Electronics(1): itself + Computers, Laptops, Phones (not Food).
            assertEquals(List.of("Computers", "Electronics", "Laptops", "Phones"), names);
        }
    }

    private static org.eclipse.daanse.sql.statement.api.expression.SqlExpression i(int v) {
        return Expressions.literal(v, Datatype.INTEGER);
    }

    private static org.eclipse.daanse.sql.statement.api.expression.SqlExpression nul() {
        return Expressions.literal(null, Datatype.INTEGER);
    }

    private static org.eclipse.daanse.sql.statement.api.expression.SqlExpression str(String v) {
        return Expressions.literal(v, Datatype.VARCHAR);
    }
}
