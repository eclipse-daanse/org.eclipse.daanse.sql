/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/
package org.eclipse.daanse.sql.dialect.db.mssqlserver.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.SynonymDefinition;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The SQL Server dialect's synonym DDL, executed against a live SQL Server. */
@Testcontainers
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
@TestInstance(Lifecycle.PER_CLASS)
class MssqlSynonymDdlRoundTripTest {

    @Container
    @SuppressWarnings("resource")
    static final MSSQLServerContainer<?> CONTAINER = new MSSQLServerContainer<>(
            "mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    private final MicrosoftSqlServerDialect dialect = new MicrosoftSqlServerDialect();
    private Connection connection;

    @BeforeAll
    void setUp() throws Exception {
        connection = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(),
                CONTAINER.getPassword());
        execute("CREATE TABLE dbo.employees (id INT PRIMARY KEY, name NVARCHAR(50))");
        execute("INSERT INTO dbo.employees VALUES (1, 'Ann')");
    }

    @AfterAll
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed())
            connection.close();
    }

    @Test
    void localSynonymCreateQueryDrop() throws Exception {
        execute(dialect.createSynonym(SynonymDefinition.of("dbo", "emp", "dbo", "employees"), false).orElseThrow());
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT name FROM dbo.emp")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("Ann");
        }
        execute(dialect.dropSynonym("dbo", "emp", false, true).orElseThrow());
        execute(dialect.dropSynonym("dbo", "emp", false, true).orElseThrow());
        assertThat(synonymCount("emp")).isZero();
    }

    @Test
    void otherDatabaseAndLinkedServerTargetsAreAccepted() throws Exception {
        // SQL Server resolves the base object only on use, not when the synonym is created.
        execute(dialect.createSynonym(
                new SynonymDefinition("dbo", "ord_db", "otherdb", "sales", "orders", null, false), false)
                .orElseThrow());
        execute(dialect.createSynonym(
                new SynonymDefinition("dbo", "ord_srv", null, "sales", "orders", "remotesrv", false), false)
                .orElseThrow());
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT name, base_object_name FROM sys.synonyms WHERE name LIKE 'ord%' ORDER BY name")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(2)).isEqualTo("[otherdb].[sales].[orders]");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(2)).isEqualTo("[remotesrv]..[sales].[orders]");
        }
        execute(dialect.dropSynonym("dbo", "ord_db", false, false).orElseThrow());
        execute(dialect.dropSynonym("dbo", "ord_srv", false, false).orElseThrow());
    }

    private void execute(String sql) throws Exception {
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
        }
    }

    private int synonymCount(String name) throws Exception {
        try (Statement s = connection.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM sys.synonyms WHERE name = '" + name + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
