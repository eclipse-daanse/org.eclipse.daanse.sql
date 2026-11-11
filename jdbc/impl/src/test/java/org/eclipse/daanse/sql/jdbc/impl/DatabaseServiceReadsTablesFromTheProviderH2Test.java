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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.jdbc.metadata.H2MetadataProvider;
import org.eclipse.daanse.sql.jdbc.record.schema.TableDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TableMetaDataRecord;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A provider that reads the tables itself gives the snapshot its tables, in every schema the tables
 * lie in, as it gives the columns: where a JDBC driver reports no comments, the provider reads them.
 */
class DatabaseServiceReadsTablesFromTheProviderH2Test {

    private static final String REMARK = "read by the provider";

    private static Connection connection;
    private static StructureInfo structure;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:tablesFromTheProvider;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA STOCK");
            stmt.execute("CREATE TABLE STOCK.PRODUCT (ID INT NOT NULL PRIMARY KEY)");
            stmt.execute("CREATE SCHEMA SALES");
            stmt.execute("CREATE TABLE SALES.ORDERS (ID INT NOT NULL PRIMARY KEY)");
        }
        H2MetadataProvider provider = new H2MetadataProvider() {

            /** The driver's tables, each with a remark only the provider knows. */
            @Override
            public Optional<List<TableDefinition>> getAllTableDefinitions(Connection c, String catalog,
                    String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
                List<TableDefinition> tables = new ArrayList<>();
                try (ResultSet rs = c.getMetaData().getTables(catalog, schemaPattern, tableNamePattern, types)) {
                    while (rs.next()) {
                        TableReference table = new TableReference(
                                Optional.of(new SchemaReference(Optional.empty(), rs.getString("TABLE_SCHEM"))),
                                rs.getString("TABLE_NAME"), rs.getString("TABLE_TYPE"));
                        tables.add(new TableDefinitionRecord(table, new TableMetaDataRecord(Optional.of(REMARK),
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                Optional.empty())));
                    }
                }
                return Optional.of(tables);
            }
        };
        structure = new DatabaseServiceImpl().createMetaInfo(connection, provider).structureInfo();
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
        connection.close();
    }

    @Test
    void theTablesOfEverySchemaAreTheProvidersOwn() {
        assertThat(structure.tables()).filteredOn(t -> name(t.table()).equals("STOCK.PRODUCT")).singleElement()
                .satisfies(t -> assertThat(t.tableMetaData().remarks()).contains(REMARK));
        assertThat(structure.tables()).filteredOn(t -> name(t.table()).equals("SALES.ORDERS")).singleElement()
                .satisfies(t -> assertThat(t.tableMetaData().remarks()).contains(REMARK));
    }

    private static String name(TableReference table) {
        return table.schema().map(SchemaReference::name).orElse("?") + "." + table.name();
    }
}
