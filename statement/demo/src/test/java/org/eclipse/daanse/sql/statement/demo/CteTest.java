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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

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
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.model.WithStatement;
import org.eclipse.daanse.sql.statement.exec.JdbcStatementExecutor;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/** Common-table expression ({@code WITH}) rendering and a real H2 round-trip. */
class CteTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    private record Built(WithStatement statement, ProjectionRef nameRef) {
    }

    /**
     * {@code WITH expensive AS (SELECT id, name FROM product WHERE price > 100)
     * SELECT name FROM expensive ORDER BY name}. The CTE columns are aliased so the body can
     * reference them by name (a CTE's column names come from its inner projection aliases).
     */
    private Built expensive() {
        SelectStatementBuilder cte = SelectStatementBuilder.create();
        TableAlias pr = TableAlias.of("pr");
        cte.from(From.table("product", pr));
        cte.project(Expressions.column(pr, "id"), BestFitColumnType.INT, ColumnAlias.of("id"));
        cte.project(Expressions.column(pr, "name"), BestFitColumnType.STRING, ColumnAlias.of("name"));
        cte.where(Predicates.gt(Expressions.column(pr, "price"), Expressions.literal(100, Datatype.INTEGER)));

        SelectStatementBuilder body = SelectStatementBuilder.create();
        TableAlias e = TableAlias.of("e");
        body.from(From.table("expensive", e));
        ProjectionRef nameRef = body.project(Expressions.column(e, "name"), BestFitColumnType.STRING);
        body.orderOn(nameRef, SortSpec.asc());

        WithStatement w = WithStatementBuilder.create().cte("expensive", cte.build()).body(body.build()).build();
        return new Built(w, nameRef);
    }

    @Test
    void rendersWithClause() {
        String sql = renderer.render(expensive().statement()).sql();
        assertEquals("""
                WITH "expensive" AS (\
                select "pr"."id" as "id", "pr"."name" as "name" from "product" as "pr" \
                where "pr"."price" > 100) \
                select "e"."name" as "c0" from "expensive" as "e" order by "e"."name" ASC\
                """, sql);
    }

    @Test
    void cteRoundTrip_onH2() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:cte;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement s = c.createStatement()) {
                s.execute("""
                        create table "product" ("id" int primary key, "name" varchar(50), "price" int)\
                        """);
            }
            JdbcStatementExecutor exec = new JdbcStatementExecutor(c);
            exec.update(renderer.render(InsertStatementBuilder.create().into("product").columns("id", "name", "price")
                    .addRow(Expressions.literal(1, Datatype.INTEGER), Expressions.literal("Cheap", Datatype.VARCHAR),
                            Expressions.literal(50, Datatype.INTEGER))
                    .addRow(Expressions.literal(2, Datatype.INTEGER), Expressions.literal("Mid", Datatype.VARCHAR),
                            Expressions.literal(150, Datatype.INTEGER))
                    .addRow(Expressions.literal(3, Datatype.INTEGER), Expressions.literal("Lux", Datatype.VARCHAR),
                            Expressions.literal(300, Datatype.INTEGER))
                    .build()));

            Built built = expensive();
            List<String> names = exec.query(renderer.render(built.statement()), row -> row.getString(built.nameRef()));
            assertEquals(List.of("Lux", "Mid"), names);
        }
    }
}
