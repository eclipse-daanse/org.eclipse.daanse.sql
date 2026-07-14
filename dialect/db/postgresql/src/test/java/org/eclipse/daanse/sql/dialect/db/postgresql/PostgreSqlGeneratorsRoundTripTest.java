/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.postgresql;

import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertExecuteUpdateAffected;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertFirstIntEquals;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertFirstStringEquals;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertSelectIdRowCountAndFirst;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class PostgreSqlGeneratorsRoundTripTest {

    private static final String DB = "rt";
    private static final String USER = "postgres";
    private static final String PWD = "secret";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> PG = new GenericContainer<>("postgres:15")
            .withEnv("POSTGRES_PASSWORD", PWD).withEnv("POSTGRES_DB", DB).withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private static String jdbcUrl;
    private static Connection conn;
    private static PostgreSqlDialect dialect;
    private static final TableReference USERS = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "public")), "users", TableReference.TYPE_TABLE);

    @BeforeAll
    static void setUp() throws Exception {
        PG.start();
        Class.forName("org.postgresql.Driver");
        jdbcUrl = "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + DB;
        conn = DriverManager.getConnection(jdbcUrl, USER, PWD);
        dialect = new PostgreSqlDialect(DialectInitData.fromConnection(conn));

        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE \"public\".\"users\" (id INT PRIMARY KEY, name VARCHAR(50))");
            s.execute("INSERT INTO \"public\".\"users\" VALUES (1,'a'),(2,'b'),(3,'c'),(4,'d'),(5,'e')");
        }
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed())
            conn.close();
    }

    @Test
    void pagination_limit_offset_executes() throws SQLException {
        String tail = dialect.paginationGenerator().paginate(OptionalLong.of(2), OptionalLong.of(1));
        assertSelectIdRowCountAndFirst(conn, "SELECT id FROM \"public\".\"users\" ORDER BY id" + tail, 2, 2);
    }

    @Test
    void upsert_on_conflict_inserts_then_updates() throws SQLException {
        MergeGenerator.UpsertSpec spec = new MergeGenerator.UpsertSpec(USERS, List.of("id"), List.of("id", "name"),
                List.of("name"));
        String sql1 = dialect.mergeGenerator().upsert(spec, List.of("10", "'first'")).orElseThrow();
        assertExecuteUpdateAffected(conn, sql1, 1, "first upsert should insert one row");
        String sql2 = dialect.mergeGenerator().upsert(spec, List.of("10", "'second'")).orElseThrow();
        assertExecuteUpdateAffected(conn, sql2, 1, "second upsert should update one row");
        assertFirstStringEquals(conn, "SELECT name FROM \"public\".\"users\" WHERE id = 10", "second");
    }

    @Test
    void returning_clause_returns_inserted_id() throws SQLException {
        String returning = dialect.returningGenerator().returning(List.of("id")).orElseThrow();
        assertFirstIntEquals(conn, "INSERT INTO \"public\".\"users\" (id, name) VALUES (99, 'returned')" + returning,
                99);
    }
}
