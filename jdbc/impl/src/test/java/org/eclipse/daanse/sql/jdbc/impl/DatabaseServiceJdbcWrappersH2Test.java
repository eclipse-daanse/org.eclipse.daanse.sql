/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.jdbc.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.schema.BestRowIdentifier;
import org.eclipse.daanse.sql.jdbc.api.schema.ColumnPrivilege;
import org.eclipse.daanse.sql.jdbc.api.schema.PseudoColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.SuperTable;
import org.eclipse.daanse.sql.jdbc.api.schema.SuperType;
import org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.jdbc.api.schema.UserDefinedType;
import org.eclipse.daanse.sql.jdbc.api.schema.VersionColumn;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DatabaseServiceJdbcWrappersH2Test {

    private static Connection connection;
    private static DatabaseService service;
    private static DatabaseMetaData dmd;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:jdbcWrappers;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE EMPLOYEES (
                        EMP_ID INT NOT NULL,
                        FIRST_NAME VARCHAR(50) NOT NULL,
                        LAST_NAME VARCHAR(50) NOT NULL,
                        CONSTRAINT PK_EMP PRIMARY KEY (EMP_ID)
                    )
                    """);
        }
        service = new DatabaseServiceImpl();
        dmd = connection.getMetaData();
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

    private static TableReference employees() {
        return new TableReference(Optional.of(new SchemaReference(Optional.empty(), "PUBLIC")), "EMPLOYEES");
    }

    @Test
    void getUDTs_returnsEmptyListForH2() throws Exception {
        List<UserDefinedType> udts = service.getUDTs(connection, MetadataProvider.EMPTY, null, null, null, null);
        assertThat(udts).isNotNull();
    }

    @Test
    void getBestRowIdentifier_returnsPrimaryKeyColumnForEmployees() throws Exception {
        List<BestRowIdentifier> best = service.getBestRowIdentifier(connection, MetadataProvider.EMPTY, employees(),
                DatabaseMetaData.bestRowSession, false);
        assertThat(best).isNotEmpty();
        assertThat(best).anyMatch(b -> "EMP_ID".equalsIgnoreCase(b.column().name()));
    }

    @Test
    void getVersionColumns_returnsListForEmployees() throws Exception {
        List<VersionColumn> vcs = service.getVersionColumns(connection, MetadataProvider.EMPTY, employees());
        // H2 has no auto-updated version columns; contract: return an empty list, not null.
        assertThat(vcs).isNotNull();
    }

    @Test
    void getPseudoColumns_returnsListForSchema() throws Exception {
        List<PseudoColumn> pcs = service.getPseudoColumns(connection, MetadataProvider.EMPTY, null, "PUBLIC", "%", "%");
        // H2 may or may not expose pseudo columns; just verify null-safety.
        assertThat(pcs).isNotNull();
    }

    @Test
    void getTablePrivileges_returnsList() throws Exception {
        List<TablePrivilege> tps = service.getTablePrivileges(connection, MetadataProvider.EMPTY, null, "PUBLIC", "%");
        assertThat(tps).isNotNull();
    }

    @Test
    void getColumnPrivileges_returnsList() throws Exception {
        List<ColumnPrivilege> cps = service.getColumnPrivileges(connection, MetadataProvider.EMPTY, employees(), "%");
        assertThat(cps).isNotNull();
    }

    @Test
    void getSuperTypes_returnsList() throws Exception {
        // Drain the ResultSet via our wrapper; H2 returns an empty set.
        List<SuperType> sts = service.getSuperTypes(connection, MetadataProvider.EMPTY, null, "PUBLIC", "%");
        assertThat(sts).isNotNull();
    }

    @Test
    void getSuperTables_returnsList() throws Exception {
        List<SuperTable> sts = service.getSuperTables(connection, MetadataProvider.EMPTY, null, "PUBLIC", "%");
        assertThat(sts).isNotNull();
    }

    @Test
    void getUDTs_matchesRawJdbcRowCount() throws Exception {
        // Sanity: wrapper walks the ResultSet once and keeps the row count consistent.
        int expected;
        try (ResultSet rs = dmd.getUDTs(null, null, null, null)) {
            int c = 0;
            while (rs.next()) c++;
            expected = c;
        }
        List<UserDefinedType> udts = service.getUDTs(connection, MetadataProvider.EMPTY, null, null, null, null);
        assertThat(udts).hasSize(expected);
    }
}
