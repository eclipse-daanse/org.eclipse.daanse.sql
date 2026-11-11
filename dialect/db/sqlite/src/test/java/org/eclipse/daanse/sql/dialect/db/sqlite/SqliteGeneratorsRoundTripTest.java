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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertExecuteUpdateAffected;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertFirstIntEquals;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertFirstStringEquals;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertSelectIdRowCountAndFirst;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertSelectIdRowCountAndFirstWithParams;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.IndexColumn;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.IndexDefinition;
import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator.PreparedPaging;
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
    void pagination_limit_offset_executesPlaceholders() throws SQLException {
        PreparedPaging preparedPaging = dialect.paginationGenerator().paginatePrepared(false, false);
        assertThat(preparedPaging.clause()).isEqualTo("");
        preparedPaging = dialect.paginationGenerator().paginatePrepared(true, false);
        assertThat(preparedPaging.clause()).isEqualTo("LIMIT ?");
        preparedPaging = dialect.paginationGenerator().paginatePrepared(false, true);
        assertThat(preparedPaging.clause()).isEqualTo("OFFSET ?");
        preparedPaging = dialect.paginationGenerator().paginatePrepared(true, true);
        assertThat(preparedPaging.clause()).isEqualTo("LIMIT ? OFFSET ?");
        assertSelectIdRowCountAndFirstWithParams(conn, "SELECT id FROM \"users\" ORDER BY id " + preparedPaging.clause(), List.of(2, 1), 2, 2);
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

    /**
     * A unique index over the rows of a condition holds among those rows only; SQLite keeps it as a
     * partial index. SQLite takes no schema before the table of an index.
     */
    @Test
    void filtered_index_is_created() throws SQLException {
        TableReference table = new TableReference(Optional.empty(), "users", TableReference.TYPE_TABLE);
        String create = dialect.ddlGenerator().createIndex("ux_users_big", table, List.of("name"), true, "id > 100",
                false).orElseThrow();
        try (Statement s = conn.createStatement()) {
            s.execute(create);
            s.execute("INSERT INTO \"users\" VALUES (31, 'same'), (32, 'same'), (301, 'same')");
            assertThatThrownBy(() -> s.execute("INSERT INTO \"users\" VALUES (302, 'same')"))
                    .isInstanceOf(SQLException.class);
            try (ResultSet rs = s.executeQuery("SELECT \"partial\" FROM pragma_index_list('users')"
                    + " WHERE name = 'ux_users_big'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("partial").isEqualTo(1);
            }
            s.execute("DROP INDEX \"ux_users_big\"");
            s.execute("DELETE FROM \"users\" WHERE name = 'same'");
        }
    }

    /** An index the dialect writes with a descending column keeps the order of each column. */
    @Test
    void descending_index_column_is_created() throws SQLException {
        TableReference table = new TableReference(Optional.empty(), "users", TableReference.TYPE_TABLE);
        IndexDefinition index = new IndexDefinition("ix_users_desc", table,
                List.of(IndexColumn.descending("name"), IndexColumn.ascending("id")), false, null);
        try (Statement s = conn.createStatement()) {
            s.execute(dialect.ddlGenerator().createIndex(index, false).orElseThrow());
            List<String> columns = new java.util.ArrayList<>();
            try (ResultSet rs = s.executeQuery(
                    "SELECT name, \"desc\" FROM pragma_index_xinfo('ix_users_desc') WHERE \"key\" = 1 ORDER BY seqno")) {
                while (rs.next()) {
                    columns.add(rs.getString(1) + (rs.getInt(2) == 1 ? " DESC" : " ASC"));
                }
            }
            assertThat(columns).containsExactly("name DESC", "id ASC");
            s.execute("DROP INDEX \"ix_users_desc\"");
        }
    }
}
