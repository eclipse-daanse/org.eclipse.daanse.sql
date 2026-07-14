/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertExecuteUpdateAffected;
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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
@TestInstance(Lifecycle.PER_CLASS)
class OracleGeneratorsRoundTripTest {

    @Container
    @SuppressWarnings("resource")
    static final OracleContainer CONTAINER = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
            .withUsername("rt").withPassword("rt");

    private Connection conn;
    private OracleDialect dialect;
    private TableReference users;

    @BeforeAll
    void setUp() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        conn = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
        dialect = new OracleDialect(DialectInitData.fromConnection(conn));
        // Oracle: schema == user (uppercased).
        String schema = CONTAINER.getUsername().toUpperCase();
        users = new TableReference(Optional.of(new SchemaReference(Optional.empty(), schema)), "USERS",
                TableReference.TYPE_TABLE);
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE \"" + schema + "\".\"USERS\" (id NUMBER PRIMARY KEY, name VARCHAR2(50))");
            // Oracle pre-23c can't do multi-row VALUES — INSERT one at a time.
            for (int i = 1; i <= 5; i++) {
                s.execute(
                        "INSERT INTO \"" + schema + "\".\"USERS\" VALUES (" + i + ", '" + (char) ('a' + i - 1) + "')");
            }
        }
    }

    @AfterAll
    void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed())
            conn.close();
    }

    @Test
    void pagination_offset_fetch_next_executes() throws SQLException {
        // Default generator emits SQL-2008 OFFSET/FETCH which Oracle 12c+ accepts.
        String tail = dialect.paginationGenerator().paginate(OptionalLong.of(2), OptionalLong.of(1));
        assertThat(tail).contains("OFFSET 1 ROWS").contains("FETCH NEXT 2 ROWS ONLY");
        String schema = CONTAINER.getUsername().toUpperCase();
        assertSelectIdRowCountAndFirst(conn, "SELECT id FROM \"" + schema + "\".\"USERS\" ORDER BY id" + tail, 2, 2);
    }

    @Test
    void merge_into_using_dual_inserts_then_updates() throws SQLException {
        MergeGenerator.UpsertSpec spec = new MergeGenerator.UpsertSpec(users, List.of("ID"), List.of("ID", "NAME"),
                List.of("NAME"));
        String sql1 = dialect.mergeGenerator().upsert(spec, List.of("10", "'first'")).orElseThrow();
        assertExecuteUpdateAffected(conn, sql1, 1, "merge: insert path");
        String sql2 = dialect.mergeGenerator().upsert(spec, List.of("10", "'second'")).orElseThrow();
        assertExecuteUpdateAffected(conn, sql2, 1, "merge: update path");
        String schema = CONTAINER.getUsername().toUpperCase();
        assertFirstStringEquals(conn, "SELECT name FROM \"" + schema + "\".\"USERS\" WHERE id = 10", "second");
    }

    @Test
    void returning_unsupported_on_pre_23c_oracle() {
        // Oracle XE 21 reports major < 23 → generator falls through to empty default.
        // (PG-style RETURNING was introduced in 23c, Aug 2023.)
        assertThat(dialect.returningGenerator().returning(List.of("id"))).isEmpty();
    }
}
