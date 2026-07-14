/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.jdbc.metadata;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionReference;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Partition;
import org.eclipse.daanse.sql.jdbc.api.schema.PartitionMethod;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Sequence;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.model.schema.TriggerReference;
import org.eclipse.daanse.sql.jdbc.api.schema.UniqueConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.ViewDefinition;
import org.eclipse.daanse.sql.jdbc.record.schema.CheckConstraintRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.FunctionColumnRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.FunctionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ImportedKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoItemRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PartitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ProcedureColumnRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ProcedureRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TriggerRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.UniqueConstraintRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ViewDefinitionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MySql system-catalog/{@code information_schema} reader — the
 * {@link MetadataProvider} overrides extracted 1:1 from the SQL dialect (reading only,
 * no spelling).
 */
public class MySqlMetadataProvider implements MetadataProvider {


    private static final Logger LOGGER = LoggerFactory.getLogger(MySqlMetadataProvider.class);


    @Override
    public List<Trigger> getAllTriggers(Connection connection, String catalog, String schema) throws SQLException {
        String sql = """
                SELECT TRIGGER_NAME, EVENT_OBJECT_TABLE, ACTION_TIMING, EVENT_MANIPULATION,
                        ACTION_STATEMENT, ACTION_ORIENTATION
                FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = ?
                ORDER BY EVENT_OBJECT_TABLE, TRIGGER_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Trigger> triggers = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    triggers.add(readTrigger(rs, schemaName));
                }
            }
        }
        return List.copyOf(triggers);
    }


    @Override
    public List<Trigger> getTriggers(Connection connection, String catalog, String schema, String tableName)
            throws SQLException {
        String sql = """
                SELECT TRIGGER_NAME, EVENT_OBJECT_TABLE, ACTION_TIMING, EVENT_MANIPULATION,
                        ACTION_STATEMENT, ACTION_ORIENTATION
                FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = ? AND EVENT_OBJECT_TABLE = ?
                ORDER BY TRIGGER_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Trigger> triggers = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    triggers.add(readTrigger(rs, schemaName));
                }
            }
        }
        return List.copyOf(triggers);
    }


    @Override
    public List<Sequence> getAllSequences(Connection connection, String catalog, String schema) throws SQLException {
        // MySQL does NOT support sequences
        return List.of();
    }


    @Override
    public List<Partition> getAllPartitions(Connection connection, String catalog, String schema) throws SQLException {
        // INFORMATION_SCHEMA.PARTITIONS returns one row per non-partitioned table with
        // PARTITION_NAME=NULL, plus one row per (partition, sub-partition) for
        // partitioned tables.
        // We filter out the PARTITION_NAME IS NULL rows.
        String sql = """
                SELECT TABLE_NAME, PARTITION_NAME, SUBPARTITION_NAME,
                        PARTITION_METHOD, SUBPARTITION_METHOD,
                        PARTITION_EXPRESSION, SUBPARTITION_EXPRESSION,
                        PARTITION_DESCRIPTION,
                        PARTITION_ORDINAL_POSITION, SUBPARTITION_ORDINAL_POSITION,
                        TABLE_ROWS
                FROM information_schema.PARTITIONS
                WHERE TABLE_SCHEMA = ? AND PARTITION_NAME IS NOT NULL
                ORDER BY TABLE_NAME, PARTITION_ORDINAL_POSITION, SUBPARTITION_ORDINAL_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Partition> partitions = new ArrayList<>();
        Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String partitionName = rs.getString("PARTITION_NAME");
                    String subPartitionName = rs.getString("SUBPARTITION_NAME");
                    boolean isSub = subPartitionName != null;

                    String name = isSub ? subPartitionName : partitionName;
                    String methodStr = rs.getString(isSub ? "SUBPARTITION_METHOD" : "PARTITION_METHOD");
                    PartitionMethod method = mapPartitionMethod(methodStr);
                    String expression = rs.getString(isSub ? "SUBPARTITION_EXPRESSION" : "PARTITION_EXPRESSION");
                    String description = rs.getString("PARTITION_DESCRIPTION");
                    int ordinal = rs.getInt(isSub ? "SUBPARTITION_ORDINAL_POSITION" : "PARTITION_ORDINAL_POSITION");
                    Optional<Integer> oOrdinal = rs.wasNull() ? Optional.empty() : Optional.of(ordinal);
                    long rowCount = rs.getLong("TABLE_ROWS");
                    Optional<Long> oRowCount = rs.wasNull() ? Optional.empty() : Optional.of(rowCount);

                    TableReference tableRef = new TableReference(oSchema, tableName);
                    partitions.add(new PartitionRecord(name, tableRef, oOrdinal, method,
                            expression == null ? Optional.empty() : Optional.of(expression),
                            description == null ? Optional.empty() : Optional.of(description), oRowCount,
                            isSub ? Optional.of(partitionName) : Optional.empty(), Optional.empty(), Optional.empty()));
                }
            }
        } catch (SQLException e) {
            LOGGER.debug("Could not load partitions from INFORMATION_SCHEMA.PARTITIONS: {}", e.getMessage());
            return List.of();
        }
        return List.copyOf(partitions);
    }


    private static PartitionMethod mapPartitionMethod(String raw) {
        if (raw == null) {
            return PartitionMethod.OTHER;
        }
        return switch (raw.toUpperCase(java.util.Locale.ROOT)) {
        case "RANGE", "RANGE COLUMNS" -> PartitionMethod.RANGE;
        case "LIST", "LIST COLUMNS" -> PartitionMethod.LIST;
        case "HASH" -> PartitionMethod.HASH;
        case "KEY" -> PartitionMethod.KEY;
        case "LINEAR HASH" -> PartitionMethod.LINEAR_HASH;
        case "LINEAR KEY" -> PartitionMethod.LINEAR_KEY;
        default -> PartitionMethod.OTHER;
        };
    }


    @Override
    public List<CheckConstraint> getAllCheckConstraints(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT cc.CONSTRAINT_NAME, cc.CHECK_CLAUSE, tc.TABLE_NAME
                FROM information_schema.CHECK_CONSTRAINTS cc
                JOIN information_schema.TABLE_CONSTRAINTS tc
                    ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                WHERE cc.CONSTRAINT_SCHEMA = ? AND tc.CONSTRAINT_TYPE = 'CHECK'
                ORDER BY tc.TABLE_NAME, cc.CONSTRAINT_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<CheckConstraint> constraints = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("CONSTRAINT_NAME");
                    String checkClause = rs.getString("CHECK_CLAUSE");
                    String tableName = rs.getString("TABLE_NAME");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference tableRef = new TableReference(oSchema, tableName);

                    constraints.add(new CheckConstraintRecord(constraintName, tableRef, checkClause));
                }
            }
        } catch (SQLException e) {
            // CHECK_CONSTRAINTS is only available in MySQL 8.0.16+
            LOGGER.debug("Could not query CHECK_CONSTRAINTS (requires MySQL 8.0.16+)", e);
        }
        return List.copyOf(constraints);
    }


    @Override
    public List<CheckConstraint> getCheckConstraints(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        String sql = """
                SELECT cc.CONSTRAINT_NAME, cc.CHECK_CLAUSE, tc.TABLE_NAME
                FROM information_schema.CHECK_CONSTRAINTS cc
                JOIN information_schema.TABLE_CONSTRAINTS tc
                    ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                WHERE cc.CONSTRAINT_SCHEMA = ? AND tc.TABLE_NAME = ? AND tc.CONSTRAINT_TYPE = 'CHECK'
                ORDER BY cc.CONSTRAINT_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<CheckConstraint> constraints = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("CONSTRAINT_NAME");
                    String checkClause = rs.getString("CHECK_CLAUSE");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference tableRef = new TableReference(oSchema, tableName);

                    constraints.add(new CheckConstraintRecord(constraintName, tableRef, checkClause));
                }
            }
        } catch (SQLException e) {
            // CHECK_CONSTRAINTS is only available in MySQL 8.0.16+
            LOGGER.debug("Could not query CHECK_CONSTRAINTS (requires MySQL 8.0.16+)", e);
        }
        return List.copyOf(constraints);
    }


    @Override
    public List<UniqueConstraint> getAllUniqueConstraints(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT tc.CONSTRAINT_NAME, tc.TABLE_NAME, kcu.COLUMN_NAME, kcu.ORDINAL_POSITION
                FROM information_schema.TABLE_CONSTRAINTS tc
                JOIN information_schema.KEY_COLUMN_USAGE kcu
                    ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.CONSTRAINT_TYPE = 'UNIQUE' AND tc.TABLE_SCHEMA = ?
                ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
                """;
        return readUniqueConstraints(connection, sql, schema, null);
    }


    @Override
    public List<UniqueConstraint> getUniqueConstraints(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        String sql = """
                SELECT tc.CONSTRAINT_NAME, tc.TABLE_NAME, kcu.COLUMN_NAME, kcu.ORDINAL_POSITION
                FROM information_schema.TABLE_CONSTRAINTS tc
                JOIN information_schema.KEY_COLUMN_USAGE kcu
                    ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.CONSTRAINT_TYPE = 'UNIQUE' AND tc.TABLE_SCHEMA = ? AND tc.TABLE_NAME = ?
                ORDER BY tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
                """;
        return readUniqueConstraints(connection, sql, schema, tableName);
    }


    @Override
    public Optional<List<PrimaryKey>> getAllPrimaryKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, kcu.COLUMN_NAME, kcu.ORDINAL_POSITION
                FROM information_schema.TABLE_CONSTRAINTS tc
                JOIN information_schema.KEY_COLUMN_USAGE kcu
                    ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY' AND tc.TABLE_SCHEMA = ?
                ORDER BY tc.TABLE_NAME, kcu.ORDINAL_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        Map<String, PkBuilder> pkMap = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("CONSTRAINT_NAME");
                    String tableName = rs.getString("TABLE_NAME");
                    String columnName = rs.getString("COLUMN_NAME");

                    String key = tableName + "." + constraintName;
                    pkMap.computeIfAbsent(key, k -> new PkBuilder(tableName, constraintName, schemaName))
                            .addColumn(columnName);
                }
            }
        }
        List<PrimaryKey> result = new ArrayList<>();
        for (PkBuilder builder : pkMap.values()) {
            result.add(builder.build());
        }
        return Optional.of(List.copyOf(result));
    }


    @Override
    public Optional<List<ImportedKey>> getAllImportedKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT tc.CONSTRAINT_NAME AS FK_NAME, kcu.TABLE_NAME AS FK_TABLE, kcu.COLUMN_NAME AS FK_COLUMN,
                        kcu.REFERENCED_TABLE_NAME AS PK_TABLE, kcu.REFERENCED_COLUMN_NAME AS PK_COLUMN,
                        kcu.ORDINAL_POSITION AS KEY_SEQ, rc.DELETE_RULE, rc.UPDATE_RULE
                FROM information_schema.TABLE_CONSTRAINTS tc
                JOIN information_schema.KEY_COLUMN_USAGE kcu
                    ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME AND tc.TABLE_NAME = kcu.TABLE_NAME
                JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
                    ON tc.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                WHERE tc.CONSTRAINT_TYPE = 'FOREIGN KEY' AND tc.TABLE_SCHEMA = ?
                ORDER BY kcu.TABLE_NAME, tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        List<ImportedKey> importedKeys = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    importedKeys.add(readImportedKey(rs, schemaName));
                }
            }
        }
        return Optional.of(List.copyOf(importedKeys));
    }


    @Override
    public Optional<List<ImportedKey>> getAllExportedKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        // Symmetric to getAllImportedKeys: find FKs that reference tables in this
        // schema.
        String sql = """
                SELECT tc.CONSTRAINT_NAME AS FK_NAME, kcu.TABLE_NAME AS FK_TABLE, kcu.COLUMN_NAME AS FK_COLUMN,
                        kcu.REFERENCED_TABLE_NAME AS PK_TABLE, kcu.REFERENCED_COLUMN_NAME AS PK_COLUMN,
                        kcu.ORDINAL_POSITION AS KEY_SEQ, rc.DELETE_RULE, rc.UPDATE_RULE
                FROM information_schema.TABLE_CONSTRAINTS tc
                JOIN information_schema.KEY_COLUMN_USAGE kcu
                    ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME AND tc.TABLE_NAME = kcu.TABLE_NAME
                JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
                    ON tc.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                WHERE tc.CONSTRAINT_TYPE = 'FOREIGN KEY' AND kcu.REFERENCED_TABLE_SCHEMA = ?
                ORDER BY kcu.REFERENCED_TABLE_NAME, tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        List<ImportedKey> exportedKeys = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    exportedKeys.add(readImportedKey(rs, schemaName));
                }
            }
        }
        return Optional.of(List.copyOf(exportedKeys));
    }


    @Override
    public Optional<List<IndexInfo>> getAllIndexInfo(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX, NON_UNIQUE, INDEX_TYPE, CARDINALITY
                FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """;
        String schemaName = resolveSchema(schema, connection);
        // Group by TABLE_NAME then by INDEX_NAME
        Map<String, Map<String, List<IndexInfoItem>>> tableIndexMap = new LinkedHashMap<>();
        Map<String, TableReference> tableRefs = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String indexName = rs.getString("INDEX_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    int seqInIndex = rs.getInt("SEQ_IN_INDEX");
                    boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                    String indexType = rs.getString("INDEX_TYPE");
                    long cardinality = rs.getLong("CARDINALITY");

                    IndexInfoItem.IndexType mappedType = mapMySqlIndexType(indexType);

                    TableReference tableRef = tableRefs.computeIfAbsent(tableName, k -> {
                        Optional<SchemaReference> oSchema = Optional
                                .of(new SchemaReference(Optional.empty(), schemaName));
                        return new TableReference(oSchema, k);
                    });

                    Optional<ColumnReference> colRef = Optional.ofNullable(columnName)
                            .map(cn -> new ColumnReference(Optional.of(tableRef), cn));

                    IndexInfoItem item = new IndexInfoItemRecord(Optional.ofNullable(indexName), mappedType, colRef,
                            seqInIndex, Optional.empty(), // MySQL STATISTICS doesn't expose ASC/DESC
                            cardinality, 0L, // pages not available
                            Optional.empty(), // filter condition
                            !nonUnique);

                    tableIndexMap.computeIfAbsent(tableName, k -> new LinkedHashMap<>())
                            .computeIfAbsent(indexName, k -> new ArrayList<>()).add(item);
                }
            }
        }
        List<IndexInfo> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<IndexInfoItem>>> tableEntry : tableIndexMap.entrySet()) {
            List<IndexInfoItem> allItems = new ArrayList<>();
            for (List<IndexInfoItem> indexItems : tableEntry.getValue().values()) {
                allItems.addAll(indexItems);
            }
            result.add(new IndexInfoRecord(tableRefs.get(tableEntry.getKey()), List.copyOf(allItems)));
        }
        return Optional.of(List.copyOf(result));
    }


    @Override
    public List<ViewDefinition> getAllViewDefinitions(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT TABLE_NAME, VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<ViewDefinition> views = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String viewDef = rs.getString("VIEW_DEFINITION");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference viewRef = new TableReference(oSchema, tableName, "VIEW");

                    views.add(new ViewDefinitionRecord(viewRef, Optional.ofNullable(viewDef), Optional.empty()));
                }
            }
        }
        return List.copyOf(views);
    }


    @Override
    public List<Procedure> getAllProcedures(Connection connection, String catalog, String schema) throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        Map<String, List<ProcedureColumn>> paramMap = loadProcedureColumns(connection, schemaName);

        String sql = """
                SELECT ROUTINE_NAME, SPECIFIC_NAME, ROUTINE_COMMENT, ROUTINE_DEFINITION
                FROM information_schema.ROUTINES
                WHERE ROUTINE_SCHEMA = ? AND ROUTINE_TYPE = 'PROCEDURE'
                ORDER BY ROUTINE_NAME
                """;
        List<Procedure> procedures = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String routineName = rs.getString("ROUTINE_NAME");
                    String specificName = rs.getString("SPECIFIC_NAME");
                    String remarks = rs.getString("ROUTINE_COMMENT");
                    String body = rs.getString("ROUTINE_DEFINITION");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    List<ProcedureColumn> cols = paramMap.getOrDefault(specificName, List.of());

                    Optional<String> fullDef = showCreateRoutine(connection, "PROCEDURE", schemaName, routineName);

                    procedures.add(new ProcedureRecord(new ProcedureReference(oSchema, routineName, specificName),
                            Procedure.ProcedureType.NO_RESULT, Optional.ofNullable(remarks), cols,
                            Optional.ofNullable(body), fullDef, Optional.empty()));
                }
            }
        }
        return List.copyOf(procedures);
    }


    @Override
    public List<Function> getAllFunctions(Connection connection, String catalog, String schema) throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        Map<String, List<FunctionColumn>> paramMap = loadFunctionColumns(connection, schemaName);

        String sql = """
                SELECT ROUTINE_NAME, SPECIFIC_NAME, ROUTINE_COMMENT, ROUTINE_DEFINITION
                FROM information_schema.ROUTINES
                WHERE ROUTINE_SCHEMA = ? AND ROUTINE_TYPE = 'FUNCTION'
                ORDER BY ROUTINE_NAME
                """;
        List<Function> functions = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String routineName = rs.getString("ROUTINE_NAME");
                    String specificName = rs.getString("SPECIFIC_NAME");
                    String remarks = rs.getString("ROUTINE_COMMENT");
                    String body = rs.getString("ROUTINE_DEFINITION");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    List<FunctionColumn> cols = paramMap.getOrDefault(specificName, List.of());

                    Optional<String> fullDef = showCreateRoutine(connection, "FUNCTION", schemaName, routineName);

                    functions.add(new FunctionRecord(new FunctionReference(oSchema, routineName, specificName),
                            Function.FunctionType.NO_TABLE, Optional.ofNullable(remarks), cols,
                            Optional.ofNullable(body), fullDef, Optional.empty()));
                }
            }
        }
        return List.copyOf(functions);
    }


    private Optional<String> showCreateRoutine(Connection connection, String kind, String schemaName, String name) {
        String quotedSchema = "`" + schemaName.replace("`", "``") + "`";
        String quotedName = "`" + name.replace("`", "``") + "`";
        String sql = "SHOW CREATE " + kind + " " + quotedSchema + "." + quotedName;
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String col = "Create " + kind.charAt(0) + kind.substring(1).toLowerCase();
                String def = rs.getString(col);
                return Optional.ofNullable(def);
            }
        } catch (SQLException e) {
            LOGGER.debug("Could not retrieve DDL for {} {}.{}: {}", kind, schemaName, name, e.getMessage());
        }
        return Optional.empty();
    }


    private Map<String, List<ProcedureColumn>> loadProcedureColumns(Connection connection, String schemaName)
            throws SQLException {
        String sql = """
                SELECT SPECIFIC_NAME, PARAMETER_NAME, PARAMETER_MODE, DATA_TYPE,
                        ORDINAL_POSITION, NUMERIC_PRECISION, NUMERIC_SCALE
                FROM information_schema.PARAMETERS
                WHERE SPECIFIC_SCHEMA = ? AND ORDINAL_POSITION > 0
                ORDER BY SPECIFIC_NAME, ORDINAL_POSITION
                """;
        Map<String, List<ProcedureColumn>> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String specificName = rs.getString("SPECIFIC_NAME");
                    String paramName = rs.getString("PARAMETER_NAME");
                    String paramMode = rs.getString("PARAMETER_MODE");
                    String dataType = rs.getString("DATA_TYPE");
                    int ordinalPosition = rs.getInt("ORDINAL_POSITION");
                    int precision = rs.getInt("NUMERIC_PRECISION");
                    boolean precisionNull = rs.wasNull();
                    int scale = rs.getInt("NUMERIC_SCALE");
                    boolean scaleNull = rs.wasNull();

                    ProcedureColumn.ColumnType colType = mapProcedureColumnType(paramMode);
                    JDBCType jdbcType = mapMySqlJdbcType(dataType);

                    ProcedureColumn col = new ProcedureColumnRecord(paramName != null ? paramName : "", colType,
                            jdbcType, dataType != null ? dataType : "",
                            precisionNull ? OptionalInt.empty() : OptionalInt.of(precision),
                            scaleNull ? OptionalInt.empty() : OptionalInt.of(scale), OptionalInt.of(10),
                            ProcedureColumn.Nullability.UNKNOWN, Optional.empty(), Optional.empty(), ordinalPosition);

                    result.computeIfAbsent(specificName, k -> new ArrayList<>()).add(col);
                }
            }
        }
        return result;
    }


    private Map<String, List<FunctionColumn>> loadFunctionColumns(Connection connection, String schemaName)
            throws SQLException {
        String sql = """
                SELECT SPECIFIC_NAME, PARAMETER_NAME, PARAMETER_MODE, DATA_TYPE,
                        ORDINAL_POSITION, NUMERIC_PRECISION, NUMERIC_SCALE
                FROM information_schema.PARAMETERS
                WHERE SPECIFIC_SCHEMA = ?
                ORDER BY SPECIFIC_NAME, ORDINAL_POSITION
                """;
        Map<String, List<FunctionColumn>> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String specificName = rs.getString("SPECIFIC_NAME");
                    String paramName = rs.getString("PARAMETER_NAME");
                    String paramMode = rs.getString("PARAMETER_MODE");
                    String dataType = rs.getString("DATA_TYPE");
                    int ordinalPosition = rs.getInt("ORDINAL_POSITION");
                    int precision = rs.getInt("NUMERIC_PRECISION");
                    boolean precisionNull = rs.wasNull();
                    int scale = rs.getInt("NUMERIC_SCALE");
                    boolean scaleNull = rs.wasNull();

                    FunctionColumn.ColumnType colType = mapFunctionColumnType(paramMode);
                    JDBCType jdbcType = mapMySqlJdbcType(dataType);

                    FunctionColumn col = new FunctionColumnRecord(paramName != null ? paramName : "", colType, jdbcType,
                            dataType != null ? dataType : "",
                            precisionNull ? OptionalInt.empty() : OptionalInt.of(precision),
                            scaleNull ? OptionalInt.empty() : OptionalInt.of(scale), OptionalInt.of(10),
                            FunctionColumn.Nullability.UNKNOWN, Optional.empty(), OptionalInt.empty(), ordinalPosition);

                    result.computeIfAbsent(specificName, k -> new ArrayList<>()).add(col);
                }
            }
        }
        return result;
    }


    private static ProcedureColumn.ColumnType mapProcedureColumnType(String mode) {
        if (mode == null) {
            return ProcedureColumn.ColumnType.UNKNOWN;
        }
        return switch (mode.toUpperCase()) {
        case "IN" -> ProcedureColumn.ColumnType.IN;
        case "OUT" -> ProcedureColumn.ColumnType.OUT;
        case "INOUT" -> ProcedureColumn.ColumnType.INOUT;
        default -> ProcedureColumn.ColumnType.UNKNOWN;
        };
    }


    private static FunctionColumn.ColumnType mapFunctionColumnType(String mode) {
        if (mode == null) {
            return FunctionColumn.ColumnType.UNKNOWN;
        }
        return switch (mode.toUpperCase()) {
        case "IN" -> FunctionColumn.ColumnType.IN;
        case "OUT" -> FunctionColumn.ColumnType.OUT;
        case "INOUT" -> FunctionColumn.ColumnType.INOUT;
        default -> FunctionColumn.ColumnType.UNKNOWN;
        };
    }


    private static JDBCType mapMySqlJdbcType(String dataType) {
        if (dataType == null) {
            return JDBCType.OTHER;
        }
        try {
            return JDBCType.valueOf(dataType.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return switch (dataType.toUpperCase()) {
            case "INT" -> JDBCType.INTEGER;
            case "MEDIUMINT" -> JDBCType.INTEGER;
            case "TINYINT" -> JDBCType.TINYINT;
            case "BOOL", "BOOLEAN" -> JDBCType.BOOLEAN;
            case "TEXT", "MEDIUMTEXT", "LONGTEXT", "TINYTEXT" -> JDBCType.VARCHAR;
            case "DATETIME" -> JDBCType.TIMESTAMP;
            case "ENUM", "SET", "JSON", "GEOMETRY" -> JDBCType.OTHER;
            default -> JDBCType.OTHER;
            };
        }
    }


    private Trigger readTrigger(ResultSet rs, String schemaName) throws SQLException {
        String triggerName = rs.getString("TRIGGER_NAME");
        String tableName = rs.getString("EVENT_OBJECT_TABLE");
        String actionTiming = rs.getString("ACTION_TIMING");
        String eventManipulation = rs.getString("EVENT_MANIPULATION");
        String actionStatement = rs.getString("ACTION_STATEMENT");
        String actionOrientation = rs.getString("ACTION_ORIENTATION");

        Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
        TableReference tableRef = new TableReference(oSchema, tableName);

        TriggerTiming timing = mapTriggerTiming(actionTiming);
        TriggerEvent event = mapTriggerEvent(eventManipulation);

        return new TriggerRecord(new TriggerReference(tableRef, triggerName), timing, event,
                Optional.ofNullable(actionStatement), Optional.empty(), Optional.ofNullable(actionOrientation));
    }


    private ImportedKey readImportedKey(ResultSet rs, String schemaName) throws SQLException {
        String fkName = rs.getString("FK_NAME");
        String fkTable = rs.getString("FK_TABLE");
        String fkColumn = rs.getString("FK_COLUMN");
        String pkTable = rs.getString("PK_TABLE");
        String pkColumn = rs.getString("PK_COLUMN");
        int keySeq = rs.getInt("KEY_SEQ");
        String deleteRule = rs.getString("DELETE_RULE");
        String updateRule = rs.getString("UPDATE_RULE");

        // FK side
        Optional<SchemaReference> fkSchemaRef = Optional.of(new SchemaReference(Optional.empty(), schemaName));
        TableReference fkTableRef = new TableReference(fkSchemaRef, fkTable);
        ColumnReference fkColRef = new ColumnReference(Optional.of(fkTableRef), fkColumn);

        // PK side
        Optional<SchemaReference> pkSchemaRef = Optional.of(new SchemaReference(Optional.empty(), schemaName));
        TableReference pkTableRef = new TableReference(pkSchemaRef, pkTable);
        ColumnReference pkColRef = new ColumnReference(Optional.of(pkTableRef), pkColumn);

        return new ImportedKeyRecord(pkColRef, fkColRef, fkName, keySeq, mapReferentialAction(updateRule),
                mapReferentialAction(deleteRule), Optional.empty(), ImportedKey.Deferrability.NOT_DEFERRABLE);
    }


    private List<UniqueConstraint> readUniqueConstraints(Connection connection, String sql, String schema,
            String tableName) throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        Map<String, UcBuilder> ucMap = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            if (tableName != null) {
                ps.setString(2, tableName);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("CONSTRAINT_NAME");
                    String table = rs.getString("TABLE_NAME");
                    String columnName = rs.getString("COLUMN_NAME");

                    String key = table + "." + constraintName;
                    ucMap.computeIfAbsent(key, k -> new UcBuilder(table, constraintName, schemaName))
                            .addColumn(columnName);
                }
            }
        }
        List<UniqueConstraint> result = new ArrayList<>();
        for (UcBuilder builder : ucMap.values()) {
            result.add(builder.build());
        }
        return List.copyOf(result);
    }


    private String resolveSchema(String schema, Connection connection) throws SQLException {
        if (schema != null) {
            return schema;
        }
        // MySQL uses catalog as database name
        String catalog = connection.getCatalog();
        if (catalog != null) {
            return catalog;
        }
        return connection.getSchema() != null ? connection.getSchema() : "";
    }


    private static TriggerTiming mapTriggerTiming(String timing) {
        if (timing == null) {
            return TriggerTiming.AFTER;
        }
        return switch (timing.toUpperCase()) {
        case "BEFORE" -> TriggerTiming.BEFORE;
        default -> TriggerTiming.AFTER;
        };
    }


    private static TriggerEvent mapTriggerEvent(String event) {
        if (event == null) {
            return TriggerEvent.INSERT;
        }
        return switch (event.toUpperCase()) {
        case "UPDATE" -> TriggerEvent.UPDATE;
        case "DELETE" -> TriggerEvent.DELETE;
        default -> TriggerEvent.INSERT;
        };
    }


    private static ImportedKey.ReferentialAction mapReferentialAction(String action) {
        if (action == null) {
            return ImportedKey.ReferentialAction.NO_ACTION;
        }
        return switch (action.toUpperCase()) {
        case "CASCADE" -> ImportedKey.ReferentialAction.CASCADE;
        case "SET NULL" -> ImportedKey.ReferentialAction.SET_NULL;
        case "SET DEFAULT" -> ImportedKey.ReferentialAction.SET_DEFAULT;
        case "RESTRICT" -> ImportedKey.ReferentialAction.RESTRICT;
        default -> ImportedKey.ReferentialAction.NO_ACTION;
        };
    }


    private static IndexInfoItem.IndexType mapMySqlIndexType(String indexType) {
        if (indexType == null) {
            return IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        }
        return switch (indexType.toUpperCase()) {
        case "HASH" -> IndexInfoItem.IndexType.TABLE_INDEX_HASHED;
        case "BTREE", "FULLTEXT", "SPATIAL" -> IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        default -> IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        };
    }


    // Builder for aggregating composite primary key columns
    private static class PkBuilder {
        private final String tableName;
        private final String constraintName;
        private final String schemaName;
        private final List<String> columns = new ArrayList<>();

        PkBuilder(String tableName, String constraintName, String schemaName) {
            this.tableName = tableName;
            this.constraintName = constraintName;
            this.schemaName = schemaName;
        }

        void addColumn(String columnName) {
            columns.add(columnName);
        }

        PrimaryKey build() {
            Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
            TableReference tableRef = new TableReference(oSchema, tableName);
            List<ColumnReference> colRefs = columns.stream()
                    .map(col -> (ColumnReference) new ColumnReference(Optional.of(tableRef), col)).toList();
            return new PrimaryKeyRecord(tableRef, colRefs, Optional.of(constraintName));
        }
    }


    // Builder for aggregating composite unique constraint columns
    private static class UcBuilder {
        private final String tableName;
        private final String constraintName;
        private final String schemaName;
        private final List<String> columns = new ArrayList<>();

        UcBuilder(String tableName, String constraintName, String schemaName) {
            this.tableName = tableName;
            this.constraintName = constraintName;
            this.schemaName = schemaName;
        }

        void addColumn(String columnName) {
            columns.add(columnName);
        }

        UniqueConstraint build() {
            Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
            TableReference tableRef = new TableReference(oSchema, tableName);
            List<ColumnReference> colRefs = columns.stream()
                    .map(col -> (ColumnReference) new ColumnReference(Optional.of(tableRef), col)).toList();
            return new UniqueConstraintRecord(constraintName, tableRef, colRefs);
        }
    }
}
