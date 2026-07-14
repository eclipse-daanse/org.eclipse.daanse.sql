/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertExecuteUpdateAffected;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertFirstStringEquals;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertSelectIdRowCountAndFirst;

import java.sql.Connection;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
@TestInstance(Lifecycle.PER_CLASS)
class MySqlGeneratorsRoundTripTest {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> CONTAINER = new MySQLContainer<>("mysql:8.4").withDatabaseName("rt")
            .withUsername("rt").withPassword("rt");

    private Connection conn;
    private MySqlDialect dialect;
    private TableReference users;

    @BeforeAll
    void setUp() throws Exception {
        conn = java.sql.DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(),
                CONTAINER.getPassword());
        dialect = new MySqlDialect(DialectInitData.fromConnection(conn));
        users = new TableReference(Optional.of(new SchemaReference(Optional.empty(), "rt")), "users",
                TableReference.TYPE_TABLE);
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE `rt`.`users` (id INT PRIMARY KEY, name VARCHAR(50))");
            s.execute("INSERT INTO `rt`.`users` VALUES (1,'a'),(2,'b'),(3,'c'),(4,'d'),(5,'e')");
        }
    }

    @AfterAll
    void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed())
            conn.close();
    }

    @Test
    void pagination_limit_offset_executes() throws SQLException {
        // MySQL paginate emits ` LIMIT off, lim` — different shape from PG.
        String tail = dialect.paginationGenerator().paginate(OptionalLong.of(2), OptionalLong.of(1));
        assertThat(tail).isEqualTo(" LIMIT 1, 2");
        assertSelectIdRowCountAndFirst(conn, "SELECT id FROM `rt`.`users` ORDER BY id" + tail, 2, 2);
    }

    @Test
    void pagination_offset_only_uses_huge_limit_trick() throws SQLException {
        // MySQL has no offset-only form, so the generator emits LIMIT off, MAX_UINT64.
        // WHERE id <= 5 isolates this test from rows inserted by the upsert test.
        String tail = dialect.paginationGenerator().paginate(OptionalLong.empty(), OptionalLong.of(2));
        assertSelectIdRowCountAndFirst(conn, "SELECT id FROM `rt`.`users` WHERE id <= 5 ORDER BY id" + tail, 3, 3);
    }

    @Test
    void upsert_on_duplicate_key_inserts_then_updates() throws SQLException {
        MergeGenerator.UpsertSpec spec = new MergeGenerator.UpsertSpec(users, List.of("id"), List.of("id", "name"),
                List.of("name"));
        String sql1 = dialect.mergeGenerator().upsert(spec, List.of("10", "'first'")).orElseThrow();
        // ON DUPLICATE KEY UPDATE returns 1 for insert, 2 for update.
        assertExecuteUpdateAffected(conn, sql1, 1, "first upsert inserts a new row");
        String sql2 = dialect.mergeGenerator().upsert(spec, List.of("10", "'second'")).orElseThrow();
        assertExecuteUpdateAffected(conn, sql2, 2, "second upsert updates the existing row");
        assertFirstStringEquals(conn, "SELECT name FROM `rt`.`users` WHERE id = 10", "second");
    }

    @Test
    void returning_unsupported_returns_empty() {
        // MySQL has no RETURNING — callers fall back to LAST_INSERT_ID() or SELECT.
        assertThat(dialect.returningGenerator().returning(List.of("id"))).isEmpty();
    }
}
