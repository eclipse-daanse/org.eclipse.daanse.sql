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
import java.util.List;

import org.eclipse.daanse.sql.jdbc.api.schema.Partition;
import org.eclipse.daanse.sql.jdbc.api.schema.PartitionMethod;
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
class MsSqlPartitionsTest {

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
            // Partition function over INT (year)
            stmt.execute("CREATE PARTITION FUNCTION pf_sales_year (INT) AS RANGE RIGHT FOR VALUES (2023, 2024, 2025)");
            stmt.execute("CREATE PARTITION SCHEME ps_sales_year AS PARTITION pf_sales_year ALL TO ([PRIMARY])");
            stmt.execute("""
                    CREATE TABLE sales_by_year (
                        sale_id INT NOT NULL,
                        sale_year INT NOT NULL,
                        amount DECIMAL(10,2) NOT NULL,
                        CONSTRAINT pk_sales_by_year PRIMARY KEY CLUSTERED (sale_year, sale_id)
                    ) ON ps_sales_year(sale_year)
                    """);
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
    void getAllPartitions_returnsFourPartitions() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, SCHEMA);
        // RANGE RIGHT FOR VALUES (2023, 2024, 2025) → 4 partitions
        assertThat(partitions).hasSize(4);
        assertThat(partitions).allMatch(p -> p.method() == PartitionMethod.RANGE);
    }

    @Test
    void getAllPartitions_partitionColumnIsSaleYear() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, SCHEMA);
        assertThat(partitions)
                .allMatch(p -> p.expression().isPresent() && "sale_year".equalsIgnoreCase(p.expression().get()));
    }

    @Test
    void getAllPartitions_ordinalPositionsAreSequential() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, SCHEMA);
        assertThat(partitions).extracting(p -> p.ordinalPosition().orElseThrow()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void getPartitions_perTableMatchesAll() throws SQLException {
        List<Partition> all = provider.getAllPartitions(connection, null, SCHEMA);
        List<Partition> sales = provider.getPartitions(connection, null, SCHEMA, "sales_by_year");
        assertThat(sales).hasSameSizeAs(all);
    }
}
