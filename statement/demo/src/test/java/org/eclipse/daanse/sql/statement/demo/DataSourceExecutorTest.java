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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.InsertStatementBuilder;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.exec.StatementExecutor;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.exec.DataSourceStatementExecutor;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

/** {@link DataSourceStatementExecutor}: per-call connections, plus transactional commit/rollback. */
class DataSourceExecutorTest {

    private final DialectSqlRenderer renderer = new DialectSqlRenderer(new AnsiDialect());

    private JdbcDataSource h2() {
        JdbcDataSource ds = new JdbcDataSource();
        // DB_CLOSE_DELAY=-1 keeps the in-memory DB alive across the per-call connections.
        ds.setURL("jdbc:h2:mem:dse;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private RenderedSql insert(int id, String name) {
        return renderer.render(InsertStatementBuilder.create().into("product").columns("id", "name")
                .addRow(Expressions.literal(id, Datatype.INTEGER), Expressions.literal(name, Datatype.VARCHAR))
                .build());
    }

    private long count(StatementExecutor exec) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias p = TableAlias.of("p");
        q.from(From.table("product", p));
        q.project(Expressions.countStar(), BestFitColumnType.LONG);
        return exec.query(renderer.render(q.build()), row -> row.getLong(0)).get(0);
    }

    @Test
    void perCall_and_transaction_commit_and_rollback() {
        DataSourceStatementExecutor exec = new DataSourceStatementExecutor(h2());

        // DDL and a one-shot insert, each on its own borrowed connection.
        exec.update(RenderedSql.of("""
                create table "product" ("id" int primary key, "name" varchar(50))\
                """,
                List.of()));
        exec.update(insert(1, "Widget"));
        assertEquals(1L, count(exec));

        // Two inserts in one transaction; the count seen inside reflects them, and they persist.
        long insideCount = exec.inTransaction(tx -> {
            tx.update(insert(2, "Gadget"));
            tx.update(insert(3, "Gizmo"));
            return count(tx);
        });
        assertEquals(3L, insideCount);
        assertEquals(3L, count(exec));

        // A failing transaction is rolled back: row 4 must not persist.
        RuntimeException boom = assertThrows(RuntimeException.class, () -> exec.inTransaction(tx -> {
            tx.update(insert(4, "Doomed"));
            throw new IllegalStateException("boom");
        }));
        assertEquals("boom", boom.getMessage());
        assertEquals(3L, count(exec));
    }
}
