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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.model.schema.CatalogReference;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege;
import org.eclipse.daanse.sql.jdbc.record.schema.TableDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TableMetaDataRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DatabaseServiceProviderFallbackH2Test {

    private static Connection connection;
    private static DatabaseService service;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:providerFallback;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE DEPARTMENTS (
                        DEPT_ID INT NOT NULL PRIMARY KEY,
                        DEPT_NAME VARCHAR(100) NOT NULL
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE EMPLOYEES (
                        EMP_ID INT NOT NULL PRIMARY KEY,
                        DEPT_ID INT,
                        CONSTRAINT FK_EMP_DEPT FOREIGN KEY (DEPT_ID) REFERENCES DEPARTMENTS(DEPT_ID)
                    )
                    """);
        }
        service = new DatabaseServiceImpl();
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

    /** A MetadataProvider that intentionally declines (always returns Optional.empty()). */
    private static MetadataProvider declining() {
        return new MetadataProvider() { };
    }

    // ---------- Catalogs ----------

    @Test
    void getCatalogs_providerEmpty_fallsBackToJdbc() throws SQLException {
        List<CatalogReference> viaProvider = service.getCatalogs(connection, declining());
        // Compare against the same modern path with a no-op provider — both paths must agree.
        List<CatalogReference> viaJdbc = service.getCatalogs(connection, MetadataProvider.EMPTY);
        assertThat(viaProvider).containsExactlyElementsOf(viaJdbc);
    }

    @Test
    void getCatalogs_providerPresent_usedVerbatim() throws SQLException {
        List<CatalogReference> sentinel = List.of(new CatalogReference("FAKE_CAT"));
        MetadataProvider provider = new MetadataProvider() {
            @Override
            public Optional<List<CatalogReference>> getAllCatalogs(Connection c) {
                return Optional.of(sentinel);
            }
        };
        List<CatalogReference> out = service.getCatalogs(connection, provider);
        assertThat(out).isSameAs(sentinel);
    }

    // ---------- Schemas ----------

    @Test
    void getSchemas_providerEmpty_fallsBackToJdbc() throws SQLException {
        List<SchemaReference> out = service.getSchemas(connection, declining(), null);
        assertThat(out).isNotEmpty();
    }

    @Test
    void getSchemas_providerPresent_usedVerbatim() throws SQLException {
        List<SchemaReference> sentinel = List.of(
                new SchemaReference(Optional.of(new CatalogReference("CAT")), "MADE_UP"));
        MetadataProvider provider = new MetadataProvider() {
            @Override
            public Optional<List<SchemaReference>> getAllSchemas(Connection c, String catalog) {
                return Optional.of(sentinel);
            }
        };
        assertThat(service.getSchemas(connection, provider, null)).isSameAs(sentinel);
    }

    // ---------- Table types ----------

    @Test
    void getTableTypes_providerEmpty_fallsBackToJdbc() throws SQLException {
        List<String> types = service.getTableTypes(connection, declining());
        assertThat(types).isNotEmpty();
    }

    @Test
    void getTableTypes_providerPresent_usedVerbatim() throws SQLException {
        List<String> sentinel = List.of("FOO", "BAR");
        MetadataProvider provider = new MetadataProvider() {
            @Override
            public Optional<List<String>> getAllTableTypes(Connection c) {
                return Optional.of(sentinel);
            }
        };
        assertThat(service.getTableTypes(connection, provider)).isSameAs(sentinel);
    }

    // ---------- Tables ----------

    @Test
    void getTableDefinitions_providerEmpty_fallsBackToJdbc() throws SQLException {
        List<TableDefinition> defs = service.getTableDefinitions(connection, declining(),
                null, "PUBLIC", "%", null);
        assertThat(defs).anyMatch(td -> "EMPLOYEES".equalsIgnoreCase(td.table().name()));
    }

    @Test
    void getTableDefinitions_providerPresent_usedVerbatim() throws SQLException {
        TableReference ref = new TableReference(
                Optional.of(new SchemaReference(Optional.empty(), "PUBLIC")),
                "FAKE_TABLE");
        List<TableDefinition> sentinel = List.of(new TableDefinitionRecord(ref,
                new TableMetaDataRecord(Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty())));
        MetadataProvider provider = new MetadataProvider() {
            @Override
            public Optional<List<TableDefinition>> getAllTableDefinitions(Connection c, String catalog,
                    String schemaPattern, String tableNamePattern, String[] types) {
                return Optional.of(sentinel);
            }
        };
        assertThat(service.getTableDefinitions(connection, provider, null, "PUBLIC", "%", null))
                .isSameAs(sentinel);
    }

    // ---------- Columns ----------

    @Test
    void getColumnDefinitions_providerEmpty_fallsBackToJdbc() throws SQLException {
        List<ColumnDefinition> cols = service.getColumnDefinitions(connection, declining(),
                null, "PUBLIC", "EMPLOYEES", "%");
        assertThat(cols).anyMatch(c -> "EMP_ID".equalsIgnoreCase(c.column().name()));
    }

    @Test
    void getColumnDefinitions_providerPresent_usedVerbatim() throws SQLException {
        List<ColumnDefinition> sentinel = List.of();  // empty marker list that the provider "owns"
        MetadataProvider provider = new MetadataProvider() {
            @Override
            public Optional<List<ColumnDefinition>> getAllColumnDefinitions(Connection c, String catalog,
                    String schemaPattern, String tableNamePattern, String columnNamePattern) {
                return Optional.of(sentinel);
            }
        };
        assertThat(service.getColumnDefinitions(connection, provider, null, "PUBLIC", "EMPLOYEES", "%"))
                .isSameAs(sentinel);
    }

    // ---------- ExportedKeys ----------

    @Test
    void getExportedKeys_providerEmpty_fallsBackToJdbc() throws SQLException {
        TableReference departments = new TableReference(
                Optional.of(new SchemaReference(Optional.empty(), "PUBLIC")),
                "DEPARTMENTS");
        List<ImportedKey> keys = service.getExportedKeys(connection, declining(),
                null, "PUBLIC", departments.name());
        // EMPLOYEES.FK_EMP_DEPT points at DEPARTMENTS — JDBC reports it.
        assertThat(keys).anyMatch(k -> "FK_EMP_DEPT".equals(k.name())
                || "FK_EMP_DEPT".equalsIgnoreCase(k.name()));
    }

    @Test
    void getExportedKeys_providerAllKeysPresent_usedVerbatim() throws SQLException {
        List<ImportedKey> sentinel = List.of();
        MetadataProvider provider = new MetadataProvider() {
            @Override
            public Optional<List<ImportedKey>> getAllExportedKeys(Connection c, String catalog, String schema) {
                return Optional.of(sentinel);
            }
        };
        assertThat(service.getExportedKeys(connection, provider, null, "PUBLIC", "DEPARTMENTS"))
                .isEmpty();
    }

    // ---------- UDTs (full signature) ----------

    @Test
    void getUDTs_providerEmpty_fallsBackToJdbc() throws SQLException {
        // H2 has no UDTs; contract: non-null result, no exception.
        assertThat(service.getUDTs(connection, declining(), null, "PUBLIC", "%", null)).isNotNull();
    }

    // ---------- TablePrivileges ----------

    @Test
    void getTablePrivileges_providerEmpty_fallsBackToJdbc() throws SQLException {
        List<TablePrivilege> tps = service.getTablePrivileges(connection, declining(), null, "PUBLIC", "%");
        assertThat(tps).isNotNull();
    }

    @Test
    void getTablePrivileges_providerPresent_usedVerbatim() throws SQLException {
        List<TablePrivilege> sentinel = List.of();
        MetadataProvider provider = new MetadataProvider() {
            @Override
            public Optional<List<TablePrivilege>> getAllTablePrivileges(Connection c, String catalog,
                    String schemaPattern, String tableNamePattern) {
                return Optional.of(sentinel);
            }
        };
        assertThat(service.getTablePrivileges(connection, provider, null, "PUBLIC", "%"))
                .isSameAs(sentinel);
    }
}
