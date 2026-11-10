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

import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.DeleteStatementBuilder;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/** Uses the shared sql.model identifier types ({@code TableReference}/{@code ColumnReference}). */
class TableRefTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    private static final TableReference SALES_ORDER = new TableReference(Optional.of(new SchemaReference("sales")),
            "order");

    @Test
    void selectWithTableReferenceAndColumnReference() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias o = TableAlias.of("o");
        q.from(From.table(SALES_ORDER, o));
        // ColumnReference carries metadata (its table); only its name() is used, qualified by alias o.
        q.project(Expressions.column(o, new ColumnReference(Optional.of(SALES_ORDER), "amount")),
                BestFitColumnType.DECIMAL);

        String sql = renderer.render(q.build()).sql();
        assertEquals("""
                select "o"."amount" as "c0" from "sales"."order" as "o"\
                """, sql);
    }

    @Test
    void deleteWithTableReference() {
        String sql = renderer.render(DeleteStatementBuilder.create().from(SALES_ORDER)
                .where(Predicates.eq(Expressions.column("id"), Expressions.literal(1, Datatype.INTEGER)))
                .build()).sql();
        assertEquals("""
                delete from "sales"."order" where "id" = 1\
                """, sql);
    }
}
