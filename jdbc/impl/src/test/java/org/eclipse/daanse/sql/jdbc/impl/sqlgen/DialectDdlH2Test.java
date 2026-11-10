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
package org.eclipse.daanse.sql.jdbc.impl.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DialectDdlH2Test {

    private static Connection connection;
    private static H2Dialect dialect;
    private static MetaInfo metaInfo;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:cwmToSql;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE EMPLOYEES (
                        EMP_ID INT NOT NULL PRIMARY KEY,
                        FIRST_NAME VARCHAR(50) NOT NULL,
                        SALARY DECIMAL(10,2)
                    )
                    """);
        }
        dialect = new H2Dialect(org.eclipse.daanse.sql.dialect.api.DialectInitData.fromConnection(connection));
        metaInfo = new DatabaseServiceImpl().createMetaInfo(connection, new org.eclipse.daanse.sql.jdbc.metadata.H2MetadataProvider());
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP ALL OBJECTS");
            }
            connection.close();
        }
    }

    private static TableDefinition employees() {
        return metaInfo.structureInfo().tables().stream()
                .filter(td -> "EMPLOYEES".equalsIgnoreCase(td.table().name()))
                .findFirst().orElseThrow();
    }

    private static PrimaryKey employeesPk() {
        return metaInfo.structureInfo().primaryKeys().stream()
                .filter(pk -> "EMPLOYEES".equalsIgnoreCase(pk.table().name()))
                .findFirst().orElseThrow();
    }

    @Test
    void createTable_emitsQuotedIdentifiersAndTypes() {
        TableDefinition emp = employees();
        List<ColumnDefinition> cols = dialect.ddlGenerator().columnsOf(emp.table(), metaInfo.structureInfo().columns());
        String ddl = dialect.ddlGenerator().createTable(emp.table(), cols, employeesPk(), false);

        assertThat(ddl).startsWith("CREATE TABLE ");
        assertThat(ddl).contains("\"EMPLOYEES\"");
        assertThat(ddl).contains("\"EMP_ID\"");
        assertThat(ddl).contains("\"FIRST_NAME\"");
        assertThat(ddl).contains("NOT NULL");
        assertThat(ddl).contains("PRIMARY KEY (\"EMP_ID\")");
    }

    @Test
    void createTable_ifNotExists() {
        TableDefinition emp = employees();
        List<ColumnDefinition> cols = dialect.ddlGenerator().columnsOf(emp.table(), metaInfo.structureInfo().columns());
        String ddl = dialect.ddlGenerator().createTable(emp.table(), cols, null, true);
        assertThat(ddl).startsWith("CREATE TABLE IF NOT EXISTS ");
    }

    @Test
    void insertInto_parameterised() {
        TableDefinition emp = employees();
        List<ColumnDefinition> cols = dialect.ddlGenerator().columnsOf(emp.table(), metaInfo.structureInfo().columns());
        String sql = dialect.ddlGenerator().insertInto(emp.table(), cols);
        assertThat(sql).startsWith("INSERT INTO ");
        // Three columns → three placeholders.
        assertThat(sql).endsWith("VALUES (?, ?, ?)");
    }

    @Test
    void selectFrom_listsExplicitColumns() {
        TableDefinition emp = employees();
        List<ColumnDefinition> cols = dialect.ddlGenerator().columnsOf(emp.table(), metaInfo.structureInfo().columns());
        String sql = dialect.ddlGenerator().selectFrom(emp.table(), cols);
        assertThat(sql).startsWith("SELECT \"EMP_ID\"");
        assertThat(sql).contains("\"FIRST_NAME\"");
    }

    @Test
    void selectAll_noColumnList() {
        TableDefinition emp = employees();
        String sql = dialect.ddlGenerator().selectAll(emp.table());
        assertThat(sql).startsWith("SELECT * FROM ");
    }

    @Test
    void generatedDdlCanBeReplayedOnFreshDatabase_andInsertAndSelectWork() throws SQLException {
        TableDefinition emp = employees();
        List<ColumnDefinition> cols = dialect.ddlGenerator().columnsOf(emp.table(), metaInfo.structureInfo().columns());
        String createDdl = dialect.ddlGenerator().createTable(emp.table(), cols, employeesPk(), true);
        String insertDml = dialect.ddlGenerator().insertInto(emp.table(), cols);
        String selectDml = dialect.ddlGenerator().selectFrom(emp.table(), cols);

        // Use a fresh H2 DB and a fresh schema equivalent to PUBLIC so the quoted
        // identifiers resolve correctly.
        try (Connection replay = DriverManager.getConnection(
                "jdbc:h2:mem:cwmToSqlReplay;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement stmt = replay.createStatement()) {
                stmt.execute(createDdl);
            }
            try (PreparedStatement ps = replay.prepareStatement(insertDml)) {
                ps.setInt(1, 42);
                ps.setString(2, "Alice");
                ps.setBigDecimal(3, new java.math.BigDecimal("1000.00"));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = replay.prepareStatement(selectDml);
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(42);
                assertThat(rs.getString(2)).isEqualTo("Alice");
                assertThat(rs.next()).isFalse();
            }
        }
    }
}
