/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.api.generator;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.model.schema.CatalogReference;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.dialect.api.capability.DialectCapabilitiesProvider;

public interface DdlGenerator extends IdentifierQuoter, DialectCapabilitiesProvider {

    // -------------------- DDL primitives (string-based) --------------------

    /**
     * @param schemaName schema name (may be null)
     * @return SQL statement to clear the table
     */
    String clearTable(String schemaName, String tableName);

    /**
     * @param schemaName schema name (may be null)
     * @param ifExists   whether to include IF EXISTS clause
     * @return SQL statement to drop the table
     */
    String dropTable(String schemaName, String tableName, boolean ifExists);

    /**
     * @param ifExists whether to include IF NOT EXISTS clause
     * @return SQL statement to create the schema
     */
    String createSchema(String schemaName, boolean ifExists);

    /**
     * @param ifExists whether to include IF EXISTS clause
     * @param cascade  whether to include CASCADE clause
     * @return SQL statement to drop the schema
     */
    String dropSchema(String schemaName, boolean ifExists, boolean cascade);

    // -------------------- DDL — descriptor-based --------------------

    default String createTable(TableReference table, List<ColumnDefinition> columns, PrimaryKey primaryKey,
            boolean ifNotExists) {
        StringBuilder sb = new StringBuilder();
        boolean useIfNotExists = ifNotExists && supportsCreateTableIfNotExists();
        sb.append(useIfNotExists ? "CREATE TABLE IF NOT EXISTS " : "CREATE TABLE ");
        sb.append(qualified(table));
        sb.append(" (\n");

        boolean first = true;
        for (ColumnDefinition cd : columns) {
            if (!first)
                sb.append(",\n");
            first = false;
            sb.append("  ").append(quoteIdentifier(cd.column().name()));
            sb.append(' ').append(nativeType(cd.columnMetaData()));
            if (cd.columnMetaData().nullability() == ColumnMetaData.Nullability.NO_NULLS) {
                sb.append(" NOT NULL");
            }
            cd.columnMetaData().columnDefault().ifPresent(d -> sb.append(" DEFAULT ").append(d));
        }
        if (primaryKey != null && !primaryKey.columns().isEmpty()) {
            sb.append(",\n  PRIMARY KEY (");
            sb.append(String.join(", ",
                    primaryKey.columns().stream().map(c -> quoteIdentifier(c.name()).toString()).toList()));
            sb.append(")");
        }
        sb.append("\n)");
        return sb.toString();
    }

    // -------------------- DML --------------------

    /** {@code INSERT INTO schema.table (col1, …) VALUES (?, …)} — parameterised. */
    default String insertInto(TableReference table, List<ColumnDefinition> columns) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty for INSERT");
        }
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        sb.append(qualified(table));
        sb.append(" (");
        appendColumnList(sb, columns);
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append('?');
        }
        sb.append(")");
        return sb.toString();
    }

    /** {@code SELECT col1, … FROM schema.table}. */
    default String selectFrom(TableReference table, List<ColumnDefinition> columns) {
        StringBuilder sb = new StringBuilder("SELECT ");
        if (columns.isEmpty()) {
            sb.append("*");
        } else {
            appendColumnList(sb, columns);
        }
        sb.append(" FROM ").append(qualified(table));
        return sb.toString();
    }

    /** {@code SELECT * FROM schema.table}. */
    default String selectAll(TableReference table) {
        return "SELECT * FROM " + qualified(table);
    }

    default String update(TableReference table, List<ColumnDefinition> setColumns,
            List<ColumnDefinition> whereColumns) {
        if (setColumns.isEmpty()) {
            throw new IllegalArgumentException("setColumns must not be empty for UPDATE");
        }
        StringBuilder sb = new StringBuilder("UPDATE ");
        sb.append(qualified(table));
        sb.append(" SET ");
        for (int i = 0; i < setColumns.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(quoteIdentifier(setColumns.get(i).column().name()));
            sb.append(" = ?");
        }
        appendWhereEqAll(sb, whereColumns);
        return sb.toString();
    }

    /** {@code DELETE FROM schema.table WHERE col1 = ? AND …}. */
    default String deleteFrom(TableReference table, List<ColumnDefinition> whereColumns) {
        StringBuilder sb = new StringBuilder("DELETE FROM ");
        sb.append(qualified(table));
        appendWhereEqAll(sb, whereColumns);
        return sb.toString();
    }

    // -------------------- DDL — drop / truncate --------------------

    /** {@code DROP TABLE [IF EXISTS] schema.table}. */
    default String dropTable(TableReference table, boolean ifExists) {
        return dropTable(table, ifExists, false);
    }

    default String dropTable(TableReference table, boolean ifExists, boolean cascade) {
        String schemaName = table.schema().map(SchemaReference::name).orElse(null);
        String base = dropTable(schemaName, table.name(), ifExists);
        return (cascade && supportsDropTableCascade()) ? base + " CASCADE" : base;
    }

    /** Dialect-specific truncate. */
    default String truncate(TableReference table) {
        String schemaName = table.schema().map(SchemaReference::name).orElse(null);
        return clearTable(schemaName, table.name());
    }

    // -------------------- DDL — ALTER --------------------

    /**
     * {@code ALTER TABLE schema.table ADD COLUMN col TYPE [NOT NULL] [DEFAULT …]}.
     */
    default String alterTableAddColumn(TableReference table, ColumnDefinition column) {
        StringBuilder sb = new StringBuilder("ALTER TABLE ");
        sb.append(qualified(table));
        sb.append(" ADD COLUMN ");
        sb.append(quoteIdentifier(column.column().name()));
        sb.append(' ').append(nativeType(column.columnMetaData()));
        if (column.columnMetaData().nullability() == ColumnMetaData.Nullability.NO_NULLS) {
            sb.append(" NOT NULL");
        }
        column.columnMetaData().columnDefault().ifPresent(d -> sb.append(" DEFAULT ").append(d));
        return sb.toString();
    }

    /** {@code ALTER TABLE schema.table DROP COLUMN col}. */
    default String alterTableDropColumn(TableReference table, String columnName) {
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" DROP COLUMN ")
                .append(quoteIdentifier(columnName)).toString();
    }

    default String alterColumnType(TableReference table, String columnName, ColumnMetaData newMeta) {
        if (newMeta == null) {
            throw new IllegalArgumentException("newMeta must not be null for ALTER COLUMN TYPE");
        }
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" ALTER COLUMN ")
                .append(quoteIdentifier(columnName)).append(" TYPE ").append(nativeType(newMeta)).toString();
    }

    default String alterColumnSetNullability(TableReference table, String columnName, boolean nullable) {
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" ALTER COLUMN ")
                .append(quoteIdentifier(columnName)).append(nullable ? " DROP NOT NULL" : " SET NOT NULL").toString();
    }

    default String alterColumnSetNullability(TableReference table, String columnName, boolean nullable,
            ColumnMetaData currentMeta) {
        return alterColumnSetNullability(table, columnName, nullable);
    }

    default String alterColumnSetDefault(TableReference table, String columnName, String defaultExpression) {
        if (defaultExpression == null || defaultExpression.isBlank()) {
            throw new IllegalArgumentException("defaultExpression must not be blank for SET DEFAULT");
        }
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" ALTER COLUMN ")
                .append(quoteIdentifier(columnName)).append(" SET DEFAULT ").append(defaultExpression).toString();
    }

    /** {@code ALTER TABLE schema.table ALTER COLUMN col DROP DEFAULT}. */
    default String alterColumnDropDefault(TableReference table, String columnName) {
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" ALTER COLUMN ")
                .append(quoteIdentifier(columnName)).append(" DROP DEFAULT").toString();
    }

    default String renameColumn(TableReference table, String oldName, String newName) {
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" RENAME COLUMN ")
                .append(quoteIdentifier(oldName)).append(" TO ").append(quoteIdentifier(newName)).toString();
    }

    default String renameTable(TableReference table, String newName) {
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" RENAME TO ")
                .append(quoteIdentifier(newName)).toString();
    }

    default String renameIndex(String oldName, String newName, TableReference table) {
        return new StringBuilder("ALTER INDEX ").append(quoteIdentifier(oldName)).append(" RENAME TO ")
                .append(quoteIdentifier(newName)).toString();
    }

    default String renameConstraint(TableReference table, String oldName, String newName) {
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" RENAME CONSTRAINT ")
                .append(quoteIdentifier(oldName)).append(" TO ").append(quoteIdentifier(newName)).toString();
    }

    // -------------------- ALTER — ColumnReference-friendly overloads
    // --------------------
    //
    // ColumnReference carries the column name plus an optional parent table, so
    // callers that already hold a ColumnReference can skip restating the
    // TableReference. {@code column.table()} must be present for these overloads;
    // otherwise an IllegalArgumentException is thrown.

    private static TableReference requireTable(ColumnReference column) {
        if (column == null || column.table().isEmpty()) {
            throw new IllegalArgumentException("ColumnReference must carry a parent TableReference");
        }
        return column.table().get();
    }

    /** ALTER COLUMN TYPE driven by a {@link ColumnReference}. */
    default String alterColumnType(ColumnReference column, ColumnMetaData newMeta) {
        return alterColumnType(requireTable(column), column.name(), newMeta);
    }

    /** ALTER COLUMN nullability driven by a {@link ColumnReference}. */
    default String alterColumnSetNullability(ColumnReference column, boolean nullable) {
        return alterColumnSetNullability(requireTable(column), column.name(), nullable);
    }

    /**
     * ALTER COLUMN nullability (type-aware) driven by a {@link ColumnReference}.
     */
    default String alterColumnSetNullability(ColumnReference column, boolean nullable, ColumnMetaData currentMeta) {
        return alterColumnSetNullability(requireTable(column), column.name(), nullable, currentMeta);
    }

    /** ALTER COLUMN SET DEFAULT driven by a {@link ColumnReference}. */
    default String alterColumnSetDefault(ColumnReference column, String defaultExpression) {
        return alterColumnSetDefault(requireTable(column), column.name(), defaultExpression);
    }

    /** ALTER COLUMN DROP DEFAULT driven by a {@link ColumnReference}. */
    default String alterColumnDropDefault(ColumnReference column) {
        return alterColumnDropDefault(requireTable(column), column.name());
    }

    /**
     * RENAME COLUMN driven by a {@link ColumnReference} (the column being renamed).
     */
    default String renameColumn(ColumnReference column, String newName) {
        return renameColumn(requireTable(column), column.name(), newName);
    }

    // -------------------- DDL — indexes --------------------

    default String createIndex(String indexName, TableReference table, List<String> columnNames, boolean unique,
            boolean ifNotExists) {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new IllegalArgumentException("columnNames must not be empty for CREATE INDEX");
        }
        StringBuilder sb = new StringBuilder("CREATE ");
        if (unique)
            sb.append("UNIQUE ");
        sb.append("INDEX ");
        if (ifNotExists && supportsCreateIndexIfNotExists()) {
            sb.append("IF NOT EXISTS ");
        }
        sb.append(quoteIdentifier(indexName));
        sb.append(" ON ").append(qualified(table));
        sb.append(" (");
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(quoteIdentifier(columnNames.get(i)));
        }
        sb.append(")");
        return sb.toString();
    }

    /** {@code DROP INDEX [IF EXISTS] idx}. SQL-99 form (no {@code ON table}). */
    default String dropIndex(String indexName, boolean ifExists) {
        StringBuilder sb = new StringBuilder("DROP INDEX ");
        if (ifExists && supportsDropIndexIfExists())
            sb.append("IF EXISTS ");
        sb.append(quoteIdentifier(indexName));
        return sb.toString();
    }

    default String dropIndex(String indexName, TableReference table, boolean ifExists) {
        StringBuilder sb = new StringBuilder("DROP INDEX ");
        if (ifExists && supportsDropIndexIfExists())
            sb.append("IF EXISTS ");
        if (!dropIndexRequiresTable() && table != null && table.schema().isPresent()) {
            sb.append(quoteIdentifier(table.schema().get().name(), indexName));
        } else {
            sb.append(quoteIdentifier(indexName));
        }
        if (dropIndexRequiresTable() && table != null) {
            sb.append(" ON ").append(qualified(table));
        }
        return sb.toString();
    }

    // -------------------- DDL — views --------------------

    default String createView(TableReference view, String selectSql, boolean orReplace) {
        if (selectSql == null || selectSql.isBlank()) {
            throw new IllegalArgumentException("selectSql must not be blank for CREATE VIEW");
        }
        StringBuilder sb = new StringBuilder("CREATE ");
        if (orReplace && supportsCreateOrReplaceView())
            sb.append("OR REPLACE ");
        sb.append("VIEW ");
        sb.append(qualified(view));
        sb.append(" AS ");
        sb.append(selectSql);
        return sb.toString();
    }

    /** {@code DROP VIEW [IF EXISTS] schema.view}. */
    default String dropView(TableReference view, boolean ifExists) {
        StringBuilder sb = new StringBuilder("DROP VIEW ");
        if (ifExists && supportsDropViewIfExists())
            sb.append("IF EXISTS ");
        sb.append(qualified(view));
        return sb.toString();
    }

    // -------------------- DDL — sequences --------------------

    record SequenceDefinition(String schemaName, String name, Long startWith, Long incrementBy, Long minValue,
            Long maxValue, Boolean cycle, Long cache) {

        public SequenceDefinition {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("sequence name must not be blank");
            }
        }

        /** Plain unqualified sequence with default options. */
        public static SequenceDefinition of(String name) {
            return new SequenceDefinition(null, name, null, null, null, null, null, null);
        }

        /** Schema-qualified sequence with default options. */
        public static SequenceDefinition of(String schemaName, String name) {
            return new SequenceDefinition(schemaName, name, null, null, null, null, null, null);
        }
    }

    default Optional<String> createSequence(SequenceDefinition seq, boolean ifNotExists) {
        if (!supportsSequences()) {
            return Optional.empty();
        }
        StringBuilder sb = new StringBuilder("CREATE SEQUENCE ");
        if (ifNotExists)
            sb.append("IF NOT EXISTS ");
        sb.append(quoteIdentifier(seq.schemaName(), seq.name()));
        if (seq.startWith() != null)
            sb.append(" START WITH ").append(seq.startWith());
        if (seq.incrementBy() != null)
            sb.append(" INCREMENT BY ").append(seq.incrementBy());
        if (seq.minValue() != null)
            sb.append(" MINVALUE ").append(seq.minValue());
        if (seq.maxValue() != null)
            sb.append(" MAXVALUE ").append(seq.maxValue());
        if (seq.cycle() != null)
            sb.append(seq.cycle() ? " CYCLE" : " NO CYCLE");
        if (seq.cache() != null)
            sb.append(" CACHE ").append(seq.cache());
        return Optional.of(sb.toString());
    }

    default Optional<String> dropSequence(String schemaName, String name, boolean ifExists) {
        if (!supportsSequences()) {
            return Optional.empty();
        }
        StringBuilder sb = new StringBuilder("DROP SEQUENCE ");
        if (ifExists)
            sb.append("IF EXISTS ");
        sb.append(quoteIdentifier(schemaName, name));
        return Optional.of(sb.toString());
    }

    /**
     * {@code NEXT VALUE FOR schema.name}. Returns empty on dialects without
     * sequences.
     */
    default Optional<String> nextValueFor(String schemaName, String name) {
        if (!supportsSequences()) {
            return Optional.empty();
        }
        return Optional.of("NEXT VALUE FOR " + quoteIdentifier(schemaName, name));
    }

    // -------------------- DDL — table-level constraints --------------------

    /**
     * {@code ALTER TABLE schema.table ADD CONSTRAINT name PRIMARY KEY (col1, …)}.
     */
    default String addPrimaryKeyConstraint(TableReference table, String constraintName, List<String> columnNames) {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new IllegalArgumentException("columnNames must not be empty for PRIMARY KEY");
        }
        StringBuilder sb = new StringBuilder("ALTER TABLE ");
        sb.append(qualified(table));
        sb.append(" ADD CONSTRAINT ").append(quoteIdentifier(constraintName));
        sb.append(" PRIMARY KEY (");
        appendQuotedNames(sb, columnNames);
        sb.append(")");
        return sb.toString();
    }

    /** {@code ALTER TABLE schema.table ADD CONSTRAINT name UNIQUE (col1, …)}. */
    default String addUniqueConstraint(TableReference table, String constraintName, List<String> columnNames) {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new IllegalArgumentException("columnNames must not be empty for UNIQUE");
        }
        StringBuilder sb = new StringBuilder("ALTER TABLE ");
        sb.append(qualified(table));
        sb.append(" ADD CONSTRAINT ").append(quoteIdentifier(constraintName));
        sb.append(" UNIQUE (");
        appendQuotedNames(sb, columnNames);
        sb.append(")");
        return sb.toString();
    }

    default String addForeignKeyConstraint(TableReference table, String constraintName, List<String> fkColumns,
            TableReference referencedTable, List<String> referencedColumns, String onDelete, String onUpdate) {
        if (fkColumns == null || fkColumns.isEmpty()) {
            throw new IllegalArgumentException("fkColumns must not be empty for FOREIGN KEY");
        }
        if (referencedColumns == null || referencedColumns.isEmpty()) {
            throw new IllegalArgumentException("referencedColumns must not be empty for FOREIGN KEY");
        }
        StringBuilder sb = new StringBuilder("ALTER TABLE ");
        sb.append(qualified(table));
        sb.append(" ADD CONSTRAINT ").append(quoteIdentifier(constraintName));
        sb.append(" FOREIGN KEY (");
        appendQuotedNames(sb, fkColumns);
        sb.append(") REFERENCES ");
        sb.append(qualified(referencedTable));
        sb.append(" (");
        appendQuotedNames(sb, referencedColumns);
        sb.append(")");
        if (onDelete != null && !onDelete.isBlank())
            sb.append(" ON DELETE ").append(onDelete);
        if (onUpdate != null && !onUpdate.isBlank())
            sb.append(" ON UPDATE ").append(onUpdate);
        return sb.toString();
    }

    /** {@code ALTER TABLE schema.table ADD CONSTRAINT name CHECK (expression)}. */
    default String addCheckConstraint(TableReference table, String constraintName, String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression must not be blank for CHECK");
        }
        StringBuilder sb = new StringBuilder("ALTER TABLE ");
        sb.append(qualified(table));
        sb.append(" ADD CONSTRAINT ").append(quoteIdentifier(constraintName));
        sb.append(" CHECK (").append(expression).append(")");
        return sb.toString();
    }

    default String dropConstraint(TableReference table, String constraintName, boolean ifExists) {
        StringBuilder sb = new StringBuilder("ALTER TABLE ");
        sb.append(qualified(table));
        sb.append(" DROP CONSTRAINT ");
        if (ifExists && supportsDropConstraintIfExists())
            sb.append("IF EXISTS ");
        sb.append(quoteIdentifier(constraintName));
        return sb.toString();
    }

    // -------------------- DDL — triggers --------------------

    /** Render the SQL keyword for a {@link Trigger.TriggerTiming}. */
    static String triggerTimingKeyword(Trigger.TriggerTiming t) {
        return t == Trigger.TriggerTiming.INSTEAD_OF ? "INSTEAD OF" : t.name();
    }

    default String createTrigger(String triggerName, Trigger.TriggerTiming timing, Trigger.TriggerEvent event,
            TableReference table, Trigger.TriggerScope scope, String whenCondition, String body) {
        return createTrigger(triggerName, timing, event, table, scope, whenCondition, body, false);
    }

    default String createTrigger(String triggerName, Trigger.TriggerTiming timing, Trigger.TriggerEvent event,
            TableReference table, Trigger.TriggerScope scope, String whenCondition, String body, boolean orReplace) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank for CREATE TRIGGER");
        }
        StringBuilder sb = new StringBuilder("CREATE ");
        if (orReplace && supportsCreateOrReplaceTrigger())
            sb.append("OR REPLACE ");
        sb.append("TRIGGER ");
        sb.append(quoteIdentifier(triggerName));
        sb.append(' ').append(triggerTimingKeyword(timing)).append(' ').append(event);
        sb.append(" ON ").append(qualified(table));
        sb.append(' ').append(scope.forEachClause());
        if (whenCondition != null && !whenCondition.isBlank()) {
            sb.append(" WHEN (").append(whenCondition).append(")");
        }
        sb.append(' ').append(body);
        return sb.toString();
    }

    default Optional<String> createTriggerProcedure(String procedureName, String schemaName, String body) {
        return Optional.empty();
    }

    default String createTriggerUsingProcedure(String triggerName, String schemaName, Trigger.TriggerTiming timing,
            Trigger.TriggerEvent event, TableReference table, Trigger.TriggerScope scope, String whenCondition,
            String procedureName) {
        throw new UnsupportedOperationException(
                "This dialect does not support separate trigger procedures. Use createTrigger with an inline body.");
    }

    default Optional<String> dropProcedure(String procedureName, String schemaName, boolean ifExists) {
        return Optional.empty();
    }

    default java.util.List<String> dropTriggerOnTable(String triggerName, TableReference table, boolean ifExists) {
        return java.util.List.of(dropTrigger(triggerName, ifExists));
    }

    /** {@code DROP TRIGGER [IF EXISTS] name}. */
    default String dropTrigger(String triggerName, boolean ifExists) {
        StringBuilder sb = new StringBuilder("DROP TRIGGER ");
        if (ifExists)
            sb.append("IF EXISTS ");
        sb.append(quoteIdentifier(triggerName));
        return sb.toString();
    }

    // -------------------- helpers --------------------

    /** Dialect-quoted, fully-qualified {@code "schema"."table"}. */
    default String qualified(TableReference table) {
        String schemaName = table.schema().map(SchemaReference::name).orElse(null);
        return quoteIdentifier(schemaName, table.name());
    }

    default String nativeType(ColumnMetaData meta) {
        String tn = meta.typeName();
        if (tn != null && !tn.isBlank() && !"UNKNOWN".equalsIgnoreCase(tn)) {
            return applyLengthAndScale(tn, meta);
        }
        return defaultTypeName(meta);
    }

    default List<ColumnDefinition> columnsOf(TableReference table, List<ColumnDefinition> allColumns) {
        String sKey = schemaKey(table);
        List<ColumnDefinition> out = new ArrayList<>();
        for (ColumnDefinition cd : allColumns) {
            ColumnReference colRef = cd.column();
            if (colRef.table().isEmpty())
                continue;
            TableReference tr = colRef.table().get();
            if (!table.name().equals(tr.name()))
                continue;
            if (!sKey.equals(schemaKey(tr)))
                continue;
            out.add(cd);
        }
        return out;
    }

    // -------------------- private helpers --------------------

    private void appendQuotedNames(StringBuilder sb, List<String> names) {
        for (int i = 0; i < names.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(quoteIdentifier(names.get(i)));
        }
    }

    private void appendWhereEqAll(StringBuilder sb, List<ColumnDefinition> whereColumns) {
        if (whereColumns == null || whereColumns.isEmpty()) {
            return;
        }
        sb.append(" WHERE ");
        for (int i = 0; i < whereColumns.size(); i++) {
            if (i > 0)
                sb.append(" AND ");
            sb.append(quoteIdentifier(whereColumns.get(i).column().name()));
            sb.append(" = ?");
        }
    }

    private void appendColumnList(StringBuilder sb, List<ColumnDefinition> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(quoteIdentifier(columns.get(i).column().name()));
        }
    }

    private static String applyLengthAndScale(String typeName, ColumnMetaData meta) {
        if (typeName.contains("("))
            return typeName;
        JDBCType jt = meta.dataType();
        if (jt == null)
            return typeName;
        OptionalInt size = meta.columnSize();
        OptionalInt scale = meta.decimalDigits();
        return switch (jt) {
        case CHAR, VARCHAR, NCHAR, NVARCHAR, LONGVARCHAR, LONGNVARCHAR ->
            size.isPresent() ? typeName + "(" + size.getAsInt() + ")" : typeName;
        case NUMERIC, DECIMAL -> {
            if (size.isPresent() && scale.isPresent()) {
                yield typeName + "(" + size.getAsInt() + ", " + scale.getAsInt() + ")";
            } else if (size.isPresent()) {
                yield typeName + "(" + size.getAsInt() + ")";
            } else {
                yield typeName;
            }
        }
        default -> typeName;
        };
    }

    private static String defaultTypeName(ColumnMetaData meta) {
        JDBCType jt = meta.dataType();
        OptionalInt size = meta.columnSize();
        OptionalInt scale = meta.decimalDigits();
        return switch (jt) {
        case BIT, BOOLEAN -> "BOOLEAN";
        case TINYINT -> "TINYINT";
        case SMALLINT -> "SMALLINT";
        case INTEGER -> "INTEGER";
        case BIGINT -> "BIGINT";
        case FLOAT, REAL -> "REAL";
        case DOUBLE -> "DOUBLE PRECISION";
        case NUMERIC, DECIMAL -> {
            if (size.isPresent() && scale.isPresent()) {
                yield "DECIMAL(" + size.getAsInt() + ", " + scale.getAsInt() + ")";
            } else if (size.isPresent()) {
                yield "DECIMAL(" + size.getAsInt() + ")";
            } else {
                yield "DECIMAL";
            }
        }
        case DATE -> "DATE";
        case TIME, TIME_WITH_TIMEZONE -> "TIME";
        case TIMESTAMP, TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP";
        case CHAR -> size.isPresent() ? "CHAR(" + size.getAsInt() + ")" : "CHAR(1)";
        case VARCHAR, LONGVARCHAR, NVARCHAR, LONGNVARCHAR, NCHAR ->
            size.isPresent() ? "VARCHAR(" + size.getAsInt() + ")" : "VARCHAR(255)";
        case BINARY, VARBINARY, LONGVARBINARY, BLOB -> "VARBINARY";
        case CLOB, NCLOB -> "CLOB";
        case null, default -> "VARCHAR(255)";
        };
    }

    private static String schemaKey(TableReference tbl) {
        String s = tbl.schema().map(SchemaReference::name).orElse("");
        String c = tbl.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse("");
        return c + "" + s;
    }
}
