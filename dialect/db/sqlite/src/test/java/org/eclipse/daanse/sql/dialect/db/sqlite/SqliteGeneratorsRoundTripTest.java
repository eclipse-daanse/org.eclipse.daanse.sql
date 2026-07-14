/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.sqlite;

import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertExecuteUpdateAffected;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertFirstIntEquals;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertFirstStringEquals;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertSelectIdRowCountAndFirst;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
class SqliteGeneratorsRoundTripTest {

    private Connection conn;
    private SqliteDialect dialect;
    private TableReference users;

    @BeforeAll
    void setUp() throws Exception {
        Class.forName("org.sqlite.JDBC");
        // SQLite has no real schemas; "main" is the implicit default.
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        dialect = new SqliteDialect(DialectInitData.fromConnection(conn));
        users = new TableReference(Optional.of(new SchemaReference(Optional.empty(), "main")), "users",
                TableReference.TYPE_TABLE);
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE \"users\" (id INTEGER PRIMARY KEY, name TEXT)");
            s.execute("INSERT INTO \"users\" VALUES (1,'a'),(2,'b'),(3,'c'),(4,'d'),(5,'e')");
        }
    }

    @AfterAll
    void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed())
            conn.close();
    }

    @Test
    void pagination_limit_offset_executes() throws SQLException {
        // SQLite parses both ANSI " OFFSET m FETCH NEXT n ROWS ONLY" and "LIMIT n
        // OFFSET m";
        // default generator emits the ANSI form.
        String tail = dialect.paginationGenerator().paginate(OptionalLong.of(2), OptionalLong.of(1));
        assertSelectIdRowCountAndFirst(conn, "SELECT id FROM \"users\" ORDER BY id" + tail, 2, 2);
    }

    @Test
    void upsert_on_conflict_inserts_then_updates() throws SQLException {
        MergeGenerator.UpsertSpec spec = new MergeGenerator.UpsertSpec(users, List.of("id"), List.of("id", "name"),
                List.of("name"));
        String sql1 = dialect.mergeGenerator().upsert(spec, List.of("10", "'first'")).orElseThrow();
        assertExecuteUpdateAffected(conn, sql1, 1, "upsert: insert path");
        String sql2 = dialect.mergeGenerator().upsert(spec, List.of("10", "'second'")).orElseThrow();
        assertExecuteUpdateAffected(conn, sql2, 1, "upsert: update path");
        assertFirstStringEquals(conn, "SELECT name FROM \"users\" WHERE id = 10", "second");
    }

    @Test
    void returning_clause_returns_inserted_id() throws SQLException {
        String returning = dialect.returningGenerator().returning(List.of("id")).orElseThrow();
        assertFirstIntEquals(conn, "INSERT INTO \"users\" (id, name) VALUES (99, 'returned')" + returning, 99);
    }
}
