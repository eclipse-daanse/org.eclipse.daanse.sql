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

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.DeleteStatementBuilder;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.InsertStatementBuilder;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.UpdateStatementBuilder;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/** Golden tests for the INSERT/UPDATE/DELETE statements (rendered with the ANSI dialect). */
class DmlTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer((Dialect) new AnsiDialect());

    @Test
    void insertValues() {
        String sql = renderer.render(InsertStatementBuilder.create().into("product").columns("id", "name")
                .addRow(Expressions.literal(1, Datatype.INTEGER), Expressions.literal("Widget", Datatype.VARCHAR))
                .addRow(Expressions.literal(2, Datatype.INTEGER), Expressions.literal("Gadget", Datatype.VARCHAR))
                .build()).sql();
        assertEquals("""
                insert into "product" ("id", "name") values (1, 'Widget'), (2, 'Gadget')\
                """, sql);
    }

    @Test
    void update() {
        String sql = renderer.render(UpdateStatementBuilder.create().table("product")
                .set("name", Expressions.literal("Gizmo", Datatype.VARCHAR))
                .set("price", Expressions.literal(10, Datatype.INTEGER))
                .where(Predicates.eq(Expressions.column("id"), Expressions.literal(1, Datatype.INTEGER)))
                .build()).sql();
        assertEquals("""
                update "product" set "name" = 'Gizmo', "price" = 10 where "id" = 1\
                """, sql);
    }

    @Test
    void delete() {
        String sql = renderer.render(DeleteStatementBuilder.create().from("product")
                .where(Predicates.eq(Expressions.column("id"), Expressions.literal(1, Datatype.INTEGER)))
                .build()).sql();
        assertEquals("""
                delete from "product" where "id" = 1\
                """, sql);
    }

    @Test
    void insertFromSelect() {
        org.eclipse.daanse.sql.statement.api.SelectStatementBuilder src = org.eclipse.daanse.sql.statement.api.SelectStatementBuilder
                .create();
        org.eclipse.daanse.sql.statement.api.model.TableAlias p = org.eclipse.daanse.sql.statement.api.model.TableAlias
                .of("p");
        src.from(org.eclipse.daanse.sql.statement.api.From.table("product", p));
        src.project(Expressions.column(p, "id"), null);
        src.project(Expressions.column(p, "name"), null);
        String sql = renderer.render(InsertStatementBuilder.create().into("product_archive").columns("id", "name")
                .fromSelect(src.build()).build()).sql();
        assertEquals("""
                insert into "product_archive" ("id", "name") \
                select "p"."id" as "c0", "p"."name" as "c1" from "product" as "p"\
                """, sql);
    }
}
