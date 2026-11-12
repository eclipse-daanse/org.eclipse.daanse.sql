/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mssqlserver;

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
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.jdbc.metadata.MicrosoftSqlServerMetadataProvider;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
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
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.DatabasePrincipal;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
@TestInstance(Lifecycle.PER_CLASS)
class MicrosoftSqlServerGeneratorsRoundTripTest {

    @Container
    @SuppressWarnings("resource")
    static final MSSQLServerContainer<?> CONTAINER = new MSSQLServerContainer<>(
            "mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    private Connection conn;
    private MicrosoftSqlServerDialect dialect;
    private TableReference users;

    @BeforeAll
    void setUp() throws Exception {
        conn = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
        dialect = new MicrosoftSqlServerDialect(DialectInitData.fromConnection(conn));
        users = new TableReference(Optional.of(new SchemaReference(Optional.empty(), "dbo")), "users",
                TableReference.TYPE_TABLE);
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE [dbo].[users] (id INT PRIMARY KEY, name VARCHAR(50))");
            s.execute("INSERT INTO [dbo].[users] VALUES (1,'a'),(2,'b'),(3,'c'),(4,'d'),(5,'e')");
        }
    }

    @AfterAll
    void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed())
            conn.close();
    }

    @Test
    void pagination_offset_fetch_next_executes() throws SQLException {
        String tail = dialect.paginationGenerator().paginate(OptionalLong.of(2), OptionalLong.of(1));
        assertThat(tail).contains("OFFSET 1 ROWS").contains("FETCH NEXT 2 ROWS ONLY");
        assertSelectIdRowCountAndFirst(conn, "SELECT id FROM [dbo].[users] ORDER BY id" + tail, 2, 2);
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
        assertSelectIdRowCountAndFirstWithParams(conn, "SELECT id FROM [dbo].[users] ORDER BY id " + preparedPaging.clause(), List.of(1, 2), 2, 2);
    }

    @Test
    void merge_upsert_inserts_then_updates() throws SQLException {
        MergeGenerator.UpsertSpec spec = new MergeGenerator.UpsertSpec(users, List.of("id"), List.of("id", "name"),
                List.of("name"));
        // T-SQL MERGE statements need a trailing semicolon.
        String sql1 = dialect.mergeGenerator().upsert(spec, List.of("10", "'first'")).orElseThrow();
        assertExecuteUpdateAffected(conn, sql1 + ";", 1, "merge: insert path");
        String sql2 = dialect.mergeGenerator().upsert(spec, List.of("10", "'second'")).orElseThrow();
        assertExecuteUpdateAffected(conn, sql2 + ";", 1, "merge: update path");
        assertFirstStringEquals(conn, "SELECT name FROM [dbo].[users] WHERE id = 10", "second");
    }

    @Test
    void output_clause_returns_inserted_id() throws SQLException {
        // SQL Server's RETURNING-equivalent is OUTPUT INSERTED.col.
        String returning = dialect.returningGenerator().returning(List.of("id")).orElseThrow();
        assertThat(returning).contains("OUTPUT INSERTED");
        // OUTPUT goes between the table and VALUES, not at the end.
        assertFirstIntEquals(conn, "INSERT INTO [dbo].[users] (id, name)" + returning + " VALUES (99, 'returned')", 99);
    }

    /**
     * The comments the dialect writes on a table, a column and a view are those the metadata
     * provider reads back. A comment on the primary key's index, an extended property whose
     * minor_id is the index_id 1 as the first column's column_id is 1, is no comment on that column.
     */
    @Test
    void comments_written_are_read_back() throws SQLException {
        TableReference noted = new TableReference(Optional.of(new SchemaReference(Optional.empty(), "dbo")),
                "noted", TableReference.TYPE_TABLE);
        TableReference view = new TableReference(Optional.of(new SchemaReference(Optional.empty(), "dbo")),
                "v_noted", TableReference.TYPE_VIEW);
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE [dbo].[noted] (id INT CONSTRAINT pk_noted PRIMARY KEY, label VARCHAR(20))");
            s.execute("CREATE VIEW [dbo].[v_noted] AS SELECT id, label FROM [dbo].[noted]");
            s.execute("EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'the key',"
                    + " @level0type = N'SCHEMA', @level0name = N'dbo', @level1type = N'TABLE',"
                    + " @level1name = N'noted', @level2type = N'INDEX', @level2name = N'pk_noted'");
            s.execute(dialect.commentOnTable(noted, "it's noted").orElseThrow());
            s.execute(dialect.commentOnColumn(noted, "id", "the number", null).orElseThrow());
            s.execute(dialect.commentOnColumn(noted, "id", "the number, again", null).orElseThrow());
            s.execute(dialect.commentOnColumn(noted, "label", "to be removed", null).orElseThrow());
            s.execute(dialect.commentOnColumn(noted, "label", null, null).orElseThrow());
            s.execute(dialect.commentOnTable(view, "noted, as a view").orElseThrow());
        }

        MicrosoftSqlServerMetadataProvider provider = new MicrosoftSqlServerMetadataProvider();
        List<TableDefinition> tables = provider.getAllTableDefinitions(conn, null, "dbo", null, null).orElseThrow();
        List<ColumnDefinition> columns = provider.getAllColumnDefinitions(conn, null, "dbo", "noted", null)
                .orElseThrow();

        assertThat(tables).filteredOn(t -> t.table().name().equals("noted")).singleElement()
                .satisfies(t -> assertThat(t.tableMetaData().remarks()).contains("it's noted"));
        assertThat(tables).filteredOn(t -> t.table().name().equals("v_noted")).singleElement()
                .satisfies(t -> assertThat(t.tableMetaData().remarks()).contains("noted, as a view"));
        assertThat(columns).filteredOn(c -> c.column().name().equals("id")).singleElement()
                .satisfies(c -> assertThat(c.columnMetaData().remarks()).contains("the number, again"));
        assertThat(columns).filteredOn(c -> c.column().name().equals("label")).singleElement()
                .satisfies(c -> assertThat(c.columnMetaData().remarks()).isEmpty());
    }

    /**
     * A user created without credentials takes a role and a privilege, is read back as a user
     * next to the role, and is dropped again.
     */
    @Test
    void user_is_created_given_a_role_and_dropped() throws SQLException {
        UserRoundTrip.assertUserRoundTrip(conn, dialect.ddlGenerator(), new MicrosoftSqlServerMetadataProvider(),
                DatabasePrincipal.KIND_USER, users, "rt_user", "rt_role");
    }

    /**
     * A unique index over the rows of a condition holds among those rows only, and the metadata
     * provider reads its condition back.
     */
    @Test
    void filtered_index_is_created_and_read_back() throws SQLException {
        String create = dialect.ddlGenerator().createIndex("ux_users_big", users, List.of("name"), true, "id > 100",
                false).orElseThrow();
        try (Statement s = conn.createStatement()) {
            s.execute(create);
            s.execute("INSERT INTO [dbo].[users] VALUES (31, 'same'), (32, 'same'), (301, 'same')");
            assertThatThrownBy(() -> s.execute("INSERT INTO [dbo].[users] VALUES (302, 'same')"))
                    .isInstanceOf(SQLException.class);
            assertThat(new MicrosoftSqlServerMetadataProvider().getAllIndexInfo(conn, null, "dbo").orElseThrow())
                    .flatExtracting(IndexInfo::indexInfoItems)
                    .filteredOn(item -> item.indexName().filter("ux_users_big"::equals).isPresent()).singleElement()
                    .satisfies(item -> assertThat(item.filterCondition().orElseThrow()).contains("[id]>(100)"));
            s.execute(dialect.ddlGenerator().dropIndex("ux_users_big", users, false));
            s.execute("DELETE FROM [dbo].[users] WHERE name = 'same'");
        }
    }

    /** An index the dialect writes with a descending column is read back with the order of each column. */
    @Test
    void descending_index_column_is_created_and_read_back() throws SQLException {
        IndexRoundTrip.assertDescendingIndexRoundTrip(conn, dialect.ddlGenerator(), new MicrosoftSqlServerMetadataProvider(),
                users, "ix_users_desc", "name", "id");
    }

    /**
     * The comments the dialect writes on a view and on one of its columns are read back as the
     * snapshot reads them, through the metadata provider or else the JDBC driver.
     */
    @Test
    void view_comments_are_written_and_read_back() throws SQLException {
        TableReference view = new TableReference(Optional.of(new SchemaReference(Optional.empty(), "dbo")),
                "v_noted_users", TableReference.TYPE_VIEW);
        DatabaseServiceImpl service = new DatabaseServiceImpl();
        MicrosoftSqlServerMetadataProvider provider = new MicrosoftSqlServerMetadataProvider();
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE VIEW [dbo].[v_noted_users] AS SELECT id, name FROM [dbo].[users]");
            s.execute(dialect.ddlGenerator().commentOnTable(view, "the view's comment").orElseThrow());
            s.execute(dialect.ddlGenerator().commentOnColumn(view, "name", "the column's comment", null)
                    .orElseThrow());
            assertThat(service.getTableDefinitions(conn, provider, null, "dbo", "v_noted_users", null))
                    .filteredOn(t -> t.table().name().equals("v_noted_users")).singleElement()
                    .satisfies(t -> assertThat(t.tableMetaData().remarks()).contains("the view's comment"));
            assertThat(service.getColumnDefinitions(conn, provider, null, "dbo", "v_noted_users", null))
                    .filteredOn(c -> c.column().table().map(TableReference::name).filter("v_noted_users"::equals).isPresent()
                            && c.column().name().equals("name"))
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
        RoutineDefinition function = new RoutineDefinition("dbo", "rt_add",
                List.of(RoutineParameter.in("a", "INT"), RoutineParameter.in("b", "INT")), "INT", "tsql",
                "BEGIN RETURN @a + @b END");
        RoutineDefinition procedure = new RoutineDefinition("dbo", "rt_bump",
                List.of(RoutineParameter.in("n", "INT"), new RoutineParameter("total", ParameterMode.INOUT, "INT")),
                null, "tsql", "BEGIN SET @total = @total + @n END");
        RoutineRoundTrip.assertRoutineRoundTrip(conn, dialect.ddlGenerator(), new MicrosoftSqlServerMetadataProvider(),
                function, procedure, "SELECT [dbo].[rt_add](2, 3)", 5, List.of("n IN", "total OUT"), "@a + @b",
                "@total = @total + @n");
    }

    /** A role the dialect writes is created, again where it can say so, read back and dropped. */
    @Test
    void role_is_created_read_back_and_dropped() throws SQLException {
        UserRoundTrip.assertRoleRoundTrip(conn, dialect.ddlGenerator(), new MicrosoftSqlServerMetadataProvider(),
                DatabasePrincipal.KIND_ROLE, "rt_only_role");
    }
}
