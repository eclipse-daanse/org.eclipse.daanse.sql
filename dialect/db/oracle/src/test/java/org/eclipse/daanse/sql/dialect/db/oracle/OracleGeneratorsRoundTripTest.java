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
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertFirstIntEquals;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertFirstStringEquals;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertSelectIdRowCountAndFirst;
import static org.eclipse.daanse.sql.dialect.db.testsupport.RoundTripAssertions.assertSelectIdRowCountAndFirstWithParams;

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
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.Deferrability;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.ParameterMode;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineDefinition;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineParameter;
import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator.PreparedPaging;
import org.eclipse.daanse.sql.dialect.db.testsupport.IndexRoundTrip;
import org.eclipse.daanse.sql.dialect.db.testsupport.RoutineRoundTrip;
import org.eclipse.daanse.sql.dialect.db.testsupport.UserRoundTrip;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.DatabasePrincipal;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.jdbc.metadata.OracleMetadataProvider;
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
    void pagination_limit_offset_executesPlaceholders() throws SQLException {
        PreparedPaging preparedPaging = dialect.paginationGenerator().paginatePrepared(false, false);
        assertThat(preparedPaging.clause()).isEqualTo("");
        preparedPaging = dialect.paginationGenerator().paginatePrepared(true, false);
        assertThat(preparedPaging.clause()).isEqualTo("OFFSET ? ROWS");
        preparedPaging = dialect.paginationGenerator().paginatePrepared(false, true);
        assertThat(preparedPaging.clause()).isEqualTo("FETCH NEXT ? ROWS ONLY");
        preparedPaging = dialect.paginationGenerator().paginatePrepared(true, true);
        assertThat(preparedPaging.clause()).isEqualTo("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        String schema = CONTAINER.getUsername().toUpperCase();
        assertSelectIdRowCountAndFirstWithParams(conn, "SELECT id FROM \"" + schema + "\".\"USERS\" ORDER BY id" + preparedPaging.clause(), List.of(1, 2), 2, 2);
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

    /**
     * A user created without credentials takes a role and a privilege, is read back as a user
     * next to the role, and is dropped again.
     */
    @Test
    void user_is_created_given_a_role_and_dropped() throws SQLException {
        // SYSTEM may create users and roles and read DBA_USERS; the container's user may not
        try (Connection system = DriverManager.getConnection(CONTAINER.getJdbcUrl(), "system",
                CONTAINER.getPassword())) {
            UserRoundTrip.assertUserRoundTrip(system, dialect.ddlGenerator(), new OracleMetadataProvider(),
                    DatabasePrincipal.KIND_USER, users, "rt_user", "rt_role");
        }
    }

    /**
     * A materialized view the dialect writes holds the rows of its query and is read back as a
     * materialized view with its query; dropped, it is gone. Before Oracle 23 the dialect has no
     * {@code IF [NOT] EXISTS}, and the statements with and without it are alike.
     */
    @Test
    void materialized_view_is_created_read_back_and_dropped() throws SQLException {
        String schema = CONTAINER.getUsername().toUpperCase();
        TableReference view = new TableReference(Optional.of(new SchemaReference(Optional.empty(), schema)),
                "MV_USERS", TableReference.TYPE_VIEW);
        String query = "SELECT id, name FROM \"" + schema + "\".\"USERS\" WHERE id <= 5";
        OracleMetadataProvider provider = new OracleMetadataProvider();
        String create = dialect.ddlGenerator().createMaterializedView(view, query, false).orElseThrow();
        String createIfNotExists = dialect.ddlGenerator().createMaterializedView(view, query, true).orElseThrow();
        String drop = dialect.ddlGenerator().dropMaterializedView(view, false).orElseThrow();
        String dropIfExists = dialect.ddlGenerator().dropMaterializedView(view, true).orElseThrow();
        try (Statement s = conn.createStatement()) {
            s.execute(create);
            if (!createIfNotExists.equals(create)) {
                s.execute(createIfNotExists);
            }
            assertFirstIntEquals(conn, "SELECT COUNT(*) FROM \"" + schema + "\".\"MV_USERS\"", 5);
            assertThat(provider.getAllMaterializedViews(conn, null, schema))
                    .filteredOn(mv -> mv.view().name().equals("MV_USERS")).singleElement()
                    .satisfies(mv -> assertThat(mv.viewBody().orElseThrow()).containsIgnoringCase("users"));

            s.execute(drop);
            if (!dropIfExists.equals(drop)) {
                s.execute(dropIfExists);
            }
            assertThat(provider.getAllMaterializedViews(conn, null, schema))
                    .noneMatch(mv -> mv.view().name().equals("MV_USERS"));
        }
    }

    /** An index the dialect writes with a descending column is read back with the order of each column. */
    @Test
    void descending_index_column_is_created_and_read_back() throws SQLException {
        IndexRoundTrip.assertDescendingIndexRoundTrip(conn, dialect.ddlGenerator(), new OracleMetadataProvider(), users,
                "IX_USERS_DESC", "NAME", "ID");
    }

    /**
     * A foreign key the dialect writes as checked at the end of the transaction lets a child come
     * before its parent within one, and is read back as initially deferred.
     */
    @Test
    void deferrable_foreign_key_is_checked_at_commit_and_read_back() throws SQLException {
        String schema = CONTAINER.getUsername().toUpperCase();
        TableReference parent = new TableReference(Optional.of(new SchemaReference(Optional.empty(), schema)), "DP",
                TableReference.TYPE_TABLE);
        TableReference child = new TableReference(Optional.of(new SchemaReference(Optional.empty(), schema)), "DC",
                TableReference.TYPE_TABLE);
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE \"" + schema + "\".\"DP\" (ID NUMBER PRIMARY KEY)");
            s.execute("CREATE TABLE \"" + schema + "\".\"DC\" (ID NUMBER PRIMARY KEY, DP_ID NUMBER)");
            s.execute(dialect.ddlGenerator().addForeignKeyConstraint(child, "FK_DC_DP", List.of("DP_ID"), parent,
                    List.of("ID"), null, null, Deferrability.INITIALLY_DEFERRED).orElseThrow());
            conn.setAutoCommit(false);
            s.execute("INSERT INTO \"" + schema + "\".\"DC\" VALUES (1, 7)");
            s.execute("INSERT INTO \"" + schema + "\".\"DP\" VALUES (7)");
            conn.commit();
        } finally {
            conn.setAutoCommit(true);
        }
        assertThat(new OracleMetadataProvider().getAllImportedKeys(conn, null, schema).orElseThrow())
                .filteredOn(key -> "FK_DC_DP".equals(key.name())).singleElement()
                .satisfies(key -> assertThat(key.deferrability()).isEqualTo(ImportedKey.Deferrability.INITIALLY_DEFERRED));
        try (Statement s = conn.createStatement()) {
            s.execute("DROP TABLE \"" + schema + "\".\"DC\"");
            s.execute("DROP TABLE \"" + schema + "\".\"DP\"");
        }
    }

    /**
     * The comments the dialect writes on a view and on one of its columns are read back as the
     * snapshot reads them, through the metadata provider or else the JDBC driver.
     */
    @Test
    void view_comments_are_written_and_read_back() throws SQLException {
        String schema = CONTAINER.getUsername().toUpperCase();
        TableReference view = new TableReference(Optional.of(new SchemaReference(Optional.empty(), schema)),
                "V_NOTED", TableReference.TYPE_VIEW);
        DatabaseServiceImpl service = new DatabaseServiceImpl();
        OracleMetadataProvider provider = new OracleMetadataProvider();
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE VIEW \"" + schema + "\".\"V_NOTED\" AS SELECT ID, NAME FROM \"" + schema + "\".\"USERS\"");
            s.execute(dialect.ddlGenerator().commentOnTable(view, "the view's comment").orElseThrow());
            s.execute(dialect.ddlGenerator().commentOnColumn(view, "NAME", "the column's comment", null)
                    .orElseThrow());
            assertThat(service.getTableDefinitions(conn, provider, null, schema, "V_NOTED", null))
                    .filteredOn(t -> t.table().name().equals("V_NOTED")).singleElement()
                    .satisfies(t -> assertThat(t.tableMetaData().remarks()).contains("the view's comment"));
            assertThat(service.getColumnDefinitions(conn, provider, null, schema, "V_NOTED", null))
                    .filteredOn(c -> c.column().table().map(TableReference::name).filter("V_NOTED"::equals).isPresent()
                            && c.column().name().equals("NAME"))
                    .singleElement()
                    .satisfies(c -> assertThat(c.columnMetaData().remarks()).contains("the column's comment"));
            s.execute(dialect.ddlGenerator().dropView(view, false));
        }
    }

    /**
     * A function and a procedure the dialect writes are created, the function is called, both are
     * read back with their parameters, language and body, and dropped by their signature.
     */
    @Test
    void routines_are_created_called_read_back_and_dropped() throws SQLException {
        String schema = CONTAINER.getUsername().toUpperCase();
        RoutineDefinition function = new RoutineDefinition(schema, "RT_ADD",
                List.of(RoutineParameter.in("A", "NUMBER"), RoutineParameter.in("B", "NUMBER")), "NUMBER", "plsql",
                "BEGIN RETURN A + B; END;");
        RoutineDefinition procedure = new RoutineDefinition(schema, "RT_BUMP",
                List.of(RoutineParameter.in("N", "NUMBER"), new RoutineParameter("TOTAL", ParameterMode.INOUT,
                        "NUMBER")),
                null, "plsql", "BEGIN TOTAL := TOTAL + N; END;");
        RoutineRoundTrip.assertRoutineRoundTrip(conn, dialect.ddlGenerator(), new OracleMetadataProvider(), function,
                procedure, "SELECT \"" + schema + "\".\"RT_ADD\"(2, 3) FROM DUAL", 5,
                List.of("n IN", "total INOUT"), "RETURN A + B", "TOTAL := TOTAL + N");
    }

    /** A role the dialect writes is created, again where it can say so, read back and dropped. */
    @Test
    void role_is_created_read_back_and_dropped() throws SQLException {
        try (Connection system = DriverManager.getConnection(CONTAINER.getJdbcUrl(), "system",
                CONTAINER.getPassword())) {
            UserRoundTrip.assertRoleRoundTrip(system, dialect.ddlGenerator(), new OracleMetadataProvider(),
                    DatabasePrincipal.KIND_ROLE, "rt_only_role");
        }
    }

    /**
     * A schema that holds a function and no table: the snapshot holds its function. The time the
     * snapshot takes is printed.
     */
    @Test
    void routines_of_a_schema_without_tables_are_in_the_snapshot() throws SQLException {
        Connection system = DriverManager.getConnection(CONTAINER.getJdbcUrl(), "system", CONTAINER.getPassword());
        try (Statement s = system.createStatement()) {
            s.execute("CREATE USER ONLY_ROUTINES NO AUTHENTICATION");
            s.execute("CREATE FUNCTION ONLY_ROUTINES.F_ALONE RETURN NUMBER AS BEGIN RETURN 1; END;");
        }
        long start = System.nanoTime();
        StructureInfo structure = new DatabaseServiceImpl().createMetaInfo(system, new OracleMetadataProvider()).structureInfo();
        System.out.printf("Oracle snapshot with its provider: %d ms, %d schemas, %d functions%n",
                (System.nanoTime() - start) / 1_000_000, structure.schemas().size(), structure.functions().size());
        assertThat(structure.functions()).anySatisfy(f -> {
            assertThat(f.reference().name()).isEqualToIgnoringCase("f_alone");
            assertThat(f.reference().schema()).map(SchemaReference::name)
                    .hasValueSatisfying(n -> assertThat(n).isEqualToIgnoringCase("only_routines"));
        });
        try (Statement s = system.createStatement()) {
            s.execute("DROP USER ONLY_ROUTINES CASCADE");
        }
        system.close();
    }
}
