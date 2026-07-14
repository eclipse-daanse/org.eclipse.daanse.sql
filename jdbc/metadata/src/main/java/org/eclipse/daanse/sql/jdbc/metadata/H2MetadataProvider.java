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
import org.eclipse.daanse.sql.model.schema.CatalogReference;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionReference;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Sequence;
import org.eclipse.daanse.sql.jdbc.api.schema.SequenceReference;
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
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ProcedureColumnRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ProcedureRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.SequenceRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TriggerRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.UniqueConstraintRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ViewDefinitionRecord;

/**
 * The H2 system-catalog/{@code information_schema} reader — the
 * {@link MetadataProvider} overrides extracted 1:1 from the SQL dialect (reading only,
 * no spelling).
 */
public class H2MetadataProvider implements MetadataProvider {


    @Override
    public List<Trigger> getAllTriggers(Connection connection, String catalog, String schema) throws SQLException {
        String sql = """
                SELECT TRIGGER_NAME, EVENT_OBJECT_TABLE, ACTION_TIMING,
                       EVENT_MANIPULATION, JAVA_CLASS, ACTION_ORIENTATION,
                       EVENT_OBJECT_SCHEMA, EVENT_OBJECT_CATALOG
                FROM INFORMATION_SCHEMA.TRIGGERS
                WHERE TRIGGER_SCHEMA = ?
                ORDER BY EVENT_OBJECT_TABLE, TRIGGER_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Trigger> triggers = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    triggers.add(readTrigger(rs));
                }
            }
        }
        return List.copyOf(triggers);
    }


    @Override
    public List<Trigger> getTriggers(Connection connection, String catalog, String schema, String tableName)
            throws SQLException {
        String sql = """
                SELECT TRIGGER_NAME, EVENT_OBJECT_TABLE, ACTION_TIMING,
                       EVENT_MANIPULATION, JAVA_CLASS, ACTION_ORIENTATION,
                       EVENT_OBJECT_SCHEMA, EVENT_OBJECT_CATALOG
                FROM INFORMATION_SCHEMA.TRIGGERS
                WHERE TRIGGER_SCHEMA = ? AND EVENT_OBJECT_TABLE = ?
                ORDER BY TRIGGER_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Trigger> triggers = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    triggers.add(readTrigger(rs));
                }
            }
        }
        return List.copyOf(triggers);
    }


    @Override
    public List<Sequence> getAllSequences(Connection connection, String catalog, String schema) throws SQLException {
        String sql = """
                SELECT SEQUENCE_NAME, START_VALUE, INCREMENT, MINIMUM_VALUE,
                       MAXIMUM_VALUE, CYCLE_OPTION, CACHE, DATA_TYPE
                FROM INFORMATION_SCHEMA.SEQUENCES
                WHERE SEQUENCE_SCHEMA = ?
                ORDER BY SEQUENCE_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Sequence> sequences = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("SEQUENCE_NAME");
                    long startValue = rs.getLong("START_VALUE");
                    long increment = rs.getLong("INCREMENT");
                    long minValue = rs.getLong("MINIMUM_VALUE");
                    Optional<Long> oMinValue = rs.wasNull() ? Optional.empty() : Optional.of(minValue);
                    long maxValue = rs.getLong("MAXIMUM_VALUE");
                    Optional<Long> oMaxValue = rs.wasNull() ? Optional.empty() : Optional.of(maxValue);
                    String cycleOption = rs.getString("CYCLE_OPTION");
                    boolean cycle = "YES".equalsIgnoreCase(cycleOption);
                    long cacheSize = rs.getLong("CACHE");
                    Optional<Long> oCacheSize = rs.wasNull() ? Optional.empty() : Optional.of(cacheSize);
                    String dataType = rs.getString("DATA_TYPE");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));

                    sequences.add(new SequenceRecord(new SequenceReference(oSchema, name), startValue, increment,
                            oMinValue, oMaxValue, cycle, oCacheSize, Optional.ofNullable(dataType)));
                }
            }
        }
        return List.copyOf(sequences);
    }


    @Override
    public List<CheckConstraint> getAllCheckConstraints(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT cc.CHECK_CLAUSE, tc.CONSTRAINT_NAME, tc.TABLE_NAME
                FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                  ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
                  AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                WHERE tc.TABLE_SCHEMA = ?
                  AND tc.CONSTRAINT_TYPE = 'CHECK'
                ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<CheckConstraint> constraints = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String checkClause = rs.getString("CHECK_CLAUSE");
                    String constraintName = rs.getString("CONSTRAINT_NAME");
                    String tableName = rs.getString("TABLE_NAME");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference tableRef = new TableReference(oSchema, tableName);

                    constraints.add(new CheckConstraintRecord(constraintName, tableRef, checkClause));
                }
            }
        }
        return List.copyOf(constraints);
    }


    @Override
    public List<CheckConstraint> getCheckConstraints(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        String sql = """
                SELECT cc.CHECK_CLAUSE, tc.CONSTRAINT_NAME, tc.TABLE_NAME
                FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                  ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
                  AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                WHERE tc.TABLE_SCHEMA = ?
                  AND tc.TABLE_NAME = ?
                  AND tc.CONSTRAINT_TYPE = 'CHECK'
                ORDER BY tc.CONSTRAINT_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<CheckConstraint> constraints = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String checkClause = rs.getString("CHECK_CLAUSE");
                    String constraintName = rs.getString("CONSTRAINT_NAME");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference tableRef = new TableReference(oSchema, tableName);

                    constraints.add(new CheckConstraintRecord(constraintName, tableRef, checkClause));
                }
            }
        }
        return List.copyOf(constraints);
    }


    @Override
    public List<UniqueConstraint> getAllUniqueConstraints(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT tc.CONSTRAINT_NAME, tc.TABLE_NAME,
                       kcu.COLUMN_NAME, kcu.ORDINAL_POSITION
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                  ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
                  AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                  AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.CONSTRAINT_TYPE = 'UNIQUE'
                  AND tc.TABLE_SCHEMA = ?
                ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
                """;
        return readUniqueConstraints(connection, sql, schema, null);
    }


    @Override
    public List<UniqueConstraint> getUniqueConstraints(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        String sql = """
                SELECT tc.CONSTRAINT_NAME, tc.TABLE_NAME,
                       kcu.COLUMN_NAME, kcu.ORDINAL_POSITION
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                  ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
                  AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                  AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.CONSTRAINT_TYPE = 'UNIQUE'
                  AND tc.TABLE_SCHEMA = ?
                  AND tc.TABLE_NAME = ?
                ORDER BY tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
                """;
        return readUniqueConstraints(connection, sql, schema, tableName);
    }


    @Override
    public Optional<List<PrimaryKey>> getAllPrimaryKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT tc.CONSTRAINT_NAME, tc.TABLE_NAME,
                       kcu.COLUMN_NAME, kcu.ORDINAL_POSITION
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                  ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
                  AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                  AND tc.TABLE_NAME = kcu.TABLE_NAME
                WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY'
                  AND tc.TABLE_SCHEMA = ?
                ORDER BY tc.TABLE_NAME, kcu.ORDINAL_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        // Group by table+constraint to build composite PKs
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
                SELECT fk_tc.CONSTRAINT_NAME AS FK_NAME,
                       fk_kcu.TABLE_NAME AS FK_TABLE,
                       fk_kcu.COLUMN_NAME AS FK_COLUMN,
                       fk_kcu.TABLE_SCHEMA AS FK_SCHEMA,
                       fk_kcu.TABLE_CATALOG AS FK_CATALOG,
                       pk_kcu.TABLE_NAME AS PK_TABLE,
                       pk_kcu.COLUMN_NAME AS PK_COLUMN,
                       pk_kcu.TABLE_SCHEMA AS PK_SCHEMA,
                       pk_kcu.TABLE_CATALOG AS PK_CATALOG,
                       fk_kcu.ORDINAL_POSITION AS KEY_SEQ,
                       rc.UPDATE_RULE, rc.DELETE_RULE,
                       rc.UNIQUE_CONSTRAINT_NAME AS PK_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS fk_tc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE fk_kcu
                  ON fk_tc.CONSTRAINT_SCHEMA = fk_kcu.CONSTRAINT_SCHEMA
                  AND fk_tc.CONSTRAINT_NAME = fk_kcu.CONSTRAINT_NAME
                JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
                  ON fk_tc.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
                  AND fk_tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE pk_kcu
                  ON rc.UNIQUE_CONSTRAINT_SCHEMA = pk_kcu.CONSTRAINT_SCHEMA
                  AND rc.UNIQUE_CONSTRAINT_NAME = pk_kcu.CONSTRAINT_NAME
                  AND fk_kcu.POSITION_IN_UNIQUE_CONSTRAINT = pk_kcu.ORDINAL_POSITION
                WHERE fk_tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
                  AND fk_tc.TABLE_SCHEMA = ?
                ORDER BY fk_kcu.TABLE_NAME, fk_tc.CONSTRAINT_NAME, fk_kcu.ORDINAL_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        List<ImportedKey> importedKeys = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    importedKeys.add(readImportedKey(rs));
                }
            }
        }
        return Optional.of(List.copyOf(importedKeys));
    }


    @Override
    public Optional<List<ImportedKey>> getAllExportedKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        // Symmetric to getAllImportedKeys: filter by the referenced (PK-side) schema
        // rather than the referencing (FK-side) schema.
        String sql = """
                SELECT fk_tc.CONSTRAINT_NAME AS FK_NAME,
                       fk_kcu.TABLE_NAME AS FK_TABLE,
                       fk_kcu.COLUMN_NAME AS FK_COLUMN,
                       fk_kcu.TABLE_SCHEMA AS FK_SCHEMA,
                       fk_kcu.TABLE_CATALOG AS FK_CATALOG,
                       pk_kcu.TABLE_NAME AS PK_TABLE,
                       pk_kcu.COLUMN_NAME AS PK_COLUMN,
                       pk_kcu.TABLE_SCHEMA AS PK_SCHEMA,
                       pk_kcu.TABLE_CATALOG AS PK_CATALOG,
                       fk_kcu.ORDINAL_POSITION AS KEY_SEQ,
                       rc.UPDATE_RULE, rc.DELETE_RULE,
                       rc.UNIQUE_CONSTRAINT_NAME AS PK_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS fk_tc
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE fk_kcu
                  ON fk_tc.CONSTRAINT_SCHEMA = fk_kcu.CONSTRAINT_SCHEMA
                  AND fk_tc.CONSTRAINT_NAME = fk_kcu.CONSTRAINT_NAME
                JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
                  ON fk_tc.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
                  AND fk_tc.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE pk_kcu
                  ON rc.UNIQUE_CONSTRAINT_SCHEMA = pk_kcu.CONSTRAINT_SCHEMA
                  AND rc.UNIQUE_CONSTRAINT_NAME = pk_kcu.CONSTRAINT_NAME
                  AND fk_kcu.POSITION_IN_UNIQUE_CONSTRAINT = pk_kcu.ORDINAL_POSITION
                WHERE fk_tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
                  AND pk_kcu.TABLE_SCHEMA = ?
                ORDER BY pk_kcu.TABLE_NAME, fk_tc.CONSTRAINT_NAME, fk_kcu.ORDINAL_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        List<ImportedKey> exportedKeys = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    exportedKeys.add(readImportedKey(rs));
                }
            }
        }
        return Optional.of(List.copyOf(exportedKeys));
    }


    @Override
    public Optional<List<IndexInfo>> getAllIndexInfo(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT i.TABLE_NAME, i.INDEX_NAME, i.INDEX_TYPE_NAME,
                       ic.COLUMN_NAME, ic.ORDINAL_POSITION, ic.IS_UNIQUE,
                       i.TABLE_SCHEMA, i.TABLE_CATALOG
                FROM INFORMATION_SCHEMA.INDEXES i
                JOIN INFORMATION_SCHEMA.INDEX_COLUMNS ic
                  ON i.INDEX_CATALOG = ic.INDEX_CATALOG
                  AND i.INDEX_SCHEMA = ic.INDEX_SCHEMA
                  AND i.INDEX_NAME = ic.INDEX_NAME
                  AND i.TABLE_NAME = ic.TABLE_NAME
                WHERE i.TABLE_SCHEMA = ?
                ORDER BY i.TABLE_NAME, i.INDEX_NAME, ic.ORDINAL_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        // Group index items by table
        Map<String, List<IndexInfoItem>> tableIndexes = new LinkedHashMap<>();
        Map<String, TableReference> tableRefs = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String indexName = rs.getString("INDEX_NAME");
                    String indexTypeName = rs.getString("INDEX_TYPE_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    int ordinalPosition = rs.getInt("ORDINAL_POSITION");
                    boolean isUnique = rs.getBoolean("IS_UNIQUE");

                    TableReference tableRef = tableRefs.computeIfAbsent(tableName, k -> {
                        Optional<SchemaReference> oSchema = Optional
                                .of(new SchemaReference(Optional.empty(), schemaName));
                        return new TableReference(oSchema, k);
                    });

                    Optional<ColumnReference> colRef = Optional.ofNullable(columnName)
                            .map(cn -> new ColumnReference(Optional.of(tableRef), cn));

                    IndexInfoItem.IndexType indexType = mapH2IndexType(indexTypeName);

                    IndexInfoItem item = new IndexInfoItemRecord(Optional.ofNullable(indexName), indexType, colRef,
                            ordinalPosition, Optional.empty(), // H2 doesn't expose ASC/DESC in
                                                               // INFORMATION_SCHEMA.INDEXES
                            0L, // cardinality not available here
                            0L, // pages not available here
                            Optional.empty(), // filter condition
                            isUnique);

                    tableIndexes.computeIfAbsent(tableName, k -> new ArrayList<>()).add(item);
                }
            }
        }
        List<IndexInfo> result = new ArrayList<>();
        for (Map.Entry<String, List<IndexInfoItem>> entry : tableIndexes.entrySet()) {
            result.add(new IndexInfoRecord(tableRefs.get(entry.getKey()), List.copyOf(entry.getValue())));
        }
        return Optional.of(List.copyOf(result));
    }


    @Override
    public List<ViewDefinition> getAllViewDefinitions(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT TABLE_NAME, VIEW_DEFINITION, TABLE_SCHEMA, TABLE_CATALOG
                FROM INFORMATION_SCHEMA.VIEWS
                WHERE TABLE_SCHEMA = ?
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
        // First, load all parameters grouped by SPECIFIC_NAME
        Map<String, List<ProcedureColumn>> paramMap = loadProcedureColumns(connection, schemaName);

        String sql = """
                SELECT ROUTINE_NAME, SPECIFIC_NAME, ROUTINE_TYPE, REMARKS, ROUTINE_DEFINITION
                FROM INFORMATION_SCHEMA.ROUTINES
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
                    String remarks = rs.getString("REMARKS");
                    String body = rs.getString("ROUTINE_DEFINITION");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    List<ProcedureColumn> cols = paramMap.getOrDefault(specificName, List.of());

                    procedures.add(new ProcedureRecord(new ProcedureReference(oSchema, routineName, specificName),
                            Procedure.ProcedureType.NO_RESULT, Optional.ofNullable(remarks), cols,
                            Optional.ofNullable(body), Optional.empty(), Optional.empty()));
                }
            }
        }
        return List.copyOf(procedures);
    }


    @Override
    public List<Function> getAllFunctions(Connection connection, String catalog, String schema) throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        // First, load all parameters grouped by SPECIFIC_NAME
        Map<String, List<FunctionColumn>> paramMap = loadFunctionColumns(connection, schemaName);

        String sql = """
                SELECT ROUTINE_NAME, SPECIFIC_NAME, ROUTINE_TYPE, REMARKS, ROUTINE_DEFINITION
                FROM INFORMATION_SCHEMA.ROUTINES
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
                    String remarks = rs.getString("REMARKS");
                    String body = rs.getString("ROUTINE_DEFINITION");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    List<FunctionColumn> cols = paramMap.getOrDefault(specificName, List.of());

                    functions.add(new FunctionRecord(new FunctionReference(oSchema, routineName, specificName),
                            Function.FunctionType.NO_TABLE, Optional.ofNullable(remarks), cols,
                            Optional.ofNullable(body), Optional.empty(), Optional.empty()));
                }
            }
        }
        return List.copyOf(functions);
    }


    private Map<String, List<ProcedureColumn>> loadProcedureColumns(Connection connection, String schemaName)
            throws SQLException {
        String sql = """
                SELECT SPECIFIC_NAME, PARAMETER_NAME, PARAMETER_MODE, DATA_TYPE,
                       ORDINAL_POSITION, NUMERIC_PRECISION, NUMERIC_SCALE,
                       PARAMETER_DEFAULT
                FROM INFORMATION_SCHEMA.PARAMETERS
                WHERE SPECIFIC_SCHEMA = ?
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
                    String paramDefault = rs.getString("PARAMETER_DEFAULT");

                    ProcedureColumn.ColumnType colType = mapProcedureColumnType(paramMode);
                    JDBCType jdbcType = mapH2DataType(dataType);

                    ProcedureColumn col = new ProcedureColumnRecord(paramName != null ? paramName : "", colType,
                            jdbcType, dataType != null ? dataType : "",
                            precisionNull ? OptionalInt.empty() : OptionalInt.of(precision),
                            scaleNull ? OptionalInt.empty() : OptionalInt.of(scale), OptionalInt.of(10),
                            ProcedureColumn.Nullability.UNKNOWN, Optional.empty(), Optional.ofNullable(paramDefault),
                            ordinalPosition);

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
                FROM INFORMATION_SCHEMA.PARAMETERS
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
                    JDBCType jdbcType = mapH2DataType(dataType);

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


    private static JDBCType mapH2DataType(String dataType) {
        if (dataType == null) {
            return JDBCType.OTHER;
        }
        try {
            return JDBCType.valueOf(dataType.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return switch (dataType.toUpperCase()) {
            case "INT", "INT4", "SIGNED" -> JDBCType.INTEGER;
            case "INT8" -> JDBCType.BIGINT;
            case "INT2" -> JDBCType.SMALLINT;
            case "FLOAT4" -> JDBCType.FLOAT;
            case "FLOAT8" -> JDBCType.DOUBLE;
            case "BOOL" -> JDBCType.BOOLEAN;
            case "STRING", "TEXT" -> JDBCType.VARCHAR;
            case "BYTEA" -> JDBCType.VARBINARY;
            case "CHARACTER VARYING" -> JDBCType.VARCHAR;
            case "CHARACTER LARGE OBJECT" -> JDBCType.CLOB;
            case "BINARY LARGE OBJECT" -> JDBCType.BLOB;
            case "BINARY VARYING" -> JDBCType.VARBINARY;
            case "DOUBLE PRECISION" -> JDBCType.DOUBLE;
            case "DECFLOAT" -> JDBCType.DECIMAL;
            case "TIMESTAMP WITH TIME ZONE" -> JDBCType.TIMESTAMP_WITH_TIMEZONE;
            case "TIME WITH TIME ZONE" -> JDBCType.TIME_WITH_TIMEZONE;
            case "INTERVAL" -> JDBCType.OTHER;
            case "GEOMETRY", "JSON", "UUID", "ENUM", "ARRAY" -> JDBCType.OTHER;
            default -> JDBCType.OTHER;
            };
        }
    }


    private Trigger readTrigger(ResultSet rs) throws SQLException {
        String triggerName = rs.getString("TRIGGER_NAME");
        String tableName = rs.getString("EVENT_OBJECT_TABLE");
        String actionTiming = rs.getString("ACTION_TIMING");
        String eventManipulation = rs.getString("EVENT_MANIPULATION");
        String actionStatement = rs.getString("JAVA_CLASS");
        String actionOrientation = rs.getString("ACTION_ORIENTATION");
        String tableSchema = rs.getString("EVENT_OBJECT_SCHEMA");

        Optional<SchemaReference> oSchema = Optional.ofNullable(tableSchema)
                .map(s -> new SchemaReference(Optional.empty(), s));
        TableReference tableRef = new TableReference(oSchema, tableName);

        TriggerTiming timing = mapTriggerTiming(actionTiming);
        TriggerEvent event = mapTriggerEvent(eventManipulation);

        return new TriggerRecord(new TriggerReference(tableRef, triggerName), timing, event,
                Optional.ofNullable(actionStatement), Optional.empty(), // H2 doesn't provide full CREATE TRIGGER DDL
                                                                        // directly
                Optional.ofNullable(actionOrientation));
    }


    private ImportedKey readImportedKey(ResultSet rs) throws SQLException {
        String fkName = rs.getString("FK_NAME");
        String fkTable = rs.getString("FK_TABLE");
        String fkColumn = rs.getString("FK_COLUMN");
        String fkSchema = rs.getString("FK_SCHEMA");
        String fkCatalog = rs.getString("FK_CATALOG");
        String pkTable = rs.getString("PK_TABLE");
        String pkColumn = rs.getString("PK_COLUMN");
        String pkSchema = rs.getString("PK_SCHEMA");
        String pkCatalog = rs.getString("PK_CATALOG");
        int keySeq = rs.getInt("KEY_SEQ");
        String updateRule = rs.getString("UPDATE_RULE");
        String deleteRule = rs.getString("DELETE_RULE");
        String pkName = rs.getString("PK_NAME");

        // FK side
        Optional<CatalogReference> fkCatRef = Optional.ofNullable(fkCatalog).map(CatalogReference::new);
        Optional<SchemaReference> fkSchemaRef = Optional.ofNullable(fkSchema)
                .map(s -> new SchemaReference(fkCatRef, s));
        TableReference fkTableRef = new TableReference(fkSchemaRef, fkTable);
        ColumnReference fkColRef = new ColumnReference(Optional.of(fkTableRef), fkColumn);

        // PK side
        Optional<CatalogReference> pkCatRef = Optional.ofNullable(pkCatalog).map(CatalogReference::new);
        Optional<SchemaReference> pkSchemaRef = Optional.ofNullable(pkSchema)
                .map(s -> new SchemaReference(pkCatRef, s));
        TableReference pkTableRef = new TableReference(pkSchemaRef, pkTable);
        ColumnReference pkColRef = new ColumnReference(Optional.of(pkTableRef), pkColumn);

        return new ImportedKeyRecord(pkColRef, fkColRef, fkName, keySeq, mapReferentialAction(updateRule),
                mapReferentialAction(deleteRule), Optional.ofNullable(pkName),
                ImportedKey.Deferrability.NOT_DEFERRABLE);
    }


    private List<UniqueConstraint> readUniqueConstraints(Connection connection, String sql, String schema,
            String tableName) throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        // Group by constraint name to collect all columns
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
        // H2 default schema is PUBLIC
        return connection.getSchema() != null ? connection.getSchema() : "PUBLIC";
    }


    private static TriggerTiming mapTriggerTiming(String timing) {
        if (timing == null) {
            return TriggerTiming.AFTER;
        }
        return switch (timing.toUpperCase()) {
        case "BEFORE" -> TriggerTiming.BEFORE;
        case "INSTEAD OF" -> TriggerTiming.INSTEAD_OF;
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


    private static IndexInfoItem.IndexType mapH2IndexType(String indexTypeName) {
        if (indexTypeName == null) {
            return IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        }
        return switch (indexTypeName.toUpperCase()) {
        case "HASH INDEX" -> IndexInfoItem.IndexType.TABLE_INDEX_HASHED;
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
