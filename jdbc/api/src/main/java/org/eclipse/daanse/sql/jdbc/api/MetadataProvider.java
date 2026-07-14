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
package org.eclipse.daanse.sql.jdbc.api;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.TypeInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.BestRowIdentifier;
import org.eclipse.daanse.sql.model.schema.CatalogReference;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.jdbc.api.schema.ColumnPrivilege;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.MaterializedView;
import org.eclipse.daanse.sql.jdbc.api.schema.Partition;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.PseudoColumn;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Sequence;
import org.eclipse.daanse.sql.jdbc.api.schema.SuperTable;
import org.eclipse.daanse.sql.jdbc.api.schema.SuperType;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.jdbc.api.schema.UniqueConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.UserDefinedType;
import org.eclipse.daanse.sql.jdbc.api.schema.VersionColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.ViewDefinition;

public interface MetadataProvider {

    /**
     * No-op provider that returns {@link Optional#empty()} for every query, forcing
     * callers of {@link MetaDataQueries} modern methods to fall back to the JDBC
     * path. Use when the dialect has no precomputed metadata to serve.
     */
    MetadataProvider EMPTY = new MetadataProvider() {
    };

    /**
     * @param catalog the catalog name, or null
     * @return the index info list, or Optional.empty() to fall back to standard
     *         JDBC
     * @throws SQLException on database access error
     */
    default Optional<List<IndexInfo>> getAllIndexInfo(Connection connection, String catalog, String schema)
            throws SQLException {
        return Optional.empty();
    }

    /**
     * @param catalog the catalog name, or null
     * @return the index info list, or Optional.empty() to fall back to standard
     *         JDBC
     * @throws SQLException on database access error
     */
    default Optional<List<IndexInfo>> getIndexInfo(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        return Optional.empty();
    }

    /**
     * @param catalog the catalog name, or null
     * @return the primary key list, or Optional.empty() to fall back to standard
     *         JDBC
     * @throws SQLException on database access error
     */
    default Optional<List<PrimaryKey>> getAllPrimaryKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        return Optional.empty();
    }

    /**
     * @param catalog the catalog name, or null
     * @return the imported key list, or Optional.empty() to fall back to standard
     *         JDBC
     * @throws SQLException on database access error
     */
    default Optional<List<ImportedKey>> getAllImportedKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        return Optional.empty();
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<Trigger> getAllTriggers(Connection connection, String catalog, String schema) throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<Trigger> getTriggers(Connection connection, String catalog, String schema, String tableName)
            throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<Sequence> getAllSequences(Connection connection, String catalog, String schema) throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @return the partition list — empty when the engine has no partitions or the
     *         loader is not implemented for this dialect
     * @throws SQLException on database access error
     */
    default List<Partition> getAllPartitions(Connection connection, String catalog, String schema) throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @param table   the table name (must not be null)
     * @return partitions of the table — empty when the table is not partitioned
     * @throws SQLException on database access error
     */
    default List<Partition> getPartitions(Connection connection, String catalog, String schema, String table)
            throws SQLException {
        List<Partition> all = getAllPartitions(connection, catalog, schema);
        if (all.isEmpty()) {
            return List.of();
        }
        java.util.List<Partition> result = new java.util.ArrayList<>();
        for (Partition p : all) {
            if (table.equals(p.table().name())) {
                result.add(p);
            }
        }
        return List.copyOf(result);
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<CheckConstraint> getAllCheckConstraints(Connection connection, String catalog, String schema)
            throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<CheckConstraint> getCheckConstraints(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<UniqueConstraint> getAllUniqueConstraints(Connection connection, String catalog, String schema)
            throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<UniqueConstraint> getUniqueConstraints(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<UserDefinedType> getAllUserDefinedTypes(Connection connection, String catalog, String schema)
            throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<ViewDefinition> getAllViewDefinitions(Connection connection, String catalog, String schema)
            throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<MaterializedView> getAllMaterializedViews(Connection connection, String catalog, String schema)
            throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<Procedure> getAllProcedures(Connection connection, String catalog, String schema) throws SQLException {
        return List.of();
    }

    /**
     * @param catalog the catalog name, or null
     * @throws SQLException on database access error
     */
    default List<Function> getAllFunctions(Connection connection, String catalog, String schema) throws SQLException {
        return List.of();
    }

    /** Bulk alternative to {@link java.sql.DatabaseMetaData#getCatalogs()}. */
    default Optional<List<CatalogReference>> getAllCatalogs(Connection connection) throws SQLException {
        return Optional.empty();
    }

    /**
     * Bulk alternative to
     * {@link java.sql.DatabaseMetaData#getSchemas(String, String)}.
     */
    default Optional<List<SchemaReference>> getAllSchemas(Connection connection, String catalog) throws SQLException {
        return Optional.empty();
    }

    /** Bulk alternative to {@link java.sql.DatabaseMetaData#getTableTypes()}. */
    default Optional<List<String>> getAllTableTypes(Connection connection) throws SQLException {
        return Optional.empty();
    }

    /**
     * Bulk alternative to
     * {@link java.sql.DatabaseMetaData#getTables(String, String, String, String[])}.
     */
    default Optional<List<TableDefinition>> getAllTableDefinitions(Connection connection, String catalog,
            String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
        return Optional.empty();
    }

    /**
     * Bulk alternative to
     * {@link java.sql.DatabaseMetaData#getColumns(String, String, String, String)}.
     */
    default Optional<List<ColumnDefinition>> getAllColumnDefinitions(Connection connection, String catalog,
            String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        return Optional.empty();
    }

    /** Bulk alternative to {@link java.sql.DatabaseMetaData#getTypeInfo()}. */
    default Optional<List<TypeInfo>> getAllTypeInfo(Connection connection) throws SQLException {
        return Optional.empty();
    }

    /**
     * Bulk alternative to
     * {@link java.sql.DatabaseMetaData#getExportedKeys(String, String, String)}
     * over a schema.
     */
    default Optional<List<ImportedKey>> getAllExportedKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        return Optional.empty();
    }

    /**
     * Per-table alternative to
     * {@link java.sql.DatabaseMetaData#getExportedKeys(String, String, String)}.
     */
    default Optional<List<ImportedKey>> getExportedKeys(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        return Optional.empty();
    }

    /**
     * Alternative to
     * {@link java.sql.DatabaseMetaData#getCrossReference(String, String, String, String, String, String)}.
     */
    default Optional<List<ImportedKey>> getCrossReference(Connection connection, String parentCatalog,
            String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable)
            throws SQLException {
        return Optional.empty();
    }

    /**
     * Per-procedure alternative to
     * {@link java.sql.DatabaseMetaData#getProcedureColumns(String, String, String, String)}.
     */
    default Optional<List<ProcedureColumn>> getProcedureColumns(Connection connection, String catalog, String schema,
            String procedureName) throws SQLException {
        return Optional.empty();
    }

    /**
     * Per-function alternative to
     * {@link java.sql.DatabaseMetaData#getFunctionColumns(String, String, String, String)}.
     */
    default Optional<List<FunctionColumn>> getFunctionColumns(Connection connection, String catalog, String schema,
            String functionName) throws SQLException {
        return Optional.empty();
    }

    /**
     * Per-table alternative to
     * {@link java.sql.DatabaseMetaData#getBestRowIdentifier(String, String, String, int, boolean)}.
     */
    default Optional<List<BestRowIdentifier>> getBestRowIdentifier(Connection connection, String catalog, String schema,
            String tableName, int scope, boolean nullable) throws SQLException {
        return Optional.empty();
    }

    /**
     * Per-table alternative to
     * {@link java.sql.DatabaseMetaData#getVersionColumns(String, String, String)}.
     */
    default Optional<List<VersionColumn>> getVersionColumns(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        return Optional.empty();
    }

    /**
     * Bulk alternative to
     * {@link java.sql.DatabaseMetaData#getPseudoColumns(String, String, String, String)}.
     */
    default Optional<List<PseudoColumn>> getAllPseudoColumns(Connection connection, String catalog,
            String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        return Optional.empty();
    }

    /**
     * Bulk alternative to
     * {@link java.sql.DatabaseMetaData#getTablePrivileges(String, String, String)}.
     */
    default Optional<List<TablePrivilege>> getAllTablePrivileges(Connection connection, String catalog,
            String schemaPattern, String tableNamePattern) throws SQLException {
        return Optional.empty();
    }

    /**
     * Per-table alternative to
     * {@link java.sql.DatabaseMetaData#getColumnPrivileges(String, String, String, String)}.
     */
    default Optional<List<ColumnPrivilege>> getColumnPrivileges(Connection connection, String catalog, String schema,
            String tableName, String columnNamePattern) throws SQLException {
        return Optional.empty();
    }

    /**
     * Bulk alternative to
     * {@link java.sql.DatabaseMetaData#getSuperTypes(String, String, String)}.
     */
    default Optional<List<SuperType>> getAllSuperTypes(Connection connection, String catalog, String schemaPattern,
            String typeNamePattern) throws SQLException {
        return Optional.empty();
    }

    /**
     * Bulk alternative to
     * {@link java.sql.DatabaseMetaData#getSuperTables(String, String, String)}.
     */
    default Optional<List<SuperTable>> getAllSuperTables(Connection connection, String catalog, String schemaPattern,
            String tableNamePattern) throws SQLException {
        return Optional.empty();
    }

    default Optional<List<UserDefinedType>> getAllUDTs(Connection connection, String catalog, String schemaPattern,
            String typeNamePattern, int[] types) throws SQLException {
        List<UserDefinedType> list = getAllUserDefinedTypes(connection, catalog, schemaPattern);
        return list.isEmpty() ? Optional.empty() : Optional.of(list);
    }
}
