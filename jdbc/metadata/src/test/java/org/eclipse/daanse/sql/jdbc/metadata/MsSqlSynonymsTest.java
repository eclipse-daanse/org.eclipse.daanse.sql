/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/
package org.eclipse.daanse.sql.jdbc.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.schema.Synonym;
import org.eclipse.daanse.sql.model.schema.CatalogReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
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
class MsSqlSynonymsTest {

    @Container
    @SuppressWarnings("resource")
    static final MSSQLServerContainer<?> CONTAINER = new MSSQLServerContainer<>(
            "mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    private static final String SCHEMA = "dbo";

    private static Connection connection;
    private static MicrosoftSqlServerMetadataProvider provider;

    @BeforeAll
    void setUp() throws Exception {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        connection = java.sql.DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(),
                CONTAINER.getPassword());
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE customers (id INT PRIMARY KEY, name NVARCHAR(100))");
            stmt.execute("CREATE SYNONYM syn_customers FOR dbo.customers");
            stmt.execute("CREATE SYNONYM syn_chain FOR [dbo].[syn_customers]");
            stmt.execute("CREATE SYNONYM syn_other_db FOR otherdb.sales.orders");
            stmt.execute("CREATE SYNONYM syn_linked FOR remotesrv.otherdb.sales.orders");
        }
        provider = new MicrosoftSqlServerMetadataProvider();
    }

    @AfterAll
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void localSynonymResolvesToTable() throws SQLException {
        Synonym syn = find("syn_customers");
        assertThat(syn.schema()).map(SchemaReference::name).contains(SCHEMA);
        assertThat(syn.targetSchema()).map(SchemaReference::name).contains("dbo");
        assertThat(syn.targetName()).isEqualTo("customers");
        assertThat(syn.targetObjectType()).contains("USER_TABLE");
        assertThat(syn.dbLink()).isEmpty();
        assertThat(syn.isPublic()).isFalse();
    }

    @Test
    void chainedSynonymPointsToSynonym() throws SQLException {
        Synonym syn = find("syn_chain");
        assertThat(syn.targetName()).isEqualTo("syn_customers");
        assertThat(syn.targetObjectType()).contains("SYNONYM");
    }

    @Test
    void otherDatabaseTargetCarriesCatalogAndNoType() throws SQLException {
        Synonym syn = find("syn_other_db");
        assertThat(syn.targetSchema()).map(SchemaReference::name).contains("sales");
        assertThat(syn.targetSchema()).flatMap(SchemaReference::catalog).map(CatalogReference::name)
                .contains("otherdb");
        assertThat(syn.targetName()).isEqualTo("orders");
        assertThat(syn.targetObjectType()).isEmpty();
    }

    @Test
    void linkedServerTargetReportedAsDbLink() throws SQLException {
        Synonym syn = find("syn_linked");
        assertThat(syn.dbLink()).contains("remotesrv");
        assertThat(syn.targetObjectType()).isEmpty();
    }

    private Synonym find(String name) throws SQLException {
        Optional<Synonym> syn = provider.getAllSynonyms(connection, null, SCHEMA).stream()
                .filter(s -> s.name().equals(name)).findFirst();
        assertThat(syn).as("synonym %s", name).isPresent();
        return syn.get();
    }
}
