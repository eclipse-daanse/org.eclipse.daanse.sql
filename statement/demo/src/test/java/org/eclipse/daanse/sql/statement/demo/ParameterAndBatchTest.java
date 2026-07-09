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
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.exec.JdbcStatementExecutor;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/** Bind parameters: placeholder rendering, a parameterized query on H2, and a batch insert. */
class ParameterAndBatchTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    private static final String DDL = """
            create table "product" ("id" int primary key, "name" varchar(50))\
            """;

    @Test
    void param_rendersPlaceholderAndCarriesValue() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias p = TableAlias.of("p");
        q.from(From.table("product", p));
        q.project(Expressions.column(p, "name"), BestFitColumnType.STRING);
        q.where(Predicates.eq(Expressions.column(p, "id"), Expressions.param(7, Datatype.INTEGER)));
        RenderedSql sql = renderer.render(q.build());

        assertEquals("""
                select "p"."name" as "c0" from "product" as "p" where "p"."id" = ?\
                """, sql.sql());
        assertEquals(1, sql.parameters().size());
        assertEquals(7, sql.parameters().get(0).value());
        assertTrue(sql.parameters().get(0).bound());
    }

    @Test
    void parameterizedQuery_onH2() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:param;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement s = c.createStatement()) {
                s.execute(DDL);
            }
            JdbcStatementExecutor exec = new JdbcStatementExecutor(c);
            exec.update(renderer.render(InsertStatementBuilder.create().into("product").columns("id", "name")
                    .addRow(Expressions.literal(1, Datatype.INTEGER), Expressions.literal("Widget", Datatype.VARCHAR))
                    .addRow(Expressions.literal(2, Datatype.INTEGER), Expressions.literal("Gadget", Datatype.VARCHAR))
                    .build()));

            SelectStatementBuilder q = SelectStatementBuilder.create();
            TableAlias p = TableAlias.of("p");
            q.from(From.table("product", p));
            ProjectionRef nameRef = q.project(Expressions.column(p, "name"), BestFitColumnType.STRING);
            q.where(Predicates.eq(Expressions.column(p, "id"), Expressions.param(2, Datatype.INTEGER)));

            List<String> names = exec.query(renderer.render(q.build()), row -> row.getString(nameRef));
            assertEquals(List.of("Gadget"), names);
        }
    }

    @Test
    void batchInsert_onH2() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:batch;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement s = c.createStatement()) {
                s.execute(DDL);
            }
            JdbcStatementExecutor exec = new JdbcStatementExecutor(c);

            RenderedSql insert = renderer.render(InsertStatementBuilder.create().into("product").columns("id", "name")
                    .addRow(Expressions.paramMarker(Datatype.INTEGER), Expressions.paramMarker(Datatype.VARCHAR))
                    .build());
            assertEquals("""
                    insert into "product" ("id", "name") values (?, ?)\
                    """, insert.sql());

            int[] counts = exec.batch(insert,
                    List.of(new Object[] { 1, "A" }, new Object[] { 2, "B" }, new Object[] { 3, "C" }));
            assertEquals(3, counts.length);

            SelectStatementBuilder q = SelectStatementBuilder.create();
            TableAlias p = TableAlias.of("p");
            q.from(From.table("product", p));
            ProjectionRef cnt = q.project(Expressions.countStar(), BestFitColumnType.LONG);
            Long total = exec.query(renderer.render(q.build()), row -> row.getLong(cnt)).get(0);
            assertEquals(3L, total);
        }
    }
}
