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
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.schema.Synonym;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@Testcontainers
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
@TestInstance(Lifecycle.PER_CLASS)
class OracleSynonymsTest {

    @Container
    @SuppressWarnings("resource")
    static final OracleContainer CONTAINER = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    private static final String SCHEMA = "TEST";

    private static Connection connection;
    private static OracleMetadataProvider provider;

    @BeforeAll
    void setUp() throws Exception {
        try (Connection system = DriverManager.getConnection(CONTAINER.getJdbcUrl(), "system",
                CONTAINER.getPassword()); Statement stmt = system.createStatement()) {
            stmt.execute("GRANT CREATE SYNONYM, CREATE PUBLIC SYNONYM, DROP PUBLIC SYNONYM, CREATE MATERIALIZED VIEW TO " + SCHEMA);
        }
        connection = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(),
                CONTAINER.getPassword());
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE customers (id NUMBER PRIMARY KEY, name VARCHAR2(100))");
            stmt.execute("CREATE MATERIALIZED VIEW customer_names AS SELECT id, name FROM customers");
            stmt.execute("CREATE FUNCTION f_one RETURN NUMBER IS BEGIN RETURN 1; END;");
            stmt.execute("CREATE SYNONYM syn_customers FOR customers");
            stmt.execute("CREATE SYNONYM syn_chain FOR syn_customers");
            stmt.execute("CREATE SYNONYM syn_f_one FOR f_one");
            stmt.execute("CREATE SYNONYM syn_customer_names FOR customer_names");
            stmt.execute("CREATE SYNONYM syn_remote FOR customers@nolink");
            stmt.execute("CREATE PUBLIC SYNONYM pub_customers FOR " + SCHEMA + ".customers");
        }
        provider = new OracleMetadataProvider();
    }

    @AfterAll
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP PUBLIC SYNONYM pub_customers");
            }
            connection.close();
        }
    }

    @Test
    void privateSynonymResolvesToTable() throws SQLException {
        Synonym syn = find("SYN_CUSTOMERS");
        assertThat(syn.schema()).map(SchemaReference::name).contains(SCHEMA);
        assertThat(syn.targetSchema()).map(SchemaReference::name).contains(SCHEMA);
        assertThat(syn.targetName()).isEqualTo("CUSTOMERS");
        assertThat(syn.targetObjectType()).contains("TABLE");
        assertThat(syn.isPublic()).isFalse();
        assertThat(syn.dbLink()).isEmpty();
    }

    @Test
    void chainedSynonymPointsToSynonym() throws SQLException {
        Synonym syn = find("SYN_CHAIN");
        assertThat(syn.targetName()).isEqualTo("SYN_CUSTOMERS");
        assertThat(syn.targetObjectType()).contains("SYNONYM");
    }

    @Test
    void synonymToFunction() throws SQLException {
        assertThat(find("SYN_F_ONE").targetObjectType()).contains("FUNCTION");
    }

    @Test
    void synonymToMaterializedViewIsReportedOnce() throws SQLException {
        List<Synonym> synonyms = provider.getAllSynonyms(connection, null, SCHEMA);
        assertThat(synonyms).filteredOn(s -> s.name().equals("SYN_CUSTOMER_NAMES")).hasSize(1);
        assertThat(find("SYN_CUSTOMER_NAMES").targetObjectType()).contains("MATERIALIZED VIEW");
    }

    @Test
    void remoteSynonymHasDbLinkAndNoType() throws SQLException {
        Synonym syn = find("SYN_REMOTE");
        assertThat(syn.dbLink()).hasValueSatisfying(link -> assertThat(link).startsWithIgnoringCase("NOLINK"));
        assertThat(syn.targetObjectType()).isEmpty();
    }

    @Test
    void publicSynonymsOnlyThosePointingIntoSchema() throws SQLException {
        List<Synonym> publics = provider.getAllSynonyms(connection, null, SCHEMA).stream()
                .filter(Synonym::isPublic).toList();
        assertThat(publics).extracting(Synonym::name).containsExactly("PUB_CUSTOMERS");
        Synonym pub = publics.get(0);
        assertThat(pub.schema()).map(SchemaReference::name).contains("PUBLIC");
        assertThat(pub.targetObjectType()).contains("TABLE");
    }

    @Test
    void defaultSchemaIsConnectionUser() throws SQLException {
        assertThat(provider.getAllSynonyms(connection, null, null)).extracting(Synonym::name)
                .contains("SYN_CUSTOMERS", "PUB_CUSTOMERS");
    }

    private Synonym find(String name) throws SQLException {
        Optional<Synonym> syn = provider.getAllSynonyms(connection, null, SCHEMA).stream()
                .filter(s -> s.name().equals(name)).findFirst();
        assertThat(syn).as("synonym %s", name).isPresent();
        return syn.get();
    }
}
