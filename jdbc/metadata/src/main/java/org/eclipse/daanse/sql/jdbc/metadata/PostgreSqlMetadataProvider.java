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
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.ColumnPrivilege;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionReference;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.MaterializedView;
import org.eclipse.daanse.sql.jdbc.api.schema.Partition;
import org.eclipse.daanse.sql.jdbc.api.schema.PartitionMethod;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureReference;
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
import org.eclipse.daanse.sql.jdbc.record.schema.FunctionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ImportedKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoItemRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.MaterializedViewRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PartitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ProcedureRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.SequenceRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TablePrivilegeRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TriggerRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.UniqueConstraintRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.UserDefinedTypeRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ViewDefinitionRecord;

/**
 * The PostgreSql system-catalog/{@code information_schema} reader — the
 * {@link MetadataProvider} overrides extracted 1:1 from the SQL dialect (reading only,
 * no spelling).
 */
public class PostgreSqlMetadataProvider implements MetadataProvider {


    @Override
    public List<Trigger> getAllTriggers(Connection connection, String catalog, String schema) throws SQLException {
        String sql = """
                SELECT t.tgname AS trigger_name, c.relname AS table_name, n.nspname AS schema_name,
                        pg_get_triggerdef(t.oid) AS definition,
                        p.prosrc AS proc_body
                FROM pg_trigger t
                JOIN pg_class c ON c.oid = t.tgrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_proc p ON p.oid = t.tgfoid
                WHERE NOT t.tgisinternal AND n.nspname = ?
                ORDER BY c.relname, t.tgname
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
                SELECT t.tgname AS trigger_name, c.relname AS table_name, n.nspname AS schema_name,
                        pg_get_triggerdef(t.oid) AS definition,
                        p.prosrc AS proc_body
                FROM pg_trigger t
                JOIN pg_class c ON c.oid = t.tgrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_proc p ON p.oid = t.tgfoid
                WHERE NOT t.tgisinternal AND n.nspname = ? AND c.relname = ?
                ORDER BY t.tgname
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
                SELECT sequence_name, start_value, increment, minimum_value, maximum_value,
                        cycle_option, data_type
                FROM information_schema.sequences
                WHERE sequence_schema = ?
                ORDER BY sequence_name
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Sequence> sequences = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("sequence_name");
                    long startValue = rs.getLong("start_value");
                    long increment = rs.getLong("increment");
                    long minValue = rs.getLong("minimum_value");
                    Optional<Long> oMinValue = rs.wasNull() ? Optional.empty() : Optional.of(minValue);
                    long maxValue = rs.getLong("maximum_value");
                    Optional<Long> oMaxValue = rs.wasNull() ? Optional.empty() : Optional.of(maxValue);
                    String cycleOption = rs.getString("cycle_option");
                    boolean cycle = "YES".equalsIgnoreCase(cycleOption);
                    String dataType = rs.getString("data_type");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));

                    sequences.add(new SequenceRecord(new SequenceReference(oSchema, name), startValue, increment,
                            oMinValue, oMaxValue, cycle, Optional.empty(), Optional.ofNullable(dataType)));
                }
            }
        }
        return List.copyOf(sequences);
    }


    @Override
    public List<Partition> getAllPartitions(Connection connection, String catalog, String schema) throws SQLException {
        // PostgreSQL declarative partitioning (10+): pg_inherits links each partition
        // to its
        // partitioned parent; pg_partitioned_table.partstrat carries the strategy
        // ('r' RANGE / 'l' LIST / 'h' HASH); pg_get_expr(relpartbound) returns the
        // bound expression
        // ("FOR VALUES FROM (...) TO (...)" or "FOR VALUES IN (...)" or "FOR VALUES
        // WITH (...)").
        // pg_get_partkeydef returns the partition key like "RANGE (year)".
        String sql = """
                SELECT p.relname AS parent_table,
                        c.relname AS partition_name,
                        pt.partstrat AS strategy,
                        pg_get_expr(c.relpartbound, c.oid) AS bound,
                        c.reltuples::bigint AS row_count,
                        pg_get_partkeydef(p.oid) AS keydef
                FROM pg_inherits i
                JOIN pg_class c ON c.oid = i.inhrelid
                JOIN pg_class p ON p.oid = i.inhparent
                JOIN pg_partitioned_table pt ON pt.partrelid = p.oid
                JOIN pg_namespace np ON np.oid = p.relnamespace
                WHERE np.nspname = ?
                ORDER BY p.relname, c.relname
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Partition> partitions = new ArrayList<>();
        Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String parentTable = rs.getString("parent_table");
                    String partitionName = rs.getString("partition_name");
                    String strategy = rs.getString("strategy");
                    String bound = rs.getString("bound");
                    long rowCount = rs.getLong("row_count");
                    String keydef = rs.getString("keydef");

                    PartitionMethod method = mapPartitionStrategy(strategy);
                    String expression = extractPartitionKey(keydef);

                    TableReference tableRef = new TableReference(oSchema, parentTable);
                    partitions.add(new PartitionRecord(partitionName, tableRef, Optional.empty(),   // PostgreSQL has no
                                                                                                    // native ordinal
                                                                                                    // position
                            method, expression == null ? Optional.empty() : Optional.of(expression),
                            bound == null ? Optional.empty() : Optional.of(bound),
                            rs.wasNull() ? Optional.empty() : Optional.of(rowCount), Optional.empty(), Optional.empty(),
                            Optional.empty()));
                }
            }
        } catch (SQLException e) {
            // pg_partitioned_table exists from PostgreSQL 10; older servers return no rows
            // but should not error. Catch defensively for ancient or non-PostgreSQL
            // impostors.
            return List.of();
        }
        return List.copyOf(partitions);
    }


    private static PartitionMethod mapPartitionStrategy(String partstrat) {
        if (partstrat == null || partstrat.isEmpty()) {
            return PartitionMethod.OTHER;
        }
        return switch (partstrat.charAt(0)) {
        case 'r' -> PartitionMethod.RANGE;
        case 'l' -> PartitionMethod.LIST;
        case 'h' -> PartitionMethod.HASH;
        default -> PartitionMethod.OTHER;
        };
    }


    private static String extractPartitionKey(String keydef) {
        if (keydef == null) {
            return null;
        }
        int paren = keydef.indexOf('(');
        if (paren < 0 || !keydef.endsWith(")")) {
            return keydef;
        }
        return keydef.substring(paren + 1, keydef.length() - 1).trim();
    }


    @Override
    public List<CheckConstraint> getAllCheckConstraints(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT con.conname, c.relname, pg_get_constraintdef(con.oid) AS check_clause
                FROM pg_constraint con
                JOIN pg_class c ON c.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE con.contype = 'c' AND n.nspname = ?
                ORDER BY c.relname, con.conname
                """;
        String schemaName = resolveSchema(schema, connection);
        List<CheckConstraint> constraints = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("conname");
                    String tableName = rs.getString("relname");
                    String checkClause = rs.getString("check_clause");

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
                SELECT con.conname, c.relname, pg_get_constraintdef(con.oid) AS check_clause
                FROM pg_constraint con
                JOIN pg_class c ON c.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE con.contype = 'c' AND n.nspname = ? AND c.relname = ?
                ORDER BY con.conname
                """;
        String schemaName = resolveSchema(schema, connection);
        List<CheckConstraint> constraints = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("conname");
                    String checkClause = rs.getString("check_clause");

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
                SELECT con.conname, c.relname, a.attname, array_position(con.conkey, a.attnum) AS ordinal
                FROM pg_constraint con
                JOIN pg_class c ON c.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = ANY(con.conkey)
                WHERE con.contype = 'u' AND n.nspname = ?
                ORDER BY c.relname, con.conname, ordinal
                """;
        return readUniqueConstraints(connection, sql, schema, null);
    }


    @Override
    public List<UniqueConstraint> getUniqueConstraints(Connection connection, String catalog, String schema,
            String tableName) throws SQLException {
        String sql = """
                SELECT con.conname, c.relname, a.attname, array_position(con.conkey, a.attnum) AS ordinal
                FROM pg_constraint con
                JOIN pg_class c ON c.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = ANY(con.conkey)
                WHERE con.contype = 'u' AND n.nspname = ? AND c.relname = ?
                ORDER BY con.conname, ordinal
                """;
        return readUniqueConstraints(connection, sql, schema, tableName);
    }


    @Override
    public Optional<List<PrimaryKey>> getAllPrimaryKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT con.conname, c.relname, a.attname, array_position(con.conkey, a.attnum) AS ordinal
                FROM pg_constraint con
                JOIN pg_class c ON c.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = ANY(con.conkey)
                WHERE con.contype = 'p' AND n.nspname = ?
                ORDER BY c.relname, ordinal
                """;
        String schemaName = resolveSchema(schema, connection);
        Map<String, PkBuilder> pkMap = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("conname");
                    String tableName = rs.getString("relname");
                    String columnName = rs.getString("attname");

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
                SELECT con.conname AS fk_name, fk_class.relname AS fk_table, fk_att.attname AS fk_column,
                        pk_class.relname AS pk_table, pk_att.attname AS pk_column,
                        cols.ord AS key_seq, con.confdeltype AS delete_rule, con.confupdtype AS update_rule
                FROM pg_constraint con
                JOIN pg_class fk_class ON fk_class.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = fk_class.relnamespace
                JOIN pg_class pk_class ON pk_class.oid = con.confrelid
                JOIN LATERAL unnest(con.conkey, con.confkey) WITH ORDINALITY AS cols(fk_attnum, pk_attnum, ord) ON TRUE
                JOIN pg_attribute fk_att ON fk_att.attrelid = con.conrelid AND fk_att.attnum = cols.fk_attnum
                JOIN pg_attribute pk_att ON pk_att.attrelid = con.confrelid AND pk_att.attnum = cols.pk_attnum
                WHERE con.contype = 'f' AND n.nspname = ?
                ORDER BY fk_class.relname, con.conname, cols.ord
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
        // Symmetric to getAllImportedKeys: filter by the namespace of the REFERENCED
        // (PK-side) table.
        String sql = """
                SELECT con.conname AS fk_name, fk_class.relname AS fk_table, fk_att.attname AS fk_column,
                        pk_class.relname AS pk_table, pk_att.attname AS pk_column,
                        cols.ord AS key_seq, con.confdeltype AS delete_rule, con.confupdtype AS update_rule
                FROM pg_constraint con
                JOIN pg_class fk_class ON fk_class.oid = con.conrelid
                JOIN pg_class pk_class ON pk_class.oid = con.confrelid
                JOIN pg_namespace pk_n ON pk_n.oid = pk_class.relnamespace
                JOIN LATERAL unnest(con.conkey, con.confkey) WITH ORDINALITY AS cols(fk_attnum, pk_attnum, ord) ON TRUE
                JOIN pg_attribute fk_att ON fk_att.attrelid = con.conrelid AND fk_att.attnum = cols.fk_attnum
                JOIN pg_attribute pk_att ON pk_att.attrelid = con.confrelid AND pk_att.attnum = cols.pk_attnum
                WHERE con.contype = 'f' AND pk_n.nspname = ?
                ORDER BY pk_class.relname, con.conname, cols.ord
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
                SELECT c.relname AS table_name, i_class.relname AS index_name,
                        a.attname AS column_name, array_position(ix.indkey, a.attnum) AS ordinal,
                        ix.indisunique AS is_unique,
                        am.amname AS index_type
                FROM pg_index ix
                JOIN pg_class c ON c.oid = ix.indrelid
                JOIN pg_class i_class ON i_class.oid = ix.indexrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_am am ON am.oid = i_class.relam
                JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = ANY(ix.indkey)
                WHERE n.nspname = ? AND NOT ix.indisprimary
                ORDER BY c.relname, i_class.relname, ordinal
                """;
        String schemaName = resolveSchema(schema, connection);
        Map<String, List<IndexInfoItem>> tableIndexes = new LinkedHashMap<>();
        Map<String, TableReference> tableRefs = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    String indexName = rs.getString("index_name");
                    String columnName = rs.getString("column_name");
                    int ordinal = rs.getInt("ordinal");
                    boolean isUnique = rs.getBoolean("is_unique");
                    String indexType = rs.getString("index_type");

                    IndexInfoItem.IndexType mappedType = mapPgIndexType(indexType);

                    TableReference tableRef = tableRefs.computeIfAbsent(tableName, k -> {
                        Optional<SchemaReference> oSchema = Optional
                                .of(new SchemaReference(Optional.empty(), schemaName));
                        return new TableReference(oSchema, k);
                    });

                    Optional<ColumnReference> colRef = Optional.ofNullable(columnName)
                            .map(cn -> new ColumnReference(Optional.of(tableRef), cn));

                    IndexInfoItem item = new IndexInfoItemRecord(Optional.ofNullable(indexName), mappedType, colRef,
                            ordinal, Optional.empty(), 0L, 0L, Optional.empty(), isUnique);

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


    private static IndexInfoItem.IndexType mapPgIndexType(String amname) {
        if (amname == null) {
            return IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        }
        return switch (amname.toLowerCase()) {
        case "hash" -> IndexInfoItem.IndexType.TABLE_INDEX_HASHED;
        default -> IndexInfoItem.IndexType.TABLE_INDEX_OTHER;
        };
    }


    @Override
    public List<ViewDefinition> getAllViewDefinitions(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT c.relname, pg_get_viewdef(c.oid, true) AS view_body
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relkind = 'v' AND n.nspname = ?
                ORDER BY c.relname
                """;
        String schemaName = resolveSchema(schema, connection);
        List<ViewDefinition> views = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String viewName = rs.getString("relname");
                    String viewBody = rs.getString("view_body");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference viewRef = new TableReference(oSchema, viewName, "VIEW");

                    views.add(new ViewDefinitionRecord(viewRef, Optional.ofNullable(viewBody), Optional.empty()));
                }
            }
        }
        return List.copyOf(views);
    }


    @Override
    public List<MaterializedView> getAllMaterializedViews(Connection connection, String catalog, String schema)
            throws SQLException {
        // pg_class.relkind = 'm' identifies materialized views. pg_get_viewdef works on
        // matview OIDs just as it does on view OIDs. PostgreSQL has no explicit refresh
        // mode (always manual REFRESH MATERIALIZED VIEW) and does not track last
        // refresh.
        String sql = """
                SELECT c.relname, pg_get_viewdef(c.oid, true) AS view_body
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relkind = 'm' AND n.nspname = ?
                ORDER BY c.relname
                """;
        String schemaName = resolveSchema(schema, connection);
        List<MaterializedView> mviews = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("relname");
                    String body = rs.getString("view_body");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference viewRef = new TableReference(oSchema, name, "MATERIALIZED VIEW");

                    mviews.add(new MaterializedViewRecord(viewRef, Optional.ofNullable(body), Optional.empty(),
                            Optional.empty(), Optional.empty()));
                }
            }
        }
        return List.copyOf(mviews);
    }


    @Override
    public List<Procedure> getAllProcedures(Connection connection, String catalog, String schema) throws SQLException {
        String sql = """
                SELECT p.proname AS routine_name, p.oid::text AS specific_name,
                        d.description AS remarks, p.prosrc AS body,
                        pg_get_functiondef(p.oid) AS full_def
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                LEFT JOIN pg_description d ON d.objoid = p.oid AND d.classoid = 'pg_proc'::regclass
                WHERE n.nspname = ? AND p.prokind = 'p'
                ORDER BY p.proname
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Procedure> procedures = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String routineName = rs.getString("routine_name");
                    String specificName = rs.getString("specific_name");
                    String remarks = rs.getString("remarks");
                    String body = rs.getString("body");
                    String fullDef = rs.getString("full_def");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));

                    procedures.add(new ProcedureRecord(new ProcedureReference(oSchema, routineName, specificName),
                            Procedure.ProcedureType.NO_RESULT, Optional.ofNullable(remarks), List.of(),
                            Optional.ofNullable(body), Optional.ofNullable(fullDef), Optional.empty()));
                }
            }
        } catch (SQLException e) {
            // prokind = 'p' requires PostgreSQL 11+
            if (e.getMessage() != null && e.getMessage().contains("prokind")) {
                return List.of();
            }
            throw e;
        }
        return List.copyOf(procedures);
    }


    @Override
    public List<Function> getAllFunctions(Connection connection, String catalog, String schema) throws SQLException {
        String sql = """
                SELECT p.proname AS routine_name, p.oid::text AS specific_name,
                        d.description AS remarks, p.prosrc AS body,
                        pg_get_functiondef(p.oid) AS full_def,
                        CASE WHEN p.proretset THEN 'TABLE' ELSE 'SCALAR' END AS return_type
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                LEFT JOIN pg_description d ON d.objoid = p.oid AND d.classoid = 'pg_proc'::regclass
                WHERE n.nspname = ? AND p.prokind = 'f'
                ORDER BY p.proname
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Function> functions = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String routineName = rs.getString("routine_name");
                    String specificName = rs.getString("specific_name");
                    String remarks = rs.getString("remarks");
                    String body = rs.getString("body");
                    String fullDef = rs.getString("full_def");
                    String returnType = rs.getString("return_type");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));

                    Function.FunctionType funcType = "TABLE".equals(returnType) ? Function.FunctionType.RETURNS_TABLE
                            : Function.FunctionType.NO_TABLE;

                    functions.add(new FunctionRecord(new FunctionReference(oSchema, routineName, specificName),
                            funcType, Optional.ofNullable(remarks), List.of(), Optional.ofNullable(body),
                            Optional.ofNullable(fullDef), Optional.empty()));
                }
            }
        } catch (SQLException e) {
            // prokind = 'f' requires PostgreSQL 11+
            if (e.getMessage() != null && e.getMessage().contains("prokind")) {
                return List.of();
            }
            throw e;
        }
        return List.copyOf(functions);
    }


    @Override
    public List<UserDefinedType> getAllUserDefinedTypes(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT t.typname AS type_name, n.nspname AS schema_name,
                        t.typtype AS type_type,
                        CASE t.typtype
                            WHEN 'c' THEN 'STRUCT'
                            WHEN 'e' THEN 'ENUM'
                            WHEN 'd' THEN 'DISTINCT'
                            ELSE 'OTHER'
                        END AS class_name,
                        obj_description(t.oid, 'pg_type') AS remarks
                FROM pg_type t
                JOIN pg_namespace n ON n.oid = t.typnamespace
                WHERE n.nspname = ?
                    AND t.typtype IN ('c', 'e', 'd')
                    AND NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.reltype = t.oid AND c.relkind IN ('r', 'v', 'm'))
                ORDER BY t.typname
                """;
        String schemaName = resolveSchema(schema, connection);
        List<UserDefinedType> types = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String typeName = rs.getString("type_name");
                    String className = rs.getString("class_name");
                    String typeType = rs.getString("type_type");
                    String remarks = rs.getString("remarks");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));

                    JDBCType baseType = mapPgUdtBaseType(typeType);

                    types.add(new UserDefinedTypeRecord(new UserDefinedTypeReference(oSchema, typeName), className,
                            baseType, Optional.ofNullable(remarks)));
                }
            }
        }
        return List.copyOf(types);
    }


    private static JDBCType mapPgUdtBaseType(String typtype) {
        if (typtype == null) {
            return JDBCType.OTHER;
        }
        return switch (typtype) {
        case "c" -> JDBCType.STRUCT;
        case "e" -> JDBCType.VARCHAR;
        case "d" -> JDBCType.DISTINCT;
        default -> JDBCType.OTHER;
        };
    }


    private Trigger readTrigger(ResultSet rs, String schemaName) throws SQLException {
        String triggerName = rs.getString("trigger_name");
        String tableName = rs.getString("table_name");
        String definition = rs.getString("definition");
        // pg_proc.prosrc is the procedural source of the function the trigger
        // calls — this is what callers need to reconstruct the trigger.
        String procBody = rs.getString("proc_body");

        Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
        TableReference tableRef = new TableReference(oSchema, tableName);

        TriggerTiming timing = parseTriggerTiming(definition);
        TriggerEvent event = parseTriggerEvent(definition);
        Optional<String> orientation = parseTriggerOrientation(definition);

        return new TriggerRecord(new TriggerReference(tableRef, triggerName), timing, event,
                Optional.ofNullable(procBody), Optional.ofNullable(definition), orientation);
    }


    private static Optional<String> parseTriggerOrientation(String definition) {
        if (definition == null)
            return Optional.empty();
        String upper = definition.toUpperCase();
        if (upper.contains("FOR EACH ROW"))
            return Optional.of("ROW");
        if (upper.contains("FOR EACH STATEMENT"))
            return Optional.of("STATEMENT");
        return Optional.empty();
    }


    private ImportedKey readImportedKey(ResultSet rs, String schemaName) throws SQLException {
        String fkName = rs.getString("fk_name");
        String fkTable = rs.getString("fk_table");
        String fkColumn = rs.getString("fk_column");
        String pkTable = rs.getString("pk_table");
        String pkColumn = rs.getString("pk_column");
        int keySeq = rs.getInt("key_seq");
        String deleteRule = rs.getString("delete_rule");
        String updateRule = rs.getString("update_rule");

        // FK side
        Optional<SchemaReference> fkSchemaRef = Optional.of(new SchemaReference(Optional.empty(), schemaName));
        TableReference fkTableRef = new TableReference(fkSchemaRef, fkTable);
        ColumnReference fkColRef = new ColumnReference(Optional.of(fkTableRef), fkColumn);

        // PK side
        Optional<SchemaReference> pkSchemaRef = Optional.of(new SchemaReference(Optional.empty(), schemaName));
        TableReference pkTableRef = new TableReference(pkSchemaRef, pkTable);
        ColumnReference pkColRef = new ColumnReference(Optional.of(pkTableRef), pkColumn);

        return new ImportedKeyRecord(pkColRef, fkColRef, fkName, keySeq, mapPgReferentialAction(updateRule),
                mapPgReferentialAction(deleteRule), Optional.empty(), ImportedKey.Deferrability.NOT_DEFERRABLE);
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
                    String constraintName = rs.getString("conname");
                    String table = rs.getString("relname");
                    String columnName = rs.getString("attname");

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
        return connection.getSchema() != null ? connection.getSchema() : "public";
    }


    private static TriggerTiming parseTriggerTiming(String definition) {
        if (definition == null) {
            return TriggerTiming.AFTER;
        }
        String upper = definition.toUpperCase();
        if (upper.contains("INSTEAD OF")) {
            return TriggerTiming.INSTEAD_OF;
        }
        if (upper.contains("BEFORE")) {
            return TriggerTiming.BEFORE;
        }
        return TriggerTiming.AFTER;
    }


    private static TriggerEvent parseTriggerEvent(String definition) {
        if (definition == null) {
            return TriggerEvent.INSERT;
        }
        String upper = definition.toUpperCase();
        if (upper.contains("DELETE")) {
            return TriggerEvent.DELETE;
        }
        if (upper.contains("UPDATE")) {
            return TriggerEvent.UPDATE;
        }
        return TriggerEvent.INSERT;
    }


    private static ImportedKey.ReferentialAction mapPgReferentialAction(String action) {
        if (action == null) {
            return ImportedKey.ReferentialAction.NO_ACTION;
        }
        return switch (action.toLowerCase()) {
        case "a" -> ImportedKey.ReferentialAction.NO_ACTION;
        case "r" -> ImportedKey.ReferentialAction.RESTRICT;
        case "c" -> ImportedKey.ReferentialAction.CASCADE;
        case "n" -> ImportedKey.ReferentialAction.SET_NULL;
        case "d" -> ImportedKey.ReferentialAction.SET_DEFAULT;
        default -> ImportedKey.ReferentialAction.NO_ACTION;
        };
    }


    @Override
    public Optional<List<TablePrivilege>> getAllTablePrivileges(Connection connection, String catalog,
            String schemaPattern, String tableNamePattern) throws SQLException {
        // information_schema.table_privileges already expands role memberships and
        // PUBLIC grants through the pg_catalog views, so it reports more than
        // JDBC's getTablePrivileges alone.
        StringBuilder sql = new StringBuilder("""
                SELECT table_schema, table_name, grantor, grantee, privilege_type, is_grantable
                FROM information_schema.table_privileges
                WHERE table_schema = ?
                """);
        boolean hasTableFilter = tableNamePattern != null && !tableNamePattern.isBlank()
                && !"%".equals(tableNamePattern);
        if (hasTableFilter) {
            sql.append("  AND table_name LIKE ?\n");
        }
        sql.append("ORDER BY table_name, privilege_type, grantee");

        String schemaName = resolveSchema(schemaPattern, connection);
        List<TablePrivilege> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setString(1, schemaName);
            if (hasTableFilter) {
                ps.setString(2, tableNamePattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    String grantor = rs.getString("grantor");
                    String grantee = rs.getString("grantee");
                    String privilege = rs.getString("privilege_type");
                    String isGrantable = rs.getString("is_grantable");

                    Optional<SchemaReference> oSchema = Optional.of(new SchemaReference(Optional.empty(), schemaName));
                    TableReference tableRef = new TableReference(oSchema, tableName);

                    result.add(new TablePrivilegeRecord(tableRef, Optional.ofNullable(grantor), grantee, privilege,
                            Optional.ofNullable(isGrantable)));
                }
            }
        }
        return Optional.of(List.copyOf(result));
    }


    @Override
    public Optional<List<ColumnPrivilege>> getColumnPrivileges(Connection connection, String catalog, String schema,
            String tableName, String columnNamePattern) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT column_name, grantor, grantee, privilege_type, is_grantable
                FROM information_schema.column_privileges
                WHERE table_schema = ? AND table_name = ?
                """);
        boolean hasColumnFilter = columnNamePattern != null && !columnNamePattern.isBlank()
                && !"%".equals(columnNamePattern);
        if (hasColumnFilter) {
            sql.append("  AND column_name LIKE ?\n");
        }
        sql.append("ORDER BY column_name, privilege_type, grantee");

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
                    String columnName = rs.getString("column_name");
                    String grantor = rs.getString("grantor");
                    String grantee = rs.getString("grantee");
                    String privilege = rs.getString("privilege_type");
                    String isGrantable = rs.getString("is_grantable");

                    ColumnReference colRef = new ColumnReference(Optional.of(tableRef), columnName);
                    result.add(new ColumnPrivilegeRecord(colRef, Optional.ofNullable(grantor), grantee, privilege,
                            Optional.ofNullable(isGrantable)));
                }
            }
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
}
