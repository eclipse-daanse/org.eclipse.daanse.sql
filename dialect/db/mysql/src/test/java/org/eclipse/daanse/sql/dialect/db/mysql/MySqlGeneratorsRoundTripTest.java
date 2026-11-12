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
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertSelectIdRowCountAndFirstWithParams;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.ParameterMode;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineDefinition;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineParameter;
import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator.PreparedPaging;
import org.eclipse.daanse.sql.dialect.db.testsupport.IndexRoundTrip;
import org.eclipse.daanse.sql.dialect.db.testsupport.RoutineRoundTrip;
import org.eclipse.daanse.sql.dialect.db.testsupport.UserRoundTrip;
import org.eclipse.daanse.sql.jdbc.api.schema.DatabasePrincipal;
import org.eclipse.daanse.sql.jdbc.metadata.MySqlMetadataProvider;
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
    void pagination_limit_offset_executesPlaceholders() throws SQLException {
        PreparedPaging preparedPaging = dialect.paginationGenerator().paginatePrepared(false, false);
        assertThat(preparedPaging.clause()).isEqualTo("");
        preparedPaging = dialect.paginationGenerator().paginatePrepared(true, false);
        assertThat(preparedPaging.clause()).isEqualTo("LIMIT ?");
        preparedPaging = dialect.paginationGenerator().paginatePrepared(false, true);
        assertThat(preparedPaging.clause()).isEqualTo("OFFSET ?");
        preparedPaging = dialect.paginationGenerator().paginatePrepared(true, true);
        assertThat(preparedPaging.clause()).isEqualTo("LIMIT ? OFFSET ?");
        assertSelectIdRowCountAndFirstWithParams(conn, "SELECT id FROM `rt`.`users` ORDER BY id" + preparedPaging.clause(), List.of(2, 1), 2, 2);
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

    /**
     * A user created without credentials, locked, takes a role and a privilege, is read back as a
     * principal MySQL does not tell from a role, and is dropped again.
     */
    @Test
    void user_is_created_given_a_role_and_dropped() throws SQLException {
        // root may create users and read mysql.user; the container's user may not
        try (Connection root = java.sql.DriverManager.getConnection(CONTAINER.getJdbcUrl(), "root",
                CONTAINER.getPassword())) {
            UserRoundTrip.assertUserRoundTrip(root, dialect.ddlGenerator(), new MySqlMetadataProvider(),
                    DatabasePrincipal.KIND_UNKNOWN, users, "rt_user", "rt_role");
        }
    }

    /** An index the dialect writes with a descending column is read back with the order of each column. */
    @Test
    void descending_index_column_is_created_and_read_back() throws SQLException {
        IndexRoundTrip.assertDescendingIndexRoundTrip(conn, dialect.ddlGenerator(), new MySqlMetadataProvider(), users,
                "ix_users_desc", "name", "id");
    }

    /**
     * A function and a procedure the dialect writes are created, the function is called, both are
     * read back with their parameters, language and body, and dropped by their signature. A body
     * starts with the routine's characteristics, as the database writes them.
     */
    @Test
    void routines_are_created_called_read_back_and_dropped() throws SQLException {
        RoutineDefinition function = new RoutineDefinition("rt", "rt_add",
                List.of(RoutineParameter.in("a", "INT"), RoutineParameter.in("b", "INT")), "INT", "sql",
                "DETERMINISTIC RETURN a + b");
        RoutineDefinition procedure = new RoutineDefinition("rt", "rt_bump",
                List.of(RoutineParameter.in("n", "INT"), new RoutineParameter("total", ParameterMode.INOUT, "INT")),
                null, "sql", "SET total = total + n");
        // root may create a function while the binary log is on; the container's user may not
        try (Connection root = java.sql.DriverManager.getConnection(CONTAINER.getJdbcUrl(), "root", CONTAINER.getPassword())) {
            RoutineRoundTrip.assertRoutineRoundTrip(root, dialect.ddlGenerator(), new MySqlMetadataProvider(), function, procedure,
                    "SELECT `rt`.`rt_add`(2, 3)", 5, List.of("n IN", "total INOUT"), "a + b", "total = total + n");
        }
    }

    /** A role the dialect writes is created, again where it can say so, read back and dropped. */
    @Test
    void role_is_created_read_back_and_dropped() throws SQLException {
        try (Connection root = java.sql.DriverManager.getConnection(CONTAINER.getJdbcUrl(), "root",
                CONTAINER.getPassword())) {
            UserRoundTrip.assertRoleRoundTrip(root, dialect.ddlGenerator(), new MySqlMetadataProvider(),
                    DatabasePrincipal.KIND_UNKNOWN, "rt_only_role");
        }
    }
}
