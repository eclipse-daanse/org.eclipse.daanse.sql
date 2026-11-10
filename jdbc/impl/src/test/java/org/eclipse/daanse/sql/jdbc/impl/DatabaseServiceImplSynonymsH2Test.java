/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/
package org.eclipse.daanse.sql.jdbc.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.Synonym;
import org.eclipse.daanse.sql.jdbc.metadata.H2MetadataProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The snapshot carries the provider's synonyms; the plain-JDBC path has none. */
class DatabaseServiceImplSynonymsH2Test {

    private static Connection connection;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:coreSynonymsTest;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE CUSTOMERS (ID INT PRIMARY KEY, NAME VARCHAR(100))");
            stmt.execute("CREATE SYNONYM SYN_CUSTOMERS FOR CUSTOMERS");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void snapshotWithProviderContainsSynonyms() throws Exception {
        MetaInfo metaInfo = new DatabaseServiceImpl().createMetaInfo(connection, new H2MetadataProvider());
        assertThat(metaInfo.structureInfo().synonyms()).singleElement().satisfies(syn -> {
            assertThat(syn.name()).isEqualTo("SYN_CUSTOMERS");
            assertThat(syn.targetName()).isEqualTo("CUSTOMERS");
        });
    }

    @Test
    void snapshotWithoutProviderHasNoSynonyms() throws Exception {
        MetaInfo metaInfo = new DatabaseServiceImpl().createMetaInfo(connection);
        assertThat(metaInfo.structureInfo().synonyms()).extracting(Synonym::name).isEmpty();
    }
}
