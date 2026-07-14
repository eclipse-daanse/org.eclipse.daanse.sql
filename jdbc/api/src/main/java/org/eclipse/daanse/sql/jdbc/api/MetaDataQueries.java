/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.jdbc.api;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

import org.eclipse.daanse.sql.jdbc.api.meta.TypeInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.BestRowIdentifier;
import org.eclipse.daanse.sql.model.schema.CatalogReference;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.jdbc.api.schema.ColumnPrivilege;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Partition;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.PseudoColumn;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.jdbc.api.schema.SuperTable;
import org.eclipse.daanse.sql.jdbc.api.schema.SuperType;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.jdbc.api.schema.UserDefinedType;
import org.eclipse.daanse.sql.jdbc.api.schema.VersionColumn;

/**
 * JDBC catalog queries keyed on {@link Connection} + {@link MetadataProvider}.
 * The provider serves cached/precomputed answers when available; the impl falls
 * back to {@link DatabaseMetaData} reads when the provider has no answer.
 */
public interface MetaDataQueries {

    // --- Catalog / schema / type-info ---

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider; serves cached answers when
     *                   available
     * @return all catalogs visible to {@code connection}
     * @throws SQLException on database access error
     */
    List<CatalogReference> getCatalogs(Connection connection, MetadataProvider provider) throws SQLException;

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @param catalog    catalog filter or {@code null} for all
     * @return schemas in {@code catalog}
     * @throws SQLException on database access error
     */
    List<SchemaReference> getSchemas(Connection connection, MetadataProvider provider, String catalog)
            throws SQLException;

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @return supported table-type names (e.g. {@code TABLE}, {@code VIEW})
     * @throws SQLException on database access error
     */
    List<String> getTableTypes(Connection connection, MetadataProvider provider) throws SQLException;

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @return type info entries describing the engine's SQL types
     * @throws SQLException on database access error
     */
    List<TypeInfo> getTypeInfo(Connection connection, MetadataProvider provider) throws SQLException;

    // --- Tables ---

    /**
     * @param connection       the connection (not closed by this method)
     * @param provider         dialect-specific provider
     * @param catalog          catalog filter or {@code null}
     * @param schemaPattern    schema name pattern or {@code null}
     * @param tableNamePattern table name pattern or {@code null}
     * @param types            table types to include or {@code null} for all
     * @return matching tables
     * @throws SQLException on database access error
     */
    List<TableDefinition> getTableDefinitions(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String tableNamePattern, String[] types) throws SQLException;

    /** @return all tables visible to {@code connection} */
    default List<TableDefinition> getTableDefinitions(Connection connection, MetadataProvider provider)
            throws SQLException {
        return getTableDefinitions(connection, provider, null, null, null, null);
    }

    /** @return tables matching the given types */
    default List<TableDefinition> getTableDefinitions(Connection connection, MetadataProvider provider,
            List<String> types) throws SQLException {
        String[] typesArr = types == null ? null : types.toArray(new String[0]);
        return getTableDefinitions(connection, provider, null, null, null, typesArr);
    }

    /** @return tables in {@code catalog} */
    default List<TableDefinition> getTableDefinitions(Connection connection, MetadataProvider provider,
            CatalogReference catalog) throws SQLException {
        return getTableDefinitions(connection, provider, catalog.name(), null, null, null);
    }

    /** @return tables in {@code catalog} matching {@code types} */
    default List<TableDefinition> getTableDefinitions(Connection connection, MetadataProvider provider,
            CatalogReference catalog, List<String> types) throws SQLException {
        String[] typesArr = types == null ? null : types.toArray(new String[0]);
        return getTableDefinitions(connection, provider, catalog.name(), null, null, typesArr);
    }

    /** @return tables in {@code schema} */
    default List<TableDefinition> getTableDefinitions(Connection connection, MetadataProvider provider,
            SchemaReference schema) throws SQLException {
        String catalog = schema.catalog().map(CatalogReference::name).orElse(null);
        return getTableDefinitions(connection, provider, catalog, schema.name(), null, null);
    }

    /** @return tables in {@code schema} matching {@code types} */
    default List<TableDefinition> getTableDefinitions(Connection connection, MetadataProvider provider,
            SchemaReference schema, List<String> types) throws SQLException {
        String catalog = schema.catalog().map(CatalogReference::name).orElse(null);
        String[] typesArr = types == null ? null : types.toArray(new String[0]);
        return getTableDefinitions(connection, provider, catalog, schema.name(), null, typesArr);
    }

    /**
     * @return tables matching {@code table} (catalog/schema derived from
     *         {@code table.schema()})
     */
    default List<TableDefinition> getTableDefinitions(Connection connection, MetadataProvider provider,
            TableReference table) throws SQLException {
        String schema = table.schema().map(SchemaReference::name).orElse(null);
        String catalog = table.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse(null);
        return getTableDefinitions(connection, provider, catalog, schema, table.name(), new String[] { table.type() });
    }

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @param table      the table to test
     * @return {@code true} when {@code table} resolves to an existing table
     * @throws SQLException on database access error
     */
    boolean tableExists(Connection connection, MetadataProvider provider, TableReference table) throws SQLException;

    /**
     * @param connection       the connection (not closed by this method)
     * @param provider         dialect-specific provider
     * @param catalog          catalog filter or {@code null}
     * @param schemaPattern    schema name pattern or {@code null}
     * @param tableNamePattern table name pattern or {@code null}
     * @param types            table types to include or {@code null} for all
     * @return {@code true} when at least one table matches
     * @throws SQLException on database access error
     */
    boolean tableExists(Connection connection, MetadataProvider provider, String catalog, String schemaPattern,
            String tableNamePattern, String[] types) throws SQLException;

    // --- Columns ---

    /**
     * @param connection        the connection (not closed by this method)
     * @param provider          dialect-specific provider
     * @param catalog           catalog filter or {@code null}
     * @param schemaPattern     schema name pattern or {@code null}
     * @param tableNamePattern  table name pattern or {@code null}
     * @param columnNamePattern column name pattern or {@code null}
     * @return matching columns
     * @throws SQLException on database access error
     */
    List<ColumnDefinition> getColumnDefinitions(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException;

    /** @return all column definitions visible to {@code connection} */
    default List<ColumnDefinition> getColumnDefinitions(Connection connection, MetadataProvider provider)
            throws SQLException {
        return getColumnDefinitions(connection, provider, null, null, null, null);
    }

    /** @return columns of {@code table} */
    default List<ColumnDefinition> getColumnDefinitions(Connection connection, MetadataProvider provider,
            TableReference table) throws SQLException {
        String schema = table.schema().map(SchemaReference::name).orElse(null);
        String catalog = table.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse(null);
        return getColumnDefinitions(connection, provider, catalog, schema, table.name(), null);
    }

    /** @return column definitions matching {@code column} */
    default List<ColumnDefinition> getColumnDefinitions(Connection connection, MetadataProvider provider,
            ColumnReference column) throws SQLException {
        var tableOpt = column.table();
        String catalog = tableOpt.flatMap(TableReference::schema).flatMap(SchemaReference::catalog)
                .map(CatalogReference::name).orElse(null);
        String schema = tableOpt.flatMap(TableReference::schema).map(SchemaReference::name).orElse(null);
        String tableName = tableOpt.map(TableReference::name).orElse(null);
        return getColumnDefinitions(connection, provider, catalog, schema, tableName, column.name());
    }

    /**
     * @param connection        the connection (not closed by this method)
     * @param provider          dialect-specific provider
     * @param catalog           catalog filter or {@code null}
     * @param schemaPattern     schema name pattern or {@code null}
     * @param tableNamePattern  table name pattern or {@code null}
     * @param columnNamePattern column name pattern or {@code null}
     * @return {@code true} when at least one column matches
     * @throws SQLException on database access error
     */
    boolean columnExists(Connection connection, MetadataProvider provider, String catalog, String schemaPattern,
            String tableNamePattern, String columnNamePattern) throws SQLException;

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @param column     the column to test
     * @return {@code true} when {@code column} resolves to an existing column
     * @throws SQLException on database access error
     */
    boolean columnExists(Connection connection, MetadataProvider provider, ColumnReference column) throws SQLException;

    // --- Foreign keys ---

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @param catalog    catalog filter or {@code null}
     * @param schema     schema filter or {@code null}
     * @param tableName  table name (must be set)
     * @return foreign keys this table imports
     * @throws SQLException on database access error
     */
    List<ImportedKey> getImportedKeys(Connection connection, MetadataProvider provider, String catalog, String schema,
            String tableName) throws SQLException;

    /** @return foreign keys imported by {@code table} */
    default List<ImportedKey> getImportedKeys(Connection connection, MetadataProvider provider, TableReference table)
            throws SQLException {
        String schema = table.schema().map(SchemaReference::name).orElse(null);
        String catalog = table.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse(null);
        return getImportedKeys(connection, provider, catalog, schema, table.name());
    }

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @param catalog    catalog filter or {@code null}
     * @param schema     schema filter or {@code null}
     * @param tableName  table name (must be set)
     * @return foreign keys other tables export referencing this one
     * @throws SQLException on database access error
     */
    List<ImportedKey> getExportedKeys(Connection connection, MetadataProvider provider, String catalog, String schema,
            String tableName) throws SQLException;

    /** @return foreign keys exported referencing {@code table} */
    default List<ImportedKey> getExportedKeys(Connection connection, MetadataProvider provider, TableReference table)
            throws SQLException {
        String schema = table.schema().map(SchemaReference::name).orElse(null);
        String catalog = table.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse(null);
        return getExportedKeys(connection, provider, catalog, schema, table.name());
    }

    /**
     * @param connection     the connection (not closed by this method)
     * @param provider       dialect-specific provider
     * @param parentCatalog  catalog of the parent table
     * @param parentSchema   schema of the parent table
     * @param parentTable    parent table name
     * @param foreignCatalog catalog of the foreign table
     * @param foreignSchema  schema of the foreign table
     * @param foreignTable   foreign table name
     * @return foreign-key constraints between the two tables
     * @throws SQLException on database access error
     */
    List<ImportedKey> getCrossReference(Connection connection, MetadataProvider provider, String parentCatalog,
            String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable)
            throws SQLException;

    /**
     * @return foreign-key constraints between {@code parentTable} and
     *         {@code foreignTable}
     */
    default List<ImportedKey> getCrossReference(Connection connection, MetadataProvider provider,
            TableReference parentTable, TableReference foreignTable) throws SQLException {
        String parentCatalog = parentTable.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name)
                .orElse(null);
        String parentSchema = parentTable.schema().map(SchemaReference::name).orElse(null);
        String foreignCatalog = foreignTable.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name)
                .orElse(null);
        String foreignSchema = foreignTable.schema().map(SchemaReference::name).orElse(null);
        return getCrossReference(connection, provider, parentCatalog, parentSchema, parentTable.name(), foreignCatalog,
                foreignSchema, foreignTable.name());
    }

    // --- Procedures / functions / UDTs ---

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @return stored procedures visible to {@code connection}
     * @throws SQLException on database access error
     */
    List<Procedure> getProcedures(Connection connection, MetadataProvider provider) throws SQLException;

    /**
     * @param connection           the connection (not closed by this method)
     * @param provider             dialect-specific provider
     * @param catalog              catalog filter or {@code null}
     * @param schemaPattern        schema name pattern or {@code null}
     * @param procedureNamePattern procedure name pattern or {@code null}
     * @return matching stored procedures
     * @throws SQLException on database access error
     */
    List<Procedure> getProcedures(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String procedureNamePattern) throws SQLException;

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @return user-defined functions visible to {@code connection}
     * @throws SQLException on database access error
     */
    List<Function> getFunctions(Connection connection, MetadataProvider provider) throws SQLException;

    /**
     * @param connection          the connection (not closed by this method)
     * @param provider            dialect-specific provider
     * @param catalog             catalog filter or {@code null}
     * @param schemaPattern       schema name pattern or {@code null}
     * @param functionNamePattern function name pattern or {@code null}
     * @return matching user-defined functions
     * @throws SQLException on database access error
     */
    List<Function> getFunctions(Connection connection, MetadataProvider provider, String catalog, String schemaPattern,
            String functionNamePattern) throws SQLException;

    /**
     * @param connection      the connection (not closed by this method)
     * @param provider        dialect-specific provider
     * @param catalog         catalog filter or {@code null}
     * @param schemaPattern   schema name pattern or {@code null}
     * @param typeNamePattern type name pattern or {@code null}
     * @param types           UDT type codes or {@code null}
     * @return user-defined types
     * @throws SQLException on database access error
     */
    List<UserDefinedType> getUDTs(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String typeNamePattern, int[] types) throws SQLException;

    // --- Row identification ---

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @param table      target table
     * @param scope      JDBC scope constant (e.g.
     *                   {@link DatabaseMetaData#bestRowSession})
     * @param nullable   whether nullable columns are eligible
     * @return best row identifier columns
     * @throws SQLException on database access error
     */
    List<BestRowIdentifier> getBestRowIdentifier(Connection connection, MetadataProvider provider, TableReference table,
            int scope, boolean nullable) throws SQLException;

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @param table      target table
     * @return version columns automatically updated when any row is updated
     * @throws SQLException on database access error
     */
    List<VersionColumn> getVersionColumns(Connection connection, MetadataProvider provider, TableReference table)
            throws SQLException;

    /**
     * @param connection        the connection (not closed by this method)
     * @param provider          dialect-specific provider
     * @param catalog           catalog filter or {@code null}
     * @param schemaPattern     schema name pattern or {@code null}
     * @param tableNamePattern  table name pattern or {@code null}
     * @param columnNamePattern column name pattern or {@code null}
     * @return pseudo columns (system columns hidden from {@code SELECT *})
     * @throws SQLException on database access error
     */
    List<PseudoColumn> getPseudoColumns(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException;

    // --- Privileges ---

    /**
     * @param connection       the connection (not closed by this method)
     * @param provider         dialect-specific provider
     * @param catalog          catalog filter or {@code null}
     * @param schemaPattern    schema name pattern or {@code null}
     * @param tableNamePattern table name pattern or {@code null}
     * @return table-level privileges
     * @throws SQLException on database access error
     */
    List<TablePrivilege> getTablePrivileges(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String tableNamePattern) throws SQLException;

    /**
     * @param connection        the connection (not closed by this method)
     * @param provider          dialect-specific provider
     * @param table             target table
     * @param columnNamePattern column name pattern or {@code null}
     * @return column-level privileges
     * @throws SQLException on database access error
     */
    List<ColumnPrivilege> getColumnPrivileges(Connection connection, MetadataProvider provider, TableReference table,
            String columnNamePattern) throws SQLException;

    // --- Type hierarchy ---

    /**
     * @param connection      the connection (not closed by this method)
     * @param provider        dialect-specific provider
     * @param catalog         catalog filter or {@code null}
     * @param schemaPattern   schema name pattern or {@code null}
     * @param typeNamePattern type name pattern or {@code null}
     * @return UDT supertype links
     * @throws SQLException on database access error
     */
    List<SuperType> getSuperTypes(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String typeNamePattern) throws SQLException;

    /**
     * @param connection       the connection (not closed by this method)
     * @param provider         dialect-specific provider
     * @param catalog          catalog filter or {@code null}
     * @param schemaPattern    schema name pattern or {@code null}
     * @param tableNamePattern table name pattern or {@code null}
     * @return table-supertable links
     * @throws SQLException on database access error
     */
    List<SuperTable> getSuperTables(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String tableNamePattern) throws SQLException;

    // --- Partitions ---

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @param table      partitioned table
     * @return partitions of {@code table}; empty when not partitioned or
     *         unsupported
     * @throws SQLException on database access error
     */
    default List<Partition> getPartitions(Connection connection, MetadataProvider provider, TableReference table)
            throws SQLException {
        String schema = table.schema().map(SchemaReference::name).orElse(null);
        String catalog = table.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse(null);
        return provider.getPartitions(connection, catalog, schema, table.name());
    }

    /**
     * @param connection the connection (not closed by this method)
     * @param provider   dialect-specific provider
     * @param catalog    catalog filter or {@code null}
     * @param schema     schema filter or {@code null}
     * @return all partitions in the given scope; empty when nothing is partitioned
     * @throws SQLException on database access error
     */
    default List<Partition> getAllPartitions(Connection connection, MetadataProvider provider, String catalog,
            String schema) throws SQLException {
        return provider.getAllPartitions(connection, catalog, schema);
    }
}
