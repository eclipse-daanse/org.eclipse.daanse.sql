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
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.schema.Synonym;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class H2SynonymsTest {

    private static final String SCHEMA = "PUBLIC";

    private static Connection connection;
    private static H2MetadataProvider provider;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:h2SynonymsTest;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE CUSTOMERS (ID INT PRIMARY KEY, NAME VARCHAR(100))");
            stmt.execute("CREATE VIEW V_CUSTOMERS AS SELECT ID, NAME FROM CUSTOMERS");
            stmt.execute("CREATE SCHEMA OTHER");
            stmt.execute("CREATE TABLE OTHER.ORDERS (ID INT PRIMARY KEY)");
            stmt.execute("CREATE SYNONYM SYN_CUSTOMERS FOR CUSTOMERS");
            stmt.execute("CREATE SYNONYM SYN_VIEW FOR V_CUSTOMERS");
            stmt.execute("CREATE SYNONYM SYN_ORDERS FOR OTHER.ORDERS");
            stmt.execute("CREATE SYNONYM OTHER.SYN_BACK FOR PUBLIC.CUSTOMERS");
        }
        provider = new H2MetadataProvider();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void synonymToTable() throws SQLException {
        Synonym syn = find(SCHEMA, "SYN_CUSTOMERS");
        assertThat(syn.schema()).map(SchemaReference::name).contains(SCHEMA);
        assertThat(syn.targetSchema()).map(SchemaReference::name).contains(SCHEMA);
        assertThat(syn.targetName()).isEqualTo("CUSTOMERS");
        assertThat(syn.targetObjectType()).contains("BASE TABLE");
        assertThat(syn.dbLink()).isEmpty();
        assertThat(syn.isPublic()).isFalse();
    }

    @Test
    void synonymToView() throws SQLException {
        Synonym syn = find(SCHEMA, "SYN_VIEW");
        assertThat(syn.targetName()).isEqualTo("V_CUSTOMERS");
        assertThat(syn.targetObjectType()).contains("VIEW");
    }

    @Test
    void synonymIntoOtherSchema() throws SQLException {
        Synonym syn = find(SCHEMA, "SYN_ORDERS");
        assertThat(syn.targetSchema()).map(SchemaReference::name).contains("OTHER");
        assertThat(syn.targetName()).isEqualTo("ORDERS");
        assertThat(syn.targetObjectType()).contains("BASE TABLE");
    }

    @Test
    void onlySynonymsOfRequestedSchema() throws SQLException {
        assertThat(provider.getAllSynonyms(connection, null, SCHEMA)).extracting(Synonym::name)
                .containsExactly("SYN_CUSTOMERS", "SYN_ORDERS", "SYN_VIEW");
        assertThat(provider.getAllSynonyms(connection, null, "OTHER")).extracting(Synonym::name)
                .containsExactly("SYN_BACK");
    }

    @Test
    void defaultSchemaIsPublic() throws SQLException {
        assertThat(provider.getAllSynonyms(connection, null, null)).extracting(Synonym::name)
                .containsExactly("SYN_CUSTOMERS", "SYN_ORDERS", "SYN_VIEW");
    }

    @Test
    void schemaWithoutSynonyms() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS EMPTY_SCHEMA");
        }
        assertThat(provider.getAllSynonyms(connection, null, "EMPTY_SCHEMA")).isEmpty();
    }

    private static Synonym find(String schema, String name) throws SQLException {
        Optional<Synonym> syn = provider.getAllSynonyms(connection, null, schema).stream()
                .filter(s -> s.name().equals(name)).findFirst();
        assertThat(syn).as("synonym %s.%s", schema, name).isPresent();
        return syn.get();
    }
}
