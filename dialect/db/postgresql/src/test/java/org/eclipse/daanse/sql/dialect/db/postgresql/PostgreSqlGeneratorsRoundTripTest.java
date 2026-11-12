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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertExecuteUpdateAffected;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertFirstIntEquals;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertFirstStringEquals;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertSelectIdRowCountAndFirst;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertSelectIdRowCountAndFirstWithParams;

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
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.Deferrability;
import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator.PreparedPaging;
import org.eclipse.daanse.sql.dialect.db.testsupport.IndexRoundTrip;
import org.eclipse.daanse.sql.dialect.db.testsupport.UserRoundTrip;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.DatabasePrincipal;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.metadata.PostgreSqlMetadataProvider;
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

    /**
     * A user created without credentials takes a role and a privilege, is read back as a user
     * next to the role, and is dropped again.
     */
    @Test
    void user_is_created_given_a_role_and_dropped() throws SQLException {
        UserRoundTrip.assertUserRoundTrip(conn, dialect.ddlGenerator(), new PostgreSqlMetadataProvider(),
                DatabasePrincipal.KIND_USER, USERS, "rt_user", "rt_role");
    }

    /**
     * A materialized view the dialect writes holds the rows of its query and is read back as a
     * materialized view with its query; dropped, it is gone.
     */
    @Test
    void materialized_view_is_created_read_back_and_dropped() throws SQLException {
        TableReference view = new TableReference(Optional.of(new SchemaReference(Optional.empty(), "public")),
                "mv_users", TableReference.TYPE_VIEW);
        String query = "SELECT id, name FROM \"public\".\"users\" WHERE id <= 5";
        PostgreSqlMetadataProvider provider = new PostgreSqlMetadataProvider();
        try (Statement s = conn.createStatement()) {
            s.execute(dialect.ddlGenerator().createMaterializedView(view, query, false).orElseThrow());
            s.execute(dialect.ddlGenerator().createMaterializedView(view, query, true).orElseThrow());
            assertFirstIntEquals(conn, "SELECT COUNT(*) FROM \"public\".\"mv_users\"", 5);
            assertThat(provider.getAllMaterializedViews(conn, null, "public"))
                    .filteredOn(mv -> mv.view().name().equals("mv_users")).singleElement()
                    .satisfies(mv -> assertThat(mv.viewBody().orElseThrow()).containsIgnoringCase("users"));

            s.execute(dialect.ddlGenerator().dropMaterializedView(view, false).orElseThrow());
            s.execute(dialect.ddlGenerator().dropMaterializedView(view, true).orElseThrow());
            assertThat(provider.getAllMaterializedViews(conn, null, "public"))
                    .noneMatch(mv -> mv.view().name().equals("mv_users"));
        }
    }

    /**
     * A unique index over the rows of a condition holds among those rows only, and the metadata
     * provider reads its condition back.
     */
    @Test
    void filtered_index_is_created_and_read_back() throws SQLException {
        String create = dialect.ddlGenerator().createIndex("ux_users_big", USERS, List.of("name"), true, "id > 100",
                false).orElseThrow();
        try (Statement s = conn.createStatement()) {
            s.execute(create);
            s.execute("INSERT INTO \"public\".\"users\" VALUES (31, 'same'), (32, 'same'), (301, 'same')");
            assertThatThrownBy(() -> s.execute("INSERT INTO \"public\".\"users\" VALUES (302, 'same')"))
                    .isInstanceOf(SQLException.class);
            assertThat(new PostgreSqlMetadataProvider().getAllIndexInfo(conn, null, "public").orElseThrow())
                    .flatExtracting(IndexInfo::indexInfoItems)
                    .filteredOn(item -> item.indexName().filter("ux_users_big"::equals).isPresent()).singleElement()
                    .satisfies(item -> assertThat(item.filterCondition().orElseThrow()).contains("id > 100"));
            s.execute(dialect.ddlGenerator().dropIndex("ux_users_big", USERS, false));
            s.execute("DELETE FROM \"public\".\"users\" WHERE name = 'same'");
        }
    }

    /** An index the dialect writes with a descending column is read back with the order of each column. */
    @Test
    void descending_index_column_is_created_and_read_back() throws SQLException {
        IndexRoundTrip.assertDescendingIndexRoundTrip(conn, dialect.ddlGenerator(), new PostgreSqlMetadataProvider(), USERS,
                "ix_users_desc", "name", "id");
    }

    /**
     * A foreign key the dialect writes as checked at the end of the transaction lets a child come
     * before its parent within one, and is read back as initially deferred.
     */
    @Test
    void deferrable_foreign_key_is_checked_at_commit_and_read_back() throws SQLException {
        TableReference parent = new TableReference(Optional.of(new SchemaReference(Optional.empty(), "public")),
                "dp", TableReference.TYPE_TABLE);
        TableReference child = new TableReference(Optional.of(new SchemaReference(Optional.empty(), "public")),
                "dc", TableReference.TYPE_TABLE);
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE \"public\".\"dp\" (id INT PRIMARY KEY)");
            s.execute("CREATE TABLE \"public\".\"dc\" (id INT PRIMARY KEY, dp_id INT)");
            s.execute(dialect.ddlGenerator().addForeignKeyConstraint(child, "fk_dc_dp", List.of("dp_id"), parent,
                    List.of("id"), null, null, Deferrability.INITIALLY_DEFERRED).orElseThrow());
            conn.setAutoCommit(false);
            s.execute("INSERT INTO \"public\".\"dc\" VALUES (1, 7)");
            s.execute("INSERT INTO \"public\".\"dp\" VALUES (7)");
            conn.commit();
        } finally {
            conn.setAutoCommit(true);
        }
        assertThat(new PostgreSqlMetadataProvider().getAllImportedKeys(conn, null, "public").orElseThrow())
                .filteredOn(key -> "fk_dc_dp".equals(key.name())).singleElement()
                .satisfies(key -> assertThat(key.deferrability()).isEqualTo(ImportedKey.Deferrability.INITIALLY_DEFERRED));
        try (Statement s = conn.createStatement()) {
            s.execute("DROP TABLE \"public\".\"dc\"");
            s.execute("DROP TABLE \"public\".\"dp\"");
        }
    }
}
