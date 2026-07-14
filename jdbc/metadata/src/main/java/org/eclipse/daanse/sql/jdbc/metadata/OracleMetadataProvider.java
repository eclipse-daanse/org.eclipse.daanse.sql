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
import java.sql.Timestamp;
import java.time.Instant;
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
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.jdbc.api.schema.ColumnPrivilege;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionReference;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.MaterializedView;
import org.eclipse.daanse.sql.jdbc.api.schema.Partition;
import org.eclipse.daanse.sql.jdbc.api.schema.PartitionMethod;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureReference;
import org.eclipse.daanse.sql.jdbc.api.schema.PseudoColumn;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Sequence;
import org.eclipse.daanse.sql.jdbc.api.schema.SequenceReference;
import org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.model.schema.TriggerReference;
import org.eclipse.daanse.sql.jdbc.api.schema.UniqueConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.UserDefinedType;
import org.eclipse.daanse.sql.jdbc.api.schema.UserDefinedTypeReference;
import org.eclipse.daanse.sql.jdbc.api.schema.ViewDefinition;
import org.eclipse.daanse.sql.jdbc.record.schema.CheckConstraintRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnPrivilegeRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.FunctionColumnRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.FunctionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ImportedKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoItemRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.MaterializedViewRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PartitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ProcedureColumnRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ProcedureRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PseudoColumnRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.SequenceRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TablePrivilegeRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TriggerRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.UniqueConstraintRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.UserDefinedTypeRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ViewDefinitionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Oracle system-catalog/{@code information_schema} reader — the
 * {@link MetadataProvider} overrides extracted 1:1 from the SQL dialect (reading only,
 * no spelling).
 */
public class OracleMetadataProvider implements MetadataProvider {


    private static final Logger LOGGER = LoggerFactory.getLogger(OracleMetadataProvider.class);


    @Override
    public List<Trigger> getAllTriggers(Connection connection, String catalog, String schema) throws SQLException {
        String sql = """
                SELECT TRIGGER_NAME, TABLE_NAME, TRIGGER_TYPE, TRIGGERING_EVENT, TRIGGER_BODY
                FROM ALL_TRIGGERS WHERE OWNER = ? ORDER BY TABLE_NAME, TRIGGER_NAME
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
                SELECT TRIGGER_NAME, TABLE_NAME, TRIGGER_TYPE, TRIGGERING_EVENT, TRIGGER_BODY
                FROM ALL_TRIGGERS WHERE OWNER = ? AND TABLE_NAME = ?
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
        String sql = """
                SELECT SEQUENCE_NAME, MIN_VALUE, MAX_VALUE, INCREMENT_BY, CYCLE_FLAG, CACHE_SIZE, LAST_NUMBER
                FROM ALL_SEQUENCES WHERE SEQUENCE_OWNER = ? ORDER BY SEQUENCE_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Sequence> sequences = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("SEQUENCE_NAME");
                    long minValue = rs.getLong("MIN_VALUE");
                    Optional<Long> oMinValue = rs.wasNull() ? Optional.empty() : Optional.of(minValue);
                    long maxValue = rs.getLong("MAX_VALUE");
                    Optional<Long> oMaxValue = rs.wasNull() ? Optional.empty() : Optional.of(maxValue);
                    long incrementBy = rs.getLong("INCREMENT_BY");
                    String cycleFlag = rs.getString("CYCLE_FLAG");
                    boolean cycle = "Y".equalsIgnoreCase(cycleFlag);
                    long cacheSize = rs.getLong("CACHE_SIZE");
                    Optional<Long> oCacheSize = rs.wasNull() ? Optional.empty() : Optional.of(cacheSize);
                    long lastNumber = rs.getLong("LAST_NUMBER");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));

                    sequences.add(new SequenceRecord(new SequenceReference(oSchema, name), lastNumber, incrementBy,
                            oMinValue, oMaxValue, cycle, oCacheSize, Optional.empty()));
                }
            }
        }
        return List.copyOf(sequences);
    }


    @Override
    public List<Partition> getAllPartitions(Connection connection, String catalog, String schema) throws SQLException {
        // Oracle exposes partitioning via ALL_TAB_PARTITIONS / ALL_TAB_SUBPARTITIONS
        // plus
        // ALL_PART_TABLES (carrying the partitioning_type, sub-partitioning_type,
        // partition_count)
        // and ALL_PART_KEY_COLUMNS (the partition key columns).
        // We emit one Partition per ALL_TAB_PARTITIONS row plus one per
        // ALL_TAB_SUBPARTITIONS row.
        String partitionSql = """
                SELECT atp.TABLE_NAME, atp.PARTITION_NAME, atp.PARTITION_POSITION,
                        atp.HIGH_VALUE, atp.NUM_ROWS,
                        apt.PARTITIONING_TYPE, apt.SUBPARTITIONING_TYPE,
                        (SELECT LISTAGG(akc.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY akc.COLUMN_POSITION)
                        FROM ALL_PART_KEY_COLUMNS akc
                        WHERE akc.OWNER = atp.TABLE_OWNER AND akc.NAME = atp.TABLE_NAME) AS PART_KEY,
                        (SELECT LISTAGG(askc.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY askc.COLUMN_POSITION)
                        FROM ALL_SUBPART_KEY_COLUMNS askc
                        WHERE askc.OWNER = atp.TABLE_OWNER AND askc.NAME = atp.TABLE_NAME) AS SUBPART_KEY
                FROM ALL_TAB_PARTITIONS atp
                JOIN ALL_PART_TABLES apt
                    ON apt.OWNER = atp.TABLE_OWNER AND apt.TABLE_NAME = atp.TABLE_NAME
                WHERE atp.TABLE_OWNER = ?
                ORDER BY atp.TABLE_NAME, atp.PARTITION_POSITION
                """;
        String subPartitionSql = """
                SELECT atsp.TABLE_NAME, atsp.PARTITION_NAME, atsp.SUBPARTITION_NAME,
                        atsp.SUBPARTITION_POSITION, atsp.HIGH_VALUE, atsp.NUM_ROWS,
                        apt.SUBPARTITIONING_TYPE,
                        (SELECT LISTAGG(askc.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY askc.COLUMN_POSITION)
                        FROM ALL_SUBPART_KEY_COLUMNS askc
                        WHERE askc.OWNER = atsp.TABLE_OWNER AND askc.NAME = atsp.TABLE_NAME) AS SUBPART_KEY
                FROM ALL_TAB_SUBPARTITIONS atsp
                JOIN ALL_PART_TABLES apt
                    ON apt.OWNER = atsp.TABLE_OWNER AND apt.TABLE_NAME = atsp.TABLE_NAME
                WHERE atsp.TABLE_OWNER = ?
                ORDER BY atsp.TABLE_NAME, atsp.PARTITION_NAME, atsp.SUBPARTITION_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Partition> partitions = new ArrayList<>();
        Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));

        try (PreparedStatement ps = connection.prepareStatement(partitionSql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String name = rs.getString("PARTITION_NAME");
                    int ordinal = rs.getInt("PARTITION_POSITION");
                    Optional<Integer> oOrdinal = rs.wasNull() ? Optional.empty() : Optional.of(ordinal);
                    String highValue = rs.getString("HIGH_VALUE");
                    long rowCount = rs.getLong("NUM_ROWS");
                    Optional<Long> oRowCount = rs.wasNull() ? Optional.empty() : Optional.of(rowCount);
                    PartitionMethod method = mapOraclePartitioningType(rs.getString("PARTITIONING_TYPE"));
                    PartitionMethod subMethod = mapOraclePartitioningType(rs.getString("SUBPARTITIONING_TYPE"));
                    String partKey = rs.getString("PART_KEY");
                    String subPartKey = rs.getString("SUBPART_KEY");

                    TableReference tableRef = new TableReference(oSchema, tableName);
                    partitions.add(new PartitionRecord(name, tableRef, oOrdinal, method,
                            partKey == null ? Optional.empty() : Optional.of(partKey),
                            highValue == null ? Optional.empty() : Optional.of(highValue), oRowCount, Optional.empty(),
                            subMethod == PartitionMethod.OTHER ? Optional.empty() : Optional.of(subMethod),
                            subPartKey == null ? Optional.empty() : Optional.of(subPartKey)));
                }
            }
        } catch (SQLException e) {
            return List.of();
        }

        try (PreparedStatement ps = connection.prepareStatement(subPartitionSql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String parentName = rs.getString("PARTITION_NAME");
                    String subName = rs.getString("SUBPARTITION_NAME");
                    int ordinal = rs.getInt("SUBPARTITION_POSITION");
                    Optional<Integer> oOrdinal = rs.wasNull() ? Optional.empty() : Optional.of(ordinal);
                    String highValue = rs.getString("HIGH_VALUE");
                    long rowCount = rs.getLong("NUM_ROWS");
                    Optional<Long> oRowCount = rs.wasNull() ? Optional.empty() : Optional.of(rowCount);
                    PartitionMethod method = mapOraclePartitioningType(rs.getString("SUBPARTITIONING_TYPE"));
                    String subPartKey = rs.getString("SUBPART_KEY");

                    TableReference tableRef = new TableReference(oSchema, tableName);
                    partitions.add(new PartitionRecord(subName, tableRef, oOrdinal, method,
                            subPartKey == null ? Optional.empty() : Optional.of(subPartKey),
                            highValue == null ? Optional.empty() : Optional.of(highValue), oRowCount,
                            Optional.of(parentName), Optional.empty(), Optional.empty()));
                }
            }
        } catch (SQLException e) {
            // Ignore — sub-partitions are optional; the partition list above is still
            // useful.
        }

        return List.copyOf(partitions);
    }


    private static PartitionMethod mapOraclePartitioningType(String type) {
        if (type == null) {
            return PartitionMethod.OTHER;
        }
        return switch (type.toUpperCase(java.util.Locale.ROOT)) {
        case "RANGE" -> PartitionMethod.RANGE;
        case "LIST" -> PartitionMethod.LIST;
        case "HASH" -> PartitionMethod.HASH;
        default -> PartitionMethod.OTHER;
        };
    }


    @Override
    public List<CheckConstraint> getAllCheckConstraints(Connection connection, String catalog, String schema)
            throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        return readCheckConstraints(connection, schemaName, null);
    }


    @Override
    public List<CheckConstraint> getCheckConstraints(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        return readCheckConstraints(connection, schemaName, tableName);
    }


    @Override
    public List<UniqueConstraint> getAllUniqueConstraints(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT c.CONSTRAINT_NAME, c.TABLE_NAME, cc.COLUMN_NAME, cc.POSITION
                FROM ALL_CONSTRAINTS c
                JOIN ALL_CONS_COLUMNS cc ON c.OWNER = cc.OWNER AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                WHERE c.OWNER = ? AND c.CONSTRAINT_TYPE = 'U'
                ORDER BY c.TABLE_NAME, c.CONSTRAINT_NAME, cc.POSITION
                """;
        return readUniqueConstraints(connection, sql, schema, null);
    }


    @Override
    public List<UniqueConstraint> getUniqueConstraints(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        String sql = """
                SELECT c.CONSTRAINT_NAME, c.TABLE_NAME, cc.COLUMN_NAME, cc.POSITION
                FROM ALL_CONSTRAINTS c
                JOIN ALL_CONS_COLUMNS cc ON c.OWNER = cc.OWNER AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                WHERE c.OWNER = ? AND c.CONSTRAINT_TYPE = 'U' AND c.TABLE_NAME = ?
                ORDER BY c.CONSTRAINT_NAME, cc.POSITION
                """;
        return readUniqueConstraints(connection, sql, schema, tableName);
    }


    @Override
    public Optional<List<PrimaryKey>> getAllPrimaryKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT c.CONSTRAINT_NAME, c.TABLE_NAME, cc.COLUMN_NAME, cc.POSITION
                FROM ALL_CONSTRAINTS c
                JOIN ALL_CONS_COLUMNS cc ON c.OWNER = cc.OWNER AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                WHERE c.OWNER = ? AND c.CONSTRAINT_TYPE = 'P'
                ORDER BY c.TABLE_NAME, cc.POSITION
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
                SELECT fk.CONSTRAINT_NAME AS FK_NAME, fk.TABLE_NAME AS FK_TABLE,
                        fk_col.COLUMN_NAME AS FK_COLUMN, pk.TABLE_NAME AS PK_TABLE,
                        pk_col.COLUMN_NAME AS PK_COLUMN, fk_col.POSITION AS KEY_SEQ,
                        fk.DELETE_RULE, pk.CONSTRAINT_NAME AS PK_NAME
                FROM ALL_CONSTRAINTS fk
                JOIN ALL_CONS_COLUMNS fk_col ON fk.OWNER = fk_col.OWNER AND fk.CONSTRAINT_NAME = fk_col.CONSTRAINT_NAME
                JOIN ALL_CONSTRAINTS pk ON fk.R_OWNER = pk.OWNER AND fk.R_CONSTRAINT_NAME = pk.CONSTRAINT_NAME
                JOIN ALL_CONS_COLUMNS pk_col ON pk.OWNER = pk_col.OWNER AND pk.CONSTRAINT_NAME = pk_col.CONSTRAINT_NAME
                    AND fk_col.POSITION = pk_col.POSITION
                WHERE fk.OWNER = ? AND fk.CONSTRAINT_TYPE = 'R'
                ORDER BY fk.TABLE_NAME, fk.CONSTRAINT_NAME, fk_col.POSITION
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
        // Symmetric to getAllImportedKeys: FK entries whose referenced (R_OWNER) side
        // is the given schema.
        String sql = """
                SELECT fk.CONSTRAINT_NAME AS FK_NAME, fk.TABLE_NAME AS FK_TABLE,
                        fk_col.COLUMN_NAME AS FK_COLUMN, pk.TABLE_NAME AS PK_TABLE,
                        pk_col.COLUMN_NAME AS PK_COLUMN, fk_col.POSITION AS KEY_SEQ,
                        fk.DELETE_RULE, pk.CONSTRAINT_NAME AS PK_NAME
                FROM ALL_CONSTRAINTS fk
                JOIN ALL_CONS_COLUMNS fk_col ON fk.OWNER = fk_col.OWNER AND fk.CONSTRAINT_NAME = fk_col.CONSTRAINT_NAME
                JOIN ALL_CONSTRAINTS pk ON fk.R_OWNER = pk.OWNER AND fk.R_CONSTRAINT_NAME = pk.CONSTRAINT_NAME
                JOIN ALL_CONS_COLUMNS pk_col ON pk.OWNER = pk_col.OWNER AND pk.CONSTRAINT_NAME = pk_col.CONSTRAINT_NAME
                    AND fk_col.POSITION = pk_col.POSITION
                WHERE fk.R_OWNER = ? AND fk.CONSTRAINT_TYPE = 'R'
                ORDER BY pk.TABLE_NAME, fk.CONSTRAINT_NAME, fk_col.POSITION
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
                SELECT i.INDEX_NAME, i.INDEX_TYPE, i.TABLE_NAME, i.UNIQUENESS,
                        ic.COLUMN_NAME, ic.COLUMN_POSITION, ic.DESCEND
                FROM ALL_INDEXES i
                JOIN ALL_IND_COLUMNS ic ON i.OWNER = ic.INDEX_OWNER AND i.INDEX_NAME = ic.INDEX_NAME
                WHERE i.TABLE_OWNER = ? ORDER BY i.TABLE_NAME, i.INDEX_NAME, ic.COLUMN_POSITION
                """;
        String schemaName = resolveSchema(schema, connection);
        Map<String, List<IndexInfoItem>> tableIndexes = new LinkedHashMap<>();
        Map<String, TableReference> tableRefs = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String indexName = rs.getString("INDEX_NAME");
                    String indexType = rs.getString("INDEX_TYPE");
                    String uniqueness = rs.getString("UNIQUENESS");
                    String columnName = rs.getString("COLUMN_NAME");
                    int columnPosition = rs.getInt("COLUMN_POSITION");
                    String descend = rs.getString("DESCEND");

                    IndexInfoItem.IndexType mappedType = mapOracleIndexType(indexType);
                    boolean isUnique = "UNIQUE".equalsIgnoreCase(uniqueness);
                    Optional<Boolean> ascending = descend != null ? Optional.of("ASC".equalsIgnoreCase(descend))
                            : Optional.empty();

                    TableReference tableRef = tableRefs.computeIfAbsent(tableName, k -> {
                        Optional<SchemaReference> oSchema = Optional
                                .of(new SchemaReference(Optional.empty(), schemaName));
                        return new TableReference(oSchema, k);
                    });

                    Optional<ColumnReference> colRef = Optional.ofNullable(columnName)
                            .map(cn -> new ColumnReference(Optional.of(tableRef), cn));

                    IndexInfoItem item = new IndexInfoItemRecord(Optional.ofNullable(indexName), mappedType, colRef,
                            columnPosition, ascending, 0L, 0L, Optional.empty(), isUnique);

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
        String schemaName = resolveSchema(schema, connection);
        List<ViewDefinition> views = new ArrayList<>();

        // Try TEXT_VC first (available in Oracle 12.2+)
        try {
            String sql = """
                    SELECT VIEW_NAME, TEXT_VC FROM ALL_VIEWS WHERE OWNER = ? ORDER BY VIEW_NAME
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, schemaName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String viewName = rs.getString("VIEW_NAME");
                        String viewBody = rs.getString("TEXT_VC");

                        Optional<SchemaReference> oSchema = Optional
                                .of(new SchemaReference(Optional.empty(), schemaName));
                        TableReference viewRef = new TableReference(oSchema, viewName, "VIEW");

                        views.add(new ViewDefinitionRecord(viewRef, Optional.ofNullable(viewBody), Optional.empty()));
                    }
                }
            }
            return List.copyOf(views);
        } catch (SQLException e) {
            LOGGER.debug("TEXT_VC column not available, falling back to TEXT column", e);
        }

        // Fall back to TEXT column (LONG type, may have issues)
        try {
            String sqlFallback = """
                    SELECT VIEW_NAME, TEXT FROM ALL_VIEWS WHERE OWNER = ? ORDER BY VIEW_NAME
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sqlFallback)) {
                ps.setString(1, schemaName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String viewName = rs.getString("VIEW_NAME");
                        String viewBody;
                        try {
                            viewBody = rs.getString("TEXT");
                        } catch (SQLException ex) {
                            LOGGER.debug("Could not read TEXT column for view {}", viewName, ex);
                            viewBody = null;
                        }

                        Optional<SchemaReference> oSchema = Optional
                                .of(new SchemaReference(Optional.empty(), schemaName));
                        TableReference viewRef = new TableReference(oSchema, viewName, "VIEW");

                        views.add(new ViewDefinitionRecord(viewRef, Optional.ofNullable(viewBody), Optional.empty()));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.debug("Could not read view definitions from ALL_VIEWS", e);
        }
        return List.copyOf(views);
    }


    @Override
    public List<MaterializedView> getAllMaterializedViews(Connection connection, String catalog, String schema)
            throws SQLException {
        // ALL_MVIEWS drives the list. Oracle also exposes REFRESH_MODE
        // ('DEMAND','COMMIT','NEVER')
        // and REFRESH_METHOD ('COMPLETE','FAST','FORCE','NEVER'); we report
        // REFRESH_METHOD since
        // that is what users normally think of when saying "refresh mode".
        String sql = """
                SELECT MVIEW_NAME, QUERY, REFRESH_METHOD, LAST_REFRESH_DATE
                FROM ALL_MVIEWS
                WHERE OWNER = ?
                ORDER BY MVIEW_NAME
                """;
        String schemaName = resolveSchema(schema, connection);
        List<MaterializedView> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("MVIEW_NAME");
                    String body = rs.getString("QUERY");
                    String refreshMethod = rs.getString("REFRESH_METHOD");
                    Timestamp lastRefresh = rs.getTimestamp("LAST_REFRESH_DATE");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference viewRef = new TableReference(oSchema, name, "MATERIALIZED VIEW");

                    // DBMS_METADATA.GET_DDL is accurate but slow; skip unless explicitly needed.
                    Optional<String> fullDef = Optional.empty();
                    Optional<Instant> lastRef = lastRefresh == null ? Optional.empty()
                            : Optional.of(lastRefresh.toInstant());

                    result.add(new MaterializedViewRecord(viewRef, Optional.ofNullable(body), fullDef,
                            Optional.ofNullable(refreshMethod), lastRef));
                }
            }
        } catch (SQLException e) {
            LOGGER.debug("Could not read materialized views from ALL_MVIEWS: {}", e.getMessage());
            return List.of();
        }
        return List.copyOf(result);
    }


    @Override
    public List<Procedure> getAllProcedures(Connection connection, String catalog, String schema) throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        Map<String, List<ProcedureColumn>> paramMap = loadOracleProcedureColumns(connection, schemaName);
        Map<String, String> sourceMap = loadOracleSources(connection, schemaName, "PROCEDURE");
        Map<String, Instant> lastDdlMap = loadOracleLastDdlTimes(connection, schemaName, "PROCEDURE");

        String sql = """
                SELECT OBJECT_NAME, PROCEDURE_NAME
                FROM ALL_PROCEDURES
                WHERE OWNER = ? AND OBJECT_TYPE = 'PROCEDURE'
                ORDER BY OBJECT_NAME
                """;
        List<Procedure> procedures = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String objectName = rs.getString("OBJECT_NAME");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    List<ProcedureColumn> cols = paramMap.getOrDefault(objectName, List.of());
                    Optional<String> body = Optional.ofNullable(sourceMap.get(objectName));
                    Optional<String> fullDef = getMetadataDdl(connection, "PROCEDURE", schemaName, objectName);
                    Optional<Instant> lastMod = Optional.ofNullable(lastDdlMap.get(objectName));

                    procedures.add(new ProcedureRecord(new ProcedureReference(oSchema, objectName),
                            Procedure.ProcedureType.NO_RESULT, Optional.empty(), cols, body, fullDef, lastMod));
                }
            }
        }
        return List.copyOf(procedures);
    }


    @Override
    public List<Function> getAllFunctions(Connection connection, String catalog, String schema) throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        Map<String, List<FunctionColumn>> paramMap = loadOracleFunctionColumns(connection, schemaName);
        Map<String, String> sourceMap = loadOracleSources(connection, schemaName, "FUNCTION");
        Map<String, Instant> lastDdlMap = loadOracleLastDdlTimes(connection, schemaName, "FUNCTION");

        String sql = """
                SELECT OBJECT_NAME
                FROM ALL_PROCEDURES
                WHERE OWNER = ? AND OBJECT_TYPE = 'FUNCTION'
                ORDER BY OBJECT_NAME
                """;
        List<Function> functions = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String objectName = rs.getString("OBJECT_NAME");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    List<FunctionColumn> cols = paramMap.getOrDefault(objectName, List.of());
                    Optional<String> body = Optional.ofNullable(sourceMap.get(objectName));
                    Optional<String> fullDef = getMetadataDdl(connection, "FUNCTION", schemaName, objectName);
                    Optional<Instant> lastMod = Optional.ofNullable(lastDdlMap.get(objectName));

                    functions.add(new FunctionRecord(new FunctionReference(oSchema, objectName),
                            Function.FunctionType.NO_TABLE, Optional.empty(), cols, body, fullDef, lastMod));
                }
            }
        }
        return List.copyOf(functions);
    }


    private Map<String, Instant> loadOracleLastDdlTimes(Connection connection, String schemaName, String objectType) {
        // ALL_OBJECTS.LAST_DDL_TIME is the canonical source for "when was this PL/SQL
        // object
        // last changed"; a single bulk query covers the whole schema.
        String sql = """
                SELECT OBJECT_NAME, LAST_DDL_TIME
                FROM ALL_OBJECTS
                WHERE OWNER = ? AND OBJECT_TYPE = ?
                """;
        Map<String, Instant> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, objectType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("OBJECT_NAME");
                    Timestamp ts = rs.getTimestamp("LAST_DDL_TIME");
                    if (ts != null) {
                        result.put(name, ts.toInstant());
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.debug("Could not read LAST_DDL_TIME for {} from ALL_OBJECTS: {}", objectType, e.getMessage());
            return Map.of();
        }
        return result;
    }


    private Map<String, String> loadOracleSources(Connection connection, String schemaName, String type) {
        // Aggregate source lines per object from ALL_SOURCE.
        String sql = """
                SELECT NAME, TEXT
                FROM ALL_SOURCE
                WHERE OWNER = ? AND TYPE = ?
                ORDER BY NAME, LINE
                """;
        Map<String, StringBuilder> byName = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("NAME");
                    String text = rs.getString("TEXT");
                    byName.computeIfAbsent(name, k -> new StringBuilder()).append(text == null ? "" : text);
                }
            }
        } catch (SQLException e) {
            LOGGER.debug("Could not read ALL_SOURCE for type {}: {}", type, e.getMessage());
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        byName.forEach((k, v) -> result.put(k, v.toString()));
        return result;
    }


    private Optional<String> getMetadataDdl(Connection connection, String objectType, String schemaName,
            String objectName) {
        // DBMS_METADATA.GET_DDL returns a CLOB with the full CREATE statement.
        String sql = "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) FROM DUAL";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, objectType);
            ps.setString(2, objectName);
            ps.setString(3, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String ddl = rs.getString(1);
                    return Optional.ofNullable(ddl);
                }
            }
        } catch (SQLException e) {
            LOGGER.debug("Could not retrieve DDL for {} {}.{}: {}", objectType, schemaName, objectName, e.getMessage());
        }
        return Optional.empty();
    }


    private Map<String, List<ProcedureColumn>> loadOracleProcedureColumns(Connection connection, String schemaName)
            throws SQLException {
        String sql = """
                SELECT a.OBJECT_NAME, a.ARGUMENT_NAME, a.IN_OUT, a.DATA_TYPE,
                        a.POSITION, a.DATA_PRECISION, a.DATA_SCALE
                FROM ALL_ARGUMENTS a
                JOIN ALL_PROCEDURES p ON a.OBJECT_ID = p.OBJECT_ID AND a.SUBPROGRAM_ID = p.SUBPROGRAM_ID
                WHERE a.OWNER = ? AND p.OBJECT_TYPE = 'PROCEDURE' AND a.POSITION > 0
                ORDER BY a.OBJECT_NAME, a.POSITION
                """;
        Map<String, List<ProcedureColumn>> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String objectName = rs.getString("OBJECT_NAME");
                    String argName = rs.getString("ARGUMENT_NAME");
                    String inOut = rs.getString("IN_OUT");
                    String dataType = rs.getString("DATA_TYPE");
                    int position = rs.getInt("POSITION");
                    int precision = rs.getInt("DATA_PRECISION");
                    boolean precisionNull = rs.wasNull();
                    int scale = rs.getInt("DATA_SCALE");
                    boolean scaleNull = rs.wasNull();

                    ProcedureColumn.ColumnType colType = mapOracleProcColumnType(inOut);
                    JDBCType jdbcType = mapOracleJdbcType(dataType);

                    ProcedureColumn col = new ProcedureColumnRecord(argName != null ? argName : "", colType, jdbcType,
                            dataType != null ? dataType : "",
                            precisionNull ? OptionalInt.empty() : OptionalInt.of(precision),
                            scaleNull ? OptionalInt.empty() : OptionalInt.of(scale), OptionalInt.of(10),
                            ProcedureColumn.Nullability.UNKNOWN, Optional.empty(), Optional.empty(), position);

                    result.computeIfAbsent(objectName, k -> new ArrayList<>()).add(col);
                }
            }
        }
        return result;
    }


    private Map<String, List<FunctionColumn>> loadOracleFunctionColumns(Connection connection, String schemaName)
            throws SQLException {
        String sql = """
                SELECT a.OBJECT_NAME, a.ARGUMENT_NAME, a.IN_OUT, a.DATA_TYPE,
                        a.POSITION, a.DATA_PRECISION, a.DATA_SCALE
                FROM ALL_ARGUMENTS a
                JOIN ALL_PROCEDURES p ON a.OBJECT_ID = p.OBJECT_ID AND a.SUBPROGRAM_ID = p.SUBPROGRAM_ID
                WHERE a.OWNER = ? AND p.OBJECT_TYPE = 'FUNCTION'
                ORDER BY a.OBJECT_NAME, a.POSITION
                """;
        Map<String, List<FunctionColumn>> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String objectName = rs.getString("OBJECT_NAME");
                    String argName = rs.getString("ARGUMENT_NAME");
                    String inOut = rs.getString("IN_OUT");
                    String dataType = rs.getString("DATA_TYPE");
                    int position = rs.getInt("POSITION");
                    int precision = rs.getInt("DATA_PRECISION");
                    boolean precisionNull = rs.wasNull();
                    int scale = rs.getInt("DATA_SCALE");
                    boolean scaleNull = rs.wasNull();

                    FunctionColumn.ColumnType colType = mapOracleFuncColumnType(inOut);
                    JDBCType jdbcType = mapOracleJdbcType(dataType);

                    FunctionColumn col = new FunctionColumnRecord(argName != null ? argName : "", colType, jdbcType,
                            dataType != null ? dataType : "",
                            precisionNull ? OptionalInt.empty() : OptionalInt.of(precision),
                            scaleNull ? OptionalInt.empty() : OptionalInt.of(scale), OptionalInt.of(10),
                            FunctionColumn.Nullability.UNKNOWN, Optional.empty(), OptionalInt.empty(), position);

                    result.computeIfAbsent(objectName, k -> new ArrayList<>()).add(col);
                }
            }
        }
        return result;
    }


    private static ProcedureColumn.ColumnType mapOracleProcColumnType(String inOut) {
        if (inOut == null) {
            return ProcedureColumn.ColumnType.UNKNOWN;
        }
        return switch (inOut.toUpperCase()) {
        case "IN" -> ProcedureColumn.ColumnType.IN;
        case "OUT" -> ProcedureColumn.ColumnType.OUT;
        case "IN/OUT" -> ProcedureColumn.ColumnType.INOUT;
        default -> ProcedureColumn.ColumnType.UNKNOWN;
        };
    }


    private static FunctionColumn.ColumnType mapOracleFuncColumnType(String inOut) {
        if (inOut == null) {
            return FunctionColumn.ColumnType.UNKNOWN;
        }
        return switch (inOut.toUpperCase()) {
        case "IN" -> FunctionColumn.ColumnType.IN;
        case "OUT" -> FunctionColumn.ColumnType.OUT;
        case "IN/OUT" -> FunctionColumn.ColumnType.INOUT;
        default -> FunctionColumn.ColumnType.UNKNOWN;
        };
    }


    private static JDBCType mapOracleJdbcType(String dataType) {
        if (dataType == null) {
            return JDBCType.OTHER;
        }
        return switch (dataType.toUpperCase()) {
        case "NUMBER" -> JDBCType.NUMERIC;
        case "VARCHAR2", "NVARCHAR2" -> JDBCType.VARCHAR;
        case "CHAR", "NCHAR" -> JDBCType.CHAR;
        case "DATE" -> JDBCType.DATE;
        case "TIMESTAMP", "TIMESTAMP(6)" -> JDBCType.TIMESTAMP;
        case "CLOB", "NCLOB" -> JDBCType.CLOB;
        case "BLOB" -> JDBCType.BLOB;
        case "RAW" -> JDBCType.VARBINARY;
        case "LONG RAW" -> JDBCType.LONGVARBINARY;
        case "LONG" -> JDBCType.LONGVARCHAR;
        case "FLOAT", "BINARY_FLOAT" -> JDBCType.FLOAT;
        case "BINARY_DOUBLE" -> JDBCType.DOUBLE;
        case "INTEGER", "PLS_INTEGER" -> JDBCType.INTEGER;
        case "BOOLEAN" -> JDBCType.BOOLEAN;
        case "XMLTYPE" -> JDBCType.SQLXML;
        default -> JDBCType.OTHER;
        };
    }


    private Trigger readTrigger(ResultSet rs, String schemaName) throws SQLException {
        String triggerName = rs.getString("TRIGGER_NAME");
        String tableName = rs.getString("TABLE_NAME");
        String triggerType = rs.getString("TRIGGER_TYPE");
        String triggeringEvent = rs.getString("TRIGGERING_EVENT");

        // TRIGGER_BODY is LONG type, wrap in try-catch
        String triggerBody;
        try {
            triggerBody = rs.getString("TRIGGER_BODY");
        } catch (SQLException e) {
            LOGGER.debug("Could not read TRIGGER_BODY for trigger {}", triggerName, e);
            triggerBody = null;
        }

        Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
        TableReference tableRef = new TableReference(oSchema, tableName);

        TriggerTiming timing = mapOracleTriggerTiming(triggerType);
        TriggerEvent event = mapOracleTriggerEvent(triggeringEvent);
        String orientation = parseOracleOrientation(triggerType);

        return new TriggerRecord(new TriggerReference(tableRef, triggerName), timing, event,
                Optional.ofNullable(triggerBody), Optional.empty(), Optional.ofNullable(orientation));
    }


    private ImportedKey readImportedKey(ResultSet rs, String schemaName) throws SQLException {
        String fkName = rs.getString("FK_NAME");
        String fkTable = rs.getString("FK_TABLE");
        String fkColumn = rs.getString("FK_COLUMN");
        String pkTable = rs.getString("PK_TABLE");
        String pkColumn = rs.getString("PK_COLUMN");
        int keySeq = rs.getInt("KEY_SEQ");
        String deleteRule = rs.getString("DELETE_RULE");
        String pkName = rs.getString("PK_NAME");

        // FK side
        Optional<SchemaReference> fkSchemaRef = Optional.of(new SchemaReference(Optional.empty(), schemaName));
        TableReference fkTableRef = new TableReference(fkSchemaRef, fkTable);
        ColumnReference fkColRef = new ColumnReference(Optional.of(fkTableRef), fkColumn);

        // PK side
        Optional<SchemaReference> pkSchemaRef = Optional.of(new SchemaReference(Optional.empty(), schemaName));
        TableReference pkTableRef = new TableReference(pkSchemaRef, pkTable);
        ColumnReference pkColRef = new ColumnReference(Optional.of(pkTableRef), pkColumn);

        return new ImportedKeyRecord(pkColRef, fkColRef, fkName, keySeq, ImportedKey.ReferentialAction.NO_ACTION,   // Oracle
                                                                                                                    // doesn't
                                                                                                                    // support
                                                                                                                    // UPDATE
                                                                                                                    // CASCADE
                mapOracleDeleteRule(deleteRule), Optional.ofNullable(pkName), ImportedKey.Deferrability.NOT_DEFERRABLE);
    }


    private List<CheckConstraint> readCheckConstraints(Connection connection, String schemaName, String tableName)
            throws SQLException {
        // Try with SEARCH_CONDITION_VC first (available in Oracle 12.1+)
        try {
            return readCheckConstraintsWithVc(connection, schemaName, tableName);
        } catch (SQLException e) {
            LOGGER.debug("SEARCH_CONDITION_VC not available, falling back to SEARCH_CONDITION", e);
        }

        // Fall back to SEARCH_CONDITION (LONG type)
        return readCheckConstraintsWithLong(connection, schemaName, tableName);
    }


    private List<CheckConstraint> readCheckConstraintsWithVc(Connection connection, String schemaName, String tableName)
            throws SQLException {
        String sql;
        if (tableName != null) {
            sql = """
                    SELECT c.CONSTRAINT_NAME, c.TABLE_NAME, c.SEARCH_CONDITION_VC
                    FROM ALL_CONSTRAINTS c WHERE c.OWNER = ? AND c.CONSTRAINT_TYPE = 'C'
                    AND c.GENERATED != 'GENERATED NAME' AND c.TABLE_NAME = ?
                    ORDER BY c.CONSTRAINT_NAME
                    """;
        } else {
            sql = """
                    SELECT c.CONSTRAINT_NAME, c.TABLE_NAME, c.SEARCH_CONDITION_VC
                    FROM ALL_CONSTRAINTS c WHERE c.OWNER = ? AND c.CONSTRAINT_TYPE = 'C'
                    AND c.GENERATED != 'GENERATED NAME'
                    ORDER BY c.TABLE_NAME, c.CONSTRAINT_NAME
                    """;
        }
        List<CheckConstraint> constraints = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            if (tableName != null) {
                ps.setString(2, tableName);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("CONSTRAINT_NAME");
                    String table = rs.getString("TABLE_NAME");
                    String checkClause = rs.getString("SEARCH_CONDITION_VC");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference tableRef = new TableReference(oSchema, table);

                    constraints.add(new CheckConstraintRecord(constraintName, tableRef,
                            checkClause != null ? checkClause : ""));
                }
            }
        }
        return List.copyOf(constraints);
    }


    private List<CheckConstraint> readCheckConstraintsWithLong(Connection connection, String schemaName,
            String tableName) throws SQLException {
        String sql;
        if (tableName != null) {
            sql = """
                    SELECT c.CONSTRAINT_NAME, c.TABLE_NAME, c.SEARCH_CONDITION
                    FROM ALL_CONSTRAINTS c WHERE c.OWNER = ? AND c.CONSTRAINT_TYPE = 'C'
                    AND c.GENERATED != 'GENERATED NAME' AND c.TABLE_NAME = ?
                    ORDER BY c.CONSTRAINT_NAME
                    """;
        } else {
            sql = """
                    SELECT c.CONSTRAINT_NAME, c.TABLE_NAME, c.SEARCH_CONDITION
                    FROM ALL_CONSTRAINTS c WHERE c.OWNER = ? AND c.CONSTRAINT_TYPE = 'C'
                    AND c.GENERATED != 'GENERATED NAME'
                    ORDER BY c.TABLE_NAME, c.CONSTRAINT_NAME
                    """;
        }
        List<CheckConstraint> constraints = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            if (tableName != null) {
                ps.setString(2, tableName);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("CONSTRAINT_NAME");
                    String table = rs.getString("TABLE_NAME");
                    String checkClause;
                    try {
                        checkClause = rs.getString("SEARCH_CONDITION");
                    } catch (SQLException e) {
                        LOGGER.debug("Could not read SEARCH_CONDITION for constraint {}", constraintName, e);
                        checkClause = null;
                    }

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference tableRef = new TableReference(oSchema, table);

                    constraints.add(new CheckConstraintRecord(constraintName, tableRef,
                            checkClause != null ? checkClause : ""));
                }
            }
        }
        return List.copyOf(constraints);
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
        // Oracle uses the user name as the default schema
        String connSchema = connection.getSchema();
        if (connSchema != null) {
            return connSchema;
        }
        return connection.getMetaData().getUserName();
    }


    private static TriggerTiming mapOracleTriggerTiming(String triggerType) {
        if (triggerType == null) {
            return TriggerTiming.AFTER;
        }
        String upper = triggerType.toUpperCase();
        if (upper.startsWith("BEFORE")) {
            return TriggerTiming.BEFORE;
        } else if (upper.startsWith("INSTEAD OF")) {
            return TriggerTiming.INSTEAD_OF;
        } else {
            return TriggerTiming.AFTER;
        }
    }


    private static TriggerEvent mapOracleTriggerEvent(String triggeringEvent) {
        if (triggeringEvent == null) {
            return TriggerEvent.INSERT;
        }
        // Take the first word: 'INSERT OR UPDATE' -> 'INSERT'
        String firstWord = triggeringEvent.trim().split("\\s+")[0].toUpperCase();
        return switch (firstWord) {
        case "UPDATE" -> TriggerEvent.UPDATE;
        case "DELETE" -> TriggerEvent.DELETE;
        default -> TriggerEvent.INSERT;
        };
    }


    private static String parseOracleOrientation(String triggerType) {
        if (triggerType == null) {
            return null;
        }
        String upper = triggerType.toUpperCase();
        if (upper.contains("EACH ROW")) {
            return "ROW";
        } else if (upper.contains("STATEMENT")) {
            return "STATEMENT";
        }
        return null;
    }


    private static ImportedKey.ReferentialAction mapOracleDeleteRule(String deleteRule) {
        if (deleteRule == null) {
            return ImportedKey.ReferentialAction.NO_ACTION;
        }
        return switch (deleteRule.toUpperCase()) {
        case "CASCADE" -> ImportedKey.ReferentialAction.CASCADE;
        case "SET NULL" -> ImportedKey.ReferentialAction.SET_NULL;
        default -> ImportedKey.ReferentialAction.NO_ACTION;
        };
    }


    private static IndexInfoItem.IndexType mapOracleIndexType(String indexType) {
        if (indexType == null) {
            return IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        }
        return switch (indexType.toUpperCase()) {
        case "CLUSTER" -> IndexInfoItem.IndexType.TABLE_INDEX_CLUSTERED;
        default -> IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        };
    }


    @Override
    public List<UserDefinedType> getAllUserDefinedTypes(Connection connection, String catalog, String schema)
            throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        String sql = """
                SELECT TYPE_NAME, TYPECODE, SUPERTYPE_NAME
                FROM ALL_TYPES
                WHERE OWNER = ?
                ORDER BY TYPE_NAME
                """;
        List<UserDefinedType> types = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String typeName = rs.getString("TYPE_NAME");
                    String typeCode = rs.getString("TYPECODE");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));

                    JDBCType baseType = mapOracleUdtBaseType(typeCode);

                    types.add(new UserDefinedTypeRecord(new UserDefinedTypeReference(oSchema, typeName), typeCode,
                            baseType, Optional.empty()));
                }
            }
        }
        return List.copyOf(types);
    }


    private static JDBCType mapOracleUdtBaseType(String typeCode) {
        if (typeCode == null) {
            return JDBCType.OTHER;
        }
        return switch (typeCode.toUpperCase()) {
        case "OBJECT" -> JDBCType.STRUCT;
        case "COLLECTION" -> JDBCType.ARRAY;
        default -> JDBCType.OTHER;
        };
    }


    @Override
    public Optional<List<TablePrivilege>> getAllTablePrivileges(Connection connection, String catalog,
            String schemaPattern, String tableNamePattern) throws SQLException {
        // ALL_TAB_PRIVS sees grants regardless of the current user — PUBLIC, role
        // grants,
        // and grants made by others are all returned. JDBC's getTablePrivileges only
        // returns direct grants to the connected user.
        StringBuilder sql = new StringBuilder("""
                SELECT OWNER, TABLE_NAME, GRANTOR, GRANTEE, PRIVILEGE, GRANTABLE
                FROM ALL_TAB_PRIVS
                WHERE OWNER = ?
                """);
        boolean hasTableFilter = tableNamePattern != null && !tableNamePattern.isBlank()
                && !"%".equals(tableNamePattern);
        if (hasTableFilter) {
            sql.append("  AND TABLE_NAME LIKE ?\n");
        }
        sql.append("ORDER BY TABLE_NAME, PRIVILEGE, GRANTEE");

        String schemaName = resolveSchema(schemaPattern, connection);
        List<TablePrivilege> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setString(1, schemaName);
            if (hasTableFilter) {
                ps.setString(2, tableNamePattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String grantor = rs.getString("GRANTOR");
                    String grantee = rs.getString("GRANTEE");
                    String privilege = rs.getString("PRIVILEGE");
                    String grantable = rs.getString("GRANTABLE");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference tableRef = new TableReference(oSchema, tableName);

                    result.add(new TablePrivilegeRecord(tableRef, Optional.ofNullable(grantor), grantee, privilege,
                            Optional.ofNullable(grantable)));
                }
            }
        } catch (SQLException e) {
            LOGGER.debug("Could not read table privileges from ALL_TAB_PRIVS: {}", e.getMessage());
            return Optional.empty();
        }
        return Optional.of(List.copyOf(result));
    }


    @Override
    public Optional<List<ColumnPrivilege>> getColumnPrivileges(Connection connection, String catalog, String schema,
            String tableName, String columnNamePattern) throws SQLException {
        // ALL_COL_PRIVS is the column-level equivalent of ALL_TAB_PRIVS.
        StringBuilder sql = new StringBuilder("""
                SELECT OWNER, TABLE_NAME, COLUMN_NAME, GRANTOR, GRANTEE, PRIVILEGE, GRANTABLE
                FROM ALL_COL_PRIVS
                WHERE OWNER = ? AND TABLE_NAME = ?
                """);
        boolean hasColumnFilter = columnNamePattern != null && !columnNamePattern.isBlank()
                && !"%".equals(columnNamePattern);
        if (hasColumnFilter) {
            sql.append("  AND COLUMN_NAME LIKE ?\n");
        }
        sql.append("ORDER BY COLUMN_NAME, PRIVILEGE, GRANTEE");

        String schemaName = resolveSchema(schema, connection);
        List<ColumnPrivilege> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            if (hasColumnFilter) {
                ps.setString(3, columnNamePattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                TableReference tableRef = new TableReference(oSchema, tableName);

                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String grantor = rs.getString("GRANTOR");
                    String grantee = rs.getString("GRANTEE");
                    String privilege = rs.getString("PRIVILEGE");
                    String grantable = rs.getString("GRANTABLE");

                    ColumnReference colRef = new ColumnReference(Optional.of(tableRef), columnName);
                    result.add(new ColumnPrivilegeRecord(colRef, Optional.ofNullable(grantor), grantee, privilege,
                            Optional.ofNullable(grantable)));
                }
            }
        } catch (SQLException e) {
            LOGGER.debug("Could not read column privileges from ALL_COL_PRIVS: {}", e.getMessage());
            return Optional.empty();
        }
        return Optional.of(List.copyOf(result));
    }


    @Override
    public Optional<List<PseudoColumn>> getAllPseudoColumns(Connection connection, String catalog, String schemaPattern,
            String tableNamePattern, String columnNamePattern) throws SQLException {
        // Oracle's JDBC driver returns an empty pseudo-column set for most objects.
        // ALL_TAB_COLS with HIDDEN_COLUMN='YES' surfaces Oracle-specific pseudo columns
        // (ROWID of index-organized tables, virtual columns, system-generated identity
        // tracking columns, etc.) that never show up via getPseudoColumns.
        StringBuilder sql = new StringBuilder("""
                SELECT c.OWNER, c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE, c.DATA_LENGTH,
                        c.DATA_PRECISION, c.DATA_SCALE, c.NULLABLE
                FROM ALL_TAB_COLS c
                WHERE c.HIDDEN_COLUMN = 'YES' AND c.OWNER = ?
                """);
        boolean hasTableFilter = tableNamePattern != null && !tableNamePattern.isBlank()
                && !"%".equals(tableNamePattern);
        if (hasTableFilter) {
            sql.append("  AND c.TABLE_NAME LIKE ?\n");
        }
        boolean hasColumnFilter = columnNamePattern != null && !columnNamePattern.isBlank()
                && !"%".equals(columnNamePattern);
        if (hasColumnFilter) {
            sql.append("  AND c.COLUMN_NAME LIKE ?\n");
        }
        sql.append("ORDER BY c.TABLE_NAME, c.COLUMN_NAME");

        String schemaName = resolveSchema(schemaPattern, connection);
        List<PseudoColumn> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, schemaName);
            if (hasTableFilter) {
                ps.setString(idx++, tableNamePattern);
            }
            if (hasColumnFilter) {
                ps.setString(idx++, columnNamePattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("DATA_TYPE");
                    int length = rs.getInt("DATA_LENGTH");
                    boolean lengthNull = rs.wasNull();
                    int precision = rs.getInt("DATA_PRECISION");
                    boolean precisionNull = rs.wasNull();
                    int scale = rs.getInt("DATA_SCALE");
                    boolean scaleNull = rs.wasNull();
                    String nullable = rs.getString("NULLABLE");

                    TableReference tableRef = new TableReference(oSchema, tableName);
                    ColumnReference colRef = new ColumnReference(Optional.of(tableRef), columnName);

                    JDBCType jdbc = mapOracleJdbcType(dataType);
                    OptionalInt columnSize = lengthNull ? OptionalInt.empty()
                            : precisionNull ? OptionalInt.of(length) : OptionalInt.of(precision);
                    OptionalInt decimalDigits = scaleNull ? OptionalInt.empty() : OptionalInt.of(scale);

                    result.add(new PseudoColumnRecord(colRef, jdbc, columnSize, decimalDigits, OptionalInt.of(10),
                            Optional.empty(), OptionalInt.empty(),
                            Optional.ofNullable("Y".equals(nullable) ? "YES" : "N".equals(nullable) ? "NO" : null),
                            "HIDDEN"));
                }
            }
        } catch (SQLException e) {
            LOGGER.debug("Could not read pseudo columns from ALL_TAB_COLS: {}", e.getMessage());
            return Optional.empty();
        }
        return Optional.of(List.copyOf(result));
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


    @Override
    public Optional<List<org.eclipse.daanse.sql.model.schema.ColumnDefinition>> getAllColumnDefinitions(
            Connection connection, String catalog, String schemaPattern, String tableNamePattern,
            String columnNamePattern) throws SQLException {
        String sql = """
                SELECT OWNER, TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION,
                        DATA_SCALE, NULLABLE, DATA_DEFAULT, COLUMN_ID
                FROM ALL_TAB_COLS
                WHERE OWNER = ? AND HIDDEN_COLUMN = 'NO'
                ORDER BY OWNER, TABLE_NAME, COLUMN_ID
                """;
        String schemaName = resolveSchema(schemaPattern, connection);
        List<org.eclipse.daanse.sql.model.schema.ColumnDefinition> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String owner = rs.getString("OWNER");
                    String tableName = rs.getString("TABLE_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("DATA_TYPE");
                    long dataLength = rs.getLong("DATA_LENGTH");
                    long dataPrecision = rs.getLong("DATA_PRECISION");
                    boolean precNull = rs.wasNull();
                    long dataScale = rs.getLong("DATA_SCALE");
                    boolean scaleNull = rs.wasNull();
                    String nullable = rs.getString("NULLABLE");
                    String columnDefault = rs.getString("DATA_DEFAULT"); // bufferable here

                    java.sql.JDBCType jdbcType = mapOracleType(dataType);
                    java.util.OptionalInt size;
                    java.util.OptionalInt scale = java.util.OptionalInt.empty();
                    if (isNumericJdbc(jdbcType)) {
                        size = precNull ? java.util.OptionalInt.empty() : java.util.OptionalInt.of((int) dataPrecision);
                        if (!scaleNull && dataScale != 0)
                            scale = java.util.OptionalInt.of((int) dataScale);
                    } else {
                        size = dataLength > 0 ? java.util.OptionalInt.of((int) dataLength)
                                : java.util.OptionalInt.empty();
                    }
                    org.eclipse.daanse.sql.model.schema.ColumnMetaData.Nullability n = "Y".equalsIgnoreCase(nullable)
                            ? org.eclipse.daanse.sql.model.schema.ColumnMetaData.Nullability.NULLABLE
                            : org.eclipse.daanse.sql.model.schema.ColumnMetaData.Nullability.NO_NULLS;

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), owner));
                    org.eclipse.daanse.sql.model.schema.TableReference tableRef = new TableReference(oSchema,
                            tableName);
                    org.eclipse.daanse.sql.model.schema.ColumnReference colRef = new ColumnReference(
                            Optional.of(tableRef), columnName);

                    org.eclipse.daanse.sql.model.schema.ColumnMetaData meta = new org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord(
                            jdbcType, dataType, size, scale, java.util.OptionalInt.empty(), n,
                            java.util.OptionalInt.empty(), Optional.empty(),
                            Optional.ofNullable(columnDefault).map(String::trim).filter(s -> !s.isEmpty()),
                            org.eclipse.daanse.sql.model.schema.ColumnMetaData.AutoIncrement.UNKNOWN,
                            org.eclipse.daanse.sql.model.schema.ColumnMetaData.GeneratedColumn.UNKNOWN);

                    out.add(new org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord(colRef, meta));
                }
            }
        }
        return Optional.of(List.copyOf(out));
    }


    private static java.sql.JDBCType mapOracleType(String oracleType) {
        if (oracleType == null)
            return java.sql.JDBCType.OTHER;
        return switch (oracleType.toUpperCase()) {
        case "NUMBER" -> java.sql.JDBCType.NUMERIC;
        case "INTEGER" -> java.sql.JDBCType.INTEGER;
        case "FLOAT", "BINARY_FLOAT" -> java.sql.JDBCType.REAL;
        case "BINARY_DOUBLE" -> java.sql.JDBCType.DOUBLE;
        case "VARCHAR", "VARCHAR2", "NVARCHAR2" -> java.sql.JDBCType.VARCHAR;
        case "CHAR", "NCHAR" -> java.sql.JDBCType.CHAR;
        case "CLOB", "NCLOB" -> java.sql.JDBCType.CLOB;
        case "BLOB" -> java.sql.JDBCType.BLOB;
        case "DATE" -> java.sql.JDBCType.DATE;
        case "TIMESTAMP", "TIMESTAMP WITH TIME ZONE", "TIMESTAMP WITH LOCAL TIME ZONE" -> java.sql.JDBCType.TIMESTAMP;
        case "RAW", "LONG RAW" -> java.sql.JDBCType.VARBINARY;
        default -> java.sql.JDBCType.OTHER;
        };
    }


    private static boolean isNumericJdbc(java.sql.JDBCType t) {
        if (t == null)
            return false;
        return switch (t) {
        case TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, REAL, DOUBLE, DECIMAL, NUMERIC -> true;
        default -> false;
        };
    }
}
