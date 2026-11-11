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

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TableDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TableMetaDataRecord;
import org.eclipse.daanse.sql.model.schema.CatalogReference;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;

/**
 * Tables and columns as the JDBC driver reports them, with the comments a driver leaves out taken
 * from the provider's own reading. For a database whose driver gives no {@code REMARKS}: the
 * provider reads the comments in bulk, and the tables and columns keep the types and sizes the
 * driver reports. A row is named as the snapshot names a driver's row, the schema in its catalog.
 */
final class DriverMetadata {

    /** The comments a provider read, by schema, table or view, and column. */
    @FunctionalInterface
    interface Comments {

        /**
         * @param column the column, or {@code null} for the table's or view's own comment
         * @return the comment, or {@code null} for none
         */
        String of(String schema, String table, String column);
    }

    private DriverMetadata() {
    }

    /**
     * {@link java.sql.DatabaseMetaData#getTables}, each table and view with the driver's remarks or
     * else its comment from {@code comments}.
     */
    static List<TableDefinition> tables(Connection connection, String catalog, String schemaPattern,
            String tableNamePattern, String[] types, Comments comments) throws SQLException {
        List<TableDefinition> tables = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getTables(catalog, schemaPattern, tableNamePattern, types)) {
            while (rs.next()) {
                String catalogName = rs.getString("TABLE_CAT");
                String schemaName = rs.getString("TABLE_SCHEM");
                String tableName = rs.getString("TABLE_NAME");
                String tableType = rs.getString("TABLE_TYPE");
                Optional<String> remarks = Optional.ofNullable(rs.getString("REMARKS"))
                        .or(() -> Optional.ofNullable(comments.of(schemaName, tableName, null)));
                TableReference table = new TableReference(namespace(catalogName, schemaName), tableName, tableType);
                tables.add(new TableDefinitionRecord(table, new TableMetaDataRecord(remarks, Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())));
            }
        }
        return List.copyOf(tables);
    }

    /**
     * {@link java.sql.DatabaseMetaData#getColumns}, read left to right, each column with the
     * driver's remarks or else its comment from {@code comments}. A type code the JDBC types do not
     * know, as a driver's own, is {@link JDBCType#OTHER}.
     */
    static List<ColumnDefinition> columns(Connection connection, String catalog, String schemaPattern,
            String tableNamePattern, String columnNamePattern, Comments comments) throws SQLException {
        List<ColumnDefinition> columns = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getColumns(catalog, schemaPattern, tableNamePattern,
                columnNamePattern)) {
            while (rs.next()) {
                String catalogName = rs.getString("TABLE_CAT");
                String schemaName = rs.getString("TABLE_SCHEM");
                String tableName = rs.getString("TABLE_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                int dataType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");
                OptionalInt columnSize = optionalInt(rs, "COLUMN_SIZE");
                OptionalInt decimalDigits = optionalInt(rs, "DECIMAL_DIGITS");
                OptionalInt numPrecRadix = optionalInt(rs, "NUM_PREC_RADIX");
                OptionalInt nullable = optionalInt(rs, "NULLABLE");
                Optional<String> remarks = Optional.ofNullable(rs.getString("REMARKS"))
                        .or(() -> Optional.ofNullable(comments.of(schemaName, tableName, columnName)));
                Optional<String> columnDefault = Optional.ofNullable(rs.getString("COLUMN_DEF"));
                OptionalInt charOctetLength = optionalInt(rs, "CHAR_OCTET_LENGTH");
                ColumnMetaData.AutoIncrement autoIncrement = ColumnMetaData.AutoIncrement
                        .ofString(rs.getString("IS_AUTOINCREMENT"));
                ColumnMetaData.GeneratedColumn generatedColumn = ColumnMetaData.GeneratedColumn
                        .ofString(rs.getString("IS_GENERATEDCOLUMN"));

                JDBCType jdbcType;
                try {
                    jdbcType = JDBCType.valueOf(dataType);
                } catch (IllegalArgumentException e) {
                    jdbcType = JDBCType.OTHER;
                }
                ColumnMetaData.Nullability nullability = nullable.isPresent()
                        ? ColumnMetaData.Nullability.of(nullable.getAsInt())
                        : ColumnMetaData.Nullability.UNKNOWN;
                TableReference table = new TableReference(namespace(catalogName, schemaName), tableName);
                columns.add(new ColumnDefinitionRecord(new ColumnReference(Optional.of(table), columnName),
                        new ColumnMetaDataRecord(jdbcType, typeName, columnSize, decimalDigits, numPrecRadix,
                                nullability, charOctetLength, remarks, columnDefault, autoIncrement,
                                generatedColumn)));
            }
        }
        return List.copyOf(columns);
    }

    /** The schema in its catalog; for a driver without schemas, the catalog as the schema. */
    private static Optional<SchemaReference> namespace(String catalogName, String schemaName) {
        Optional<CatalogReference> catalog = Optional.ofNullable(catalogName).map(CatalogReference::new);
        if (schemaName != null) {
            return Optional.of(new SchemaReference(catalog, schemaName));
        }
        return Optional.ofNullable(catalogName).map(name -> new SchemaReference(Optional.empty(), name));
    }

    private static OptionalInt optionalInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? OptionalInt.empty() : OptionalInt.of(value);
    }
}
