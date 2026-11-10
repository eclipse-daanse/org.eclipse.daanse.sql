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

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Sequence;
import org.eclipse.daanse.sql.jdbc.api.schema.SequenceReference;
import org.eclipse.daanse.sql.jdbc.api.schema.UniqueConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.ViewDefinition;
import org.eclipse.daanse.sql.jdbc.record.schema.CheckConstraintRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ImportedKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoItemRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.SequenceRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.UniqueConstraintRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ViewDefinitionRecord;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;

/**
 * Reads DuckDB's own catalog functions — {@code duckdb_constraints()}, {@code duckdb_indexes()},
 * {@code duckdb_views()} and {@code duckdb_sequences()} — of the current database. Its JDBC driver
 * reports an index without its columns, and no check or unique constraint at all.
 *
 * <p>DuckDB does not keep the name a constraint was given; it names each constraint after its table
 * and columns. A foreign key refers to a table of its own schema, as DuckDB has no other. Its
 * referential action is always {@code NO ACTION}.</p>
 */
public class DuckDbMetadataProvider implements MetadataProvider {

    private static final String CONSTRAINTS = """
            SELECT table_name, constraint_name, constraint_column_names, expression,
                    referenced_table, referenced_column_names
            FROM duckdb_constraints()
            WHERE database_name = current_database() AND schema_name = ? AND constraint_type = ?
            ORDER BY table_name, constraint_index
            """;

    /** The query of a view: what follows the first {@code AS} after its name and column list. */
    private static final Pattern VIEW_QUERY = Pattern
            .compile("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\s+.+?\\s+AS\\s+(.*?)\\s*;?\\s*$");

    /** A plain or a quoted identifier: an index on it is an index on that column. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*|\"(?:[^\"]|\"\")+\"");

    @Override
    public Optional<List<PrimaryKey>> getAllPrimaryKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        List<PrimaryKey> keys = new ArrayList<>();
        try (PreparedStatement ps = constraints(connection, schemaName, "PRIMARY KEY");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                TableReference table = table(schemaName, rs.getString("table_name"));
                keys.add(new PrimaryKeyRecord(table, columns(table, rs.getArray("constraint_column_names")),
                        Optional.ofNullable(rs.getString("constraint_name"))));
            }
        }
        return Optional.of(List.copyOf(keys));
    }

    @Override
    public Optional<List<ImportedKey>> getAllImportedKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        return Optional.of(foreignKeys(connection, resolveSchema(schema, connection)));
    }

    /** A foreign key and the key it refers to lie in one schema, so the exported keys are the imported ones. */
    @Override
    public Optional<List<ImportedKey>> getAllExportedKeys(Connection connection, String catalog, String schema)
            throws SQLException {
        return Optional.of(foreignKeys(connection, resolveSchema(schema, connection)));
    }

    private List<ImportedKey> foreignKeys(Connection connection, String schemaName) throws SQLException {
        List<ImportedKey> keys = new ArrayList<>();
        try (PreparedStatement ps = constraints(connection, schemaName, "FOREIGN KEY");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                TableReference table = table(schemaName, rs.getString("table_name"));
                TableReference referenced = table(schemaName, rs.getString("referenced_table"));
                List<ColumnReference> columns = columns(table, rs.getArray("constraint_column_names"));
                List<ColumnReference> referencedColumns = columns(referenced,
                        rs.getArray("referenced_column_names"));
                String name = rs.getString("constraint_name");
                for (int i = 0; i < columns.size() && i < referencedColumns.size(); i++) {
                    keys.add(new ImportedKeyRecord(referencedColumns.get(i), columns.get(i), name, i + 1,
                            ImportedKey.ReferentialAction.NO_ACTION, ImportedKey.ReferentialAction.NO_ACTION,
                            Optional.empty(), ImportedKey.Deferrability.NOT_DEFERRABLE));
                }
            }
        }
        return List.copyOf(keys);
    }

    @Override
    public List<CheckConstraint> getAllCheckConstraints(Connection connection, String catalog, String schema)
            throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        List<CheckConstraint> checks = new ArrayList<>();
        try (PreparedStatement ps = constraints(connection, schemaName, "CHECK"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                checks.add(new CheckConstraintRecord(rs.getString("constraint_name"),
                        table(schemaName, rs.getString("table_name")), rs.getString("expression")));
            }
        }
        return List.copyOf(checks);
    }

    @Override
    public List<UniqueConstraint> getAllUniqueConstraints(Connection connection, String catalog, String schema)
            throws SQLException {
        String schemaName = resolveSchema(schema, connection);
        List<UniqueConstraint> constraints = new ArrayList<>();
        try (PreparedStatement ps = constraints(connection, schemaName, "UNIQUE"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                TableReference table = table(schemaName, rs.getString("table_name"));
                constraints.add(new UniqueConstraintRecord(rs.getString("constraint_name"), table,
                        columns(table, rs.getArray("constraint_column_names"))));
            }
        }
        return List.copyOf(constraints);
    }

    /**
     * The indexes created with {@code CREATE INDEX}; those behind a primary key or unique constraint
     * are not listed by DuckDB. An index on an expression has an item without a column.
     */
    @Override
    public Optional<List<IndexInfo>> getAllIndexInfo(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT table_name, index_name, is_unique, expressions
                FROM duckdb_indexes()
                WHERE database_name = current_database() AND schema_name = ?
                ORDER BY table_name, index_name
                """;
        String schemaName = resolveSchema(schema, connection);
        Map<String, List<IndexInfoItem>> byTable = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    TableReference table = table(schemaName, tableName);
                    String indexName = rs.getString("index_name");
                    boolean unique = rs.getBoolean("is_unique");
                    List<String> expressions = elements(rs.getObject("expressions"));
                    List<IndexInfoItem> items = byTable.computeIfAbsent(tableName, k -> new ArrayList<>());
                    for (int i = 0; i < expressions.size(); i++) {
                        items.add(new IndexInfoItemRecord(Optional.of(indexName), IndexInfoItem.IndexType.TABLE_INDEX_OTHER,
                                column(table, expressions.get(i)), i + 1, Optional.empty(), 0L, 0L,
                                Optional.empty(), unique));
                    }
                }
            }
        }
        List<IndexInfo> indexes = new ArrayList<>();
        byTable.forEach((tableName, items) -> indexes
                .add(new IndexInfoRecord(table(schemaName, tableName), List.copyOf(items))));
        return Optional.of(List.copyOf(indexes));
    }

    @Override
    public List<ViewDefinition> getAllViewDefinitions(Connection connection, String catalog, String schema)
            throws SQLException {
        String sql = """
                SELECT view_name, sql
                FROM duckdb_views()
                WHERE database_name = current_database() AND schema_name = ? AND NOT internal
                ORDER BY view_name
                """;
        String schemaName = resolveSchema(schema, connection);
        List<ViewDefinition> views = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String definition = rs.getString("sql");
                    Optional<String> query = Optional.ofNullable(definition).map(VIEW_QUERY::matcher)
                            .filter(Matcher::matches).map(m -> m.group(1));
                    views.add(new ViewDefinitionRecord(
                            new TableReference(schemaRef(schemaName), rs.getString("view_name"), "VIEW"), query,
                            Optional.ofNullable(definition)));
                }
            }
        }
        return List.copyOf(views);
    }

    @Override
    public List<Sequence> getAllSequences(Connection connection, String catalog, String schema) throws SQLException {
        String sql = """
                SELECT sequence_name, start_value, min_value, max_value, increment_by, cycle
                FROM duckdb_sequences()
                WHERE database_name = current_database() AND schema_name = ?
                ORDER BY sequence_name
                """;
        String schemaName = resolveSchema(schema, connection);
        List<Sequence> sequences = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sequences.add(new SequenceRecord(
                            new SequenceReference(schemaRef(schemaName), rs.getString("sequence_name")),
                            rs.getLong("start_value"), rs.getLong("increment_by"),
                            Optional.of(rs.getLong("min_value")), Optional.of(rs.getLong("max_value")),
                            rs.getBoolean("cycle"), Optional.empty(), Optional.empty()));
                }
            }
        }
        return List.copyOf(sequences);
    }

    private static PreparedStatement constraints(Connection connection, String schemaName, String type)
            throws SQLException {
        PreparedStatement ps = connection.prepareStatement(CONSTRAINTS);
        ps.setString(1, schemaName);
        ps.setString(2, type);
        return ps;
    }

    private static List<ColumnReference> columns(TableReference table, Array names) throws SQLException {
        List<ColumnReference> columns = new ArrayList<>();
        for (String name : elements(names)) {
            columns.add(new ColumnReference(Optional.of(table), name));
        }
        return List.copyOf(columns);
    }

    /** The column an index expression names, or none when it is an expression. */
    private static Optional<ColumnReference> column(TableReference table, String expression) {
        String trimmed = expression.trim();
        if (!IDENTIFIER.matcher(trimmed).matches()) {
            return Optional.empty();
        }
        String name = trimmed.startsWith("\"") ? trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"")
                : trimmed;
        return Optional.of(new ColumnReference(Optional.of(table), name));
    }

    /** The elements of a DuckDB list, read as an SQL array or, where the driver gives text, from {@code [a, b]}. */
    private static List<String> elements(Object list) throws SQLException {
        if (list == null) {
            return List.of();
        }
        if (list instanceof Array array) {
            List<String> out = new ArrayList<>();
            for (Object element : (Object[]) array.getArray()) {
                out.add(String.valueOf(element));
            }
            return out;
        }
        return splitList(list.toString());
    }

    /**
     * {@code [a, f(b, c), '"D, e"']} → {@code a}, {@code f(b, c)}, {@code "D, e"}: commas inside
     * parentheses or quotes stay, and an element DuckDB writes as a string literal, as it does one
     * that holds a quoted identifier, is unwrapped.
     */
    static List<String> splitList(String text) {
        String inner = text.trim();
        if (inner.startsWith("[") && inner.endsWith("]")) {
            inner = inner.substring(1, inner.length() - 1);
        }
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        Character quote = null;
        for (char c : inner.toCharArray()) {
            if (quote != null) {
                if (c == quote) {
                    quote = null;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                out.add(unwrap(current.toString().trim()));
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.toString().isBlank()) {
            out.add(unwrap(current.toString().trim()));
        }
        return out;
    }

    /** {@code '"NAME"'} → {@code "NAME"}: the text of an element written as a string literal. */
    private static String unwrap(String element) {
        if (element.length() >= 2 && element.startsWith("'") && element.endsWith("'")) {
            return element.substring(1, element.length() - 1).replace("''", "'");
        }
        return element;
    }

    private static TableReference table(String schemaName, String tableName) {
        return new TableReference(schemaRef(schemaName), tableName);
    }

    private static Optional<SchemaReference> schemaRef(String schemaName) {
        return Optional.of(new SchemaReference(Optional.empty(), schemaName));
    }

    private static String resolveSchema(String schema, Connection connection) throws SQLException {
        if (schema != null) {
            return schema;
        }
        return connection.getSchema() != null ? connection.getSchema() : "main";
    }
}
