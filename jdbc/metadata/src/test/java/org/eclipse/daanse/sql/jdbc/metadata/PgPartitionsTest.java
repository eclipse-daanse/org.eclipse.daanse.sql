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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import org.eclipse.daanse.sql.jdbc.api.schema.Partition;
import org.eclipse.daanse.sql.jdbc.api.schema.PartitionMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class PgPartitionsTest {

    private static final String DATABASE = "test";
    private static final String USER = "postgres";
    private static final String PASSWORD = "secret";
    private static final String SCHEMA = "public";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:15")
            .withEnv("POSTGRES_PASSWORD", PASSWORD).withEnv("POSTGRES_DB", DATABASE).withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private static Connection connection;
    private static PostgreSqlMetadataProvider provider;

    @BeforeAll
    static void setUp() throws Exception {
        POSTGRES.start();
        String jdbcUrl = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/"
                + DATABASE;
        Class.forName("org.postgresql.Driver");
        connection = DriverManager.getConnection(jdbcUrl, USER, PASSWORD);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE sales_by_year (
                        sale_id  BIGINT NOT NULL,
                        sale_date DATE   NOT NULL,
                        amount    NUMERIC(10,2) NOT NULL
                    ) PARTITION BY RANGE (sale_date)
                    """);
            stmt.execute("CREATE TABLE sales_2022 PARTITION OF sales_by_year "
                    + "FOR VALUES FROM ('2022-01-01') TO ('2023-01-01')");
            stmt.execute("CREATE TABLE sales_2023 PARTITION OF sales_by_year "
                    + "FOR VALUES FROM ('2023-01-01') TO ('2024-01-01')");
            stmt.execute("CREATE TABLE sales_default PARTITION OF sales_by_year DEFAULT");

            stmt.execute("""
                    CREATE TABLE customers_by_region (
                        cust_id BIGINT NOT NULL,
                        region  TEXT   NOT NULL
                    ) PARTITION BY LIST (region)
                    """);
            stmt.execute("CREATE TABLE customers_eu PARTITION OF customers_by_region FOR VALUES IN ('EU')");
            stmt.execute("CREATE TABLE customers_us PARTITION OF customers_by_region FOR VALUES IN ('US')");
        }
        provider = new PostgreSqlMetadataProvider();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        POSTGRES.stop();
    }

    @Test
    void getAllPartitions_findsBothPartitionedTables() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, SCHEMA);
        // 3 sales partitions + 2 customers partitions
        assertThat(partitions).hasSize(5);
    }

    @Test
    void getAllPartitions_rangeAndListMethodsBothReported() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, SCHEMA);
        assertThat(partitions).filteredOn(p -> "sales_by_year".equals(p.table().name()))
                .allMatch(p -> p.method() == PartitionMethod.RANGE);
        assertThat(partitions).filteredOn(p -> "customers_by_region".equals(p.table().name()))
                .allMatch(p -> p.method() == PartitionMethod.LIST);
    }

    @Test
    void getAllPartitions_boundsCarryFromTo() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, SCHEMA);
        Partition sales2022 = partitions.stream().filter(p -> "sales_2022".equals(p.name())).findFirst().orElseThrow();
        assertThat(sales2022.description()).isPresent();
        assertThat(sales2022.description().get()).contains("2022-01-01").contains("2023-01-01");
    }

    @Test
    void getAllPartitions_listPartitionDescription() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, SCHEMA);
        Partition eu = partitions.stream().filter(p -> "customers_eu".equals(p.name())).findFirst().orElseThrow();
        assertThat(eu.description()).isPresent();
        assertThat(eu.description().get()).contains("EU");
    }

    @Test
    void getAllPartitions_expressionExtractsPartitionKey() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, SCHEMA);
        Partition any = partitions.stream().filter(p -> "sales_by_year".equals(p.table().name())).findFirst()
                .orElseThrow();
        assertThat(any.expression()).contains("sale_date");
    }

    @Test
    void getPartitions_perTableFilters() throws SQLException {
        List<Partition> sales = provider.getPartitions(connection, null, SCHEMA, "sales_by_year");
        assertThat(sales).hasSize(3);
        List<Partition> cust = provider.getPartitions(connection, null, SCHEMA, "customers_by_region");
        assertThat(cust).hasSize(2);
    }
}
