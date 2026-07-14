/*
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * For more information please visit the Project: Hitachi Vantara - Mondrian
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial adapt parts of Syntax.class
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.dialect.db.mssqlserver;

import java.sql.Date;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming;
import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.eclipse.daanse.sql.dialect.api.generator.StatementHint;
import org.eclipse.daanse.sql.model.sql.OrderedColumn;
import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;
import org.eclipse.daanse.sql.dialect.db.common.DialectUtil;

/**
 * @author jhyde
 * @since Nov 23, 2008
 */
public class MicrosoftSqlServerDialect extends AbstractJdbcDialect {

    private final DateFormat df = new SimpleDateFormat("yyyyMMdd");

    private static final String SUPPORTED_PRODUCT_NAME = "MSSQL";

    private volatile org.eclipse.daanse.sql.dialect.api.generator.ReturningGenerator cachedReturningGenerator;
    private volatile org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator cachedMergeGenerator;
    private volatile org.eclipse.daanse.sql.dialect.api.generator.CteGenerator cachedCteGenerator;

    public MicrosoftSqlServerDialect() {
        super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults());
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public MicrosoftSqlServerDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator paginationGenerator() {
        return new org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator() {
            @Override
            public java.util.Optional<String> selectPrefix(java.util.OptionalLong limit, java.util.OptionalLong offset) {
                // SQL Server expresses a pure row cap as a leading "TOP n". When an offset is
                // present it uses the trailing OFFSET/FETCH form instead (see paginate).
                if (limit.isPresent() && offset.isEmpty()) {
                    return java.util.Optional.of("TOP " + limit.getAsLong());
                }
                return java.util.Optional.empty();
            }

            @Override
            public String paginate(java.util.OptionalLong limit, java.util.OptionalLong offset) {
                // No offset → the cap is emitted as a leading TOP (selectPrefix), so nothing
                // trailing. With an offset, fall back to ANSI OFFSET/FETCH (SQL Server 2012+).
                return offset.isEmpty()
                        ? ""
                        : org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator.super.paginate(limit,
                                offset);
            }
        };
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.ReturningGenerator returningGenerator() {
        var local = cachedReturningGenerator;
        if (local != null)
            return local;
        local = new org.eclipse.daanse.sql.dialect.api.generator.ReturningGenerator() {
            @Override
            public boolean supportsReturning() {
                return true;
            }

            @Override
            public java.util.Optional<String> returning(java.util.List<String> columns) {
                if (columns == null || columns.isEmpty())
                    return java.util.Optional.empty();
                if (columns.size() == 1 && "*".equals(columns.get(0))) {
                    return java.util.Optional.of(" OUTPUT INSERTED.*");
                }
                StringBuilder sb = new StringBuilder(" OUTPUT ");
                boolean first = true;
                for (String c : columns) {
                    if (!first)
                        sb.append(", ");
                    sb.append("INSERTED.").append(quoteIdentifier(c));
                    first = false;
                }
                return java.util.Optional.of(sb.toString());
            }
        };
        cachedReturningGenerator = local;
        return local;
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator mergeGenerator() {
        var local = cachedMergeGenerator;
        if (local != null)
            return local;
        // SQL Server major version map: 2008=10, 2012=11, 2014=12, 2016=13, 2017=14,
        // 2019=15, 2022=16.
        if (!dialectVersion.isUnknownOrAtLeast(10, 0)) {
            local = super.mergeGenerator();
            cachedMergeGenerator = local;
            return local;
        }
        local = new org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator() {
            @Override
            public boolean supportsMerge() {
                return true;
            }

            @Override
            public java.util.Optional<String> upsert(UpsertSpec spec, java.util.List<String> values) {
                if (values == null || values.size() != spec.insertColumns().size()) {
                    throw new IllegalArgumentException("values must match insertColumns in length");
                }
                StringBuilder sb = new StringBuilder("MERGE INTO ").append(qualified(spec.target()))
                        .append(" AS T USING (VALUES (");
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(values.get(i));
                }
                sb.append(")) AS S(");
                appendQuotedCsv(sb, spec.insertColumns());
                sb.append(") ON ");
                for (int i = 0; i < spec.keyColumns().size(); i++) {
                    if (i > 0)
                        sb.append(" AND ");
                    sb.append("T.").append(quoteIdentifier(spec.keyColumns().get(i))).append(" = S.")
                            .append(quoteIdentifier(spec.keyColumns().get(i)));
                }
                if (!spec.updateColumns().isEmpty()) {
                    sb.append(" WHEN MATCHED THEN UPDATE SET ");
                    boolean first = true;
                    for (String c : spec.updateColumns()) {
                        if (!first)
                            sb.append(", ");
                        sb.append("T.").append(quoteIdentifier(c)).append(" = S.").append(quoteIdentifier(c));
                        first = false;
                    }
                }
                sb.append(" WHEN NOT MATCHED THEN INSERT (");
                appendQuotedCsv(sb, spec.insertColumns());
                sb.append(") VALUES (");
                for (int i = 0; i < spec.insertColumns().size(); i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append("S.").append(quoteIdentifier(spec.insertColumns().get(i)));
                }
                sb.append(");");
                return java.util.Optional.of(sb.toString());
            }
        };
        cachedMergeGenerator = local;
        return local;
    }

    /**
     * SQL Server: CTE {@code WITH …} but no {@code RECURSIVE} keyword
     * (auto-detected).
     */
    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.CteGenerator cteGenerator() {
        var local = cachedCteGenerator;
        if (local != null)
            return local;
        local = new org.eclipse.daanse.sql.dialect.api.generator.CteGenerator() {
            @Override
            public boolean emitsRecursiveKeyword() {
                return false;
            }
        };
        cachedCteGenerator = local;
        return local;
    }

    /** SQL Server rejects {@code CASCADE} on {@code DROP TABLE}. */
    @Override
    public boolean supportsDropTableCascade() {
        return false;
    }

    /** SQL Server rejects {@code IF NOT EXISTS} on {@code CREATE TABLE}. */
    @Override
    public boolean supportsCreateTableIfNotExists() {
        return false;
    }

    /** SQL Server rejects {@code IF NOT EXISTS} on {@code CREATE INDEX}. */
    @Override
    public boolean supportsCreateIndexIfNotExists() {
        return false;
    }

    /** SQL Server requires DROP+CREATE — no {@code OR REPLACE} on views. */
    @Override
    public boolean supportsCreateOrReplaceView() {
        return false;
    }

    /** SQL Server: {@code DROP INDEX name ON table_name}. */
    @Override
    public boolean dropIndexRequiresTable() {
        return true;
    }

    /**
     * SQL Server rejects {@code IF EXISTS} on
     * {@code ALTER TABLE … DROP CONSTRAINT}.
     */
    @Override
    public boolean supportsDropConstraintIfExists() {
        return false;
    }

    @Override
    public String createSchema(String schemaName, boolean ifNotExists) {
        if (!ifNotExists) {
            return "CREATE SCHEMA " + quoteIdentifier(schemaName);
        }
        // Single-line guarded form; works in any batch with QUOTED_IDENTIFIER on.
        return "IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = '" + schemaName.replace("'", "''")
                + "') EXEC('CREATE SCHEMA " + quoteIdentifier(schemaName) + "')";
    }

    @Override
    public String dropTable(String schemaName, String tableName, boolean ifExists) {
        if (!ifExists) {
            return "DROP TABLE " + quoteIdentifier(schemaName, tableName);
        }
        return "IF OBJECT_ID('" + (schemaName == null ? "" : schemaName + ".") + tableName
                + "', 'U') IS NOT NULL DROP TABLE " + quoteIdentifier(schemaName, tableName);
    }

    @Override
    public StringBuilder generateInline(List<String> columnNames, List<String> columnTypes, List<String[]> valueList) {
        return generateInlineGeneric(columnNames, columnTypes, valueList, null, false);
    }

    @Override
    public boolean requiresAliasForFromQuery() {
        return true;
    }

    @Override
    public boolean requiresUnionOrderByOrdinal() {
        return false;
    }

    @Override
    public void quoteBooleanLiteral(StringBuilder buf, String value) {
        // avoid padding origin values with blanks to n for char(n),
        // when ANSI_PADDING=ON
        String boolLiteral = value.trim();
        if (!boolLiteral.equalsIgnoreCase("TRUE") && !(boolLiteral.equalsIgnoreCase("FALSE"))
                && !(boolLiteral.equalsIgnoreCase("1")) && !(boolLiteral.equalsIgnoreCase("0"))) {
            throw new NumberFormatException("Illegal BOOLEAN literal:  " + value);
        }
        buf.append(DialectUtil.singleQuoteString(value));
    }

    @Override
    protected void quoteDateLiteral(StringBuilder buf, Date date) {
        buf.append("CONVERT(DATE, '");
        buf.append(df.format(date));
        // Format 112 is equivalent to "yyyyMMdd" in Java.
        // See http://msdn.microsoft.com/en-us/library/ms187928.aspx
        buf.append("', 112)");
    }

    @Override
    protected void quoteTimestampLiteral(StringBuilder buf, String value, Timestamp timestamp) {
        buf.append("CONVERT(datetime, '");
        buf.append(timestamp.toString());
        // Format 120 is equivalent to "yyyy-mm-dd hh:mm:ss" in Java.
        // See http://msdn.microsoft.com/en-us/library/ms187928.aspx
        buf.append("', 120)");
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding caseFolding() {
        return org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding.PRESERVE;
    }

    /**
     * T-SQL spells statement hints as a trailing query-option clause:
     * {@code OPTION (RECOMPILE, MAXDOP 2)} (leading space) — per hint the name followed by
     * its arguments separated by spaces ({@code name args...}), hints separated by commas.
     */
    @Override
    public StringBuilder statementOption(List<StatementHint> hints) {
        if (hints.isEmpty()) {
            return new StringBuilder();
        }
        StringBuilder sb = new StringBuilder(" OPTION (");
        boolean first = true;
        for (StatementHint hint : hints) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(hint.name());
            for (String argument : hint.arguments()) {
                sb.append(' ').append(argument);
            }
        }
        return sb.append(")");
    }

    @Override
    public StringBuilder generateKnownFunction(KnownFunction function, List<? extends CharSequence> arguments) {
        return switch (function) {
        case LENGTH -> {
            if (arguments.size() != 1) {
                throw new IllegalArgumentException("LENGTH expects 1 argument(s), got " + arguments.size());
            }
            yield new StringBuilder("LEN(").append(arguments.get(0)).append(")");
        }
        case CONCAT -> {
            // T-SQL + concatenates but coerces/NULL-propagates awkwardly; CONCAT is the idiom.
            if (arguments.size() < 2) {
                throw new IllegalArgumentException("CONCAT expects 2 or more argument(s), got " + arguments.size());
            }
            StringBuilder sb = new StringBuilder("CONCAT(");
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(arguments.get(i));
            }
            yield sb.append(")");
        }
        case INDEX_OF -> {
            if (arguments.size() != 2) {
                throw new IllegalArgumentException("INDEX_OF expects 2 argument(s), got " + arguments.size());
            }
            yield new StringBuilder("CHARINDEX(").append(arguments.get(0)).append(", ").append(arguments.get(1))
                    .append(")");
        }
        case YEAR, MONTH, DAY, HOUR, MINUTE, SECOND -> {
            if (arguments.size() != 1) {
                throw new IllegalArgumentException(
                        function.name() + " expects 1 argument(s), got " + arguments.size());
            }
            // T-SQL has no EXTRACT; DATEPART covers the whole family.
            yield new StringBuilder("DATEPART(").append(function.name().toLowerCase(java.util.Locale.ROOT))
                    .append(", ").append(arguments.get(0)).append(")");
        }
        case NOW -> {
            if (!arguments.isEmpty()) {
                throw new IllegalArgumentException("NOW expects 0 argument(s), got " + arguments.size());
            }
            // GETDATE() (datetime) chosen over SYSDATETIME() (datetime2): it is the
            // ubiquitous T-SQL spelling and its precision suffices for NOW's intent.
            yield new StringBuilder("GETDATE()");
        }
        default -> super.generateKnownFunction(function, arguments);
        };
    }

    @Override
    public java.util.Optional<String> generatePercentileDisc(double percentile, boolean desc, String tableName,
            String columnName) {
        return java.util.Optional
                .of((buildPercentileFunction("PERCENTILE_DISC", percentile, desc, tableName, columnName)).toString());
    }

    @Override
    public java.util.Optional<String> generatePercentileCont(double percentile, boolean desc, String tableName,
            String columnName) {
        return java.util.Optional
                .of((buildPercentileFunction("PERCENTILE_CONT", percentile, desc, tableName, columnName)).toString());
    }

    @Override
    public boolean supportsPercentileDisc() {
        return true;
    }

    @Override
    public boolean supportsPercentileCont() {
        return true;
    }

    @Override
    public java.util.Optional<String> generateListAgg(CharSequence operand, boolean distinct, String separator,
            String coalesce, String onOverflowTruncate, List<OrderedColumn> columns) {
        StringBuilder buf = new StringBuilder(64);
        buf.append("STRING_AGG");
        buf.append("( ");
        if (distinct) {
            buf.append("DISTINCT ");
        }
        buf.append(operand);
        buf.append(", '");
        if (separator != null) {
            buf.append(separator);
        } else {
            buf.append(", ");
        }
        buf.append("'");
        buf.append(")");
        if (columns != null && !columns.isEmpty()) {
            buf.append(" WITHIN GROUP (ORDER BY ");
            buf.append(buildOrderedColumnsClause(columns));
            buf.append(")");
        }
        // STRING_AGG(CONVERT (NVARCHAR (MAX), EmailAddress), ';') WITHIN GROUP (ORDER
        // BY EmailAddress ASC)
        return java.util.Optional.of((buf).toString());
    }

    @Override
    public java.util.Optional<String> generateNthValueAgg(CharSequence operand, boolean ignoreNulls, Integer n,
            List<OrderedColumn> columns) {
        return java.util.Optional
                .of((buildNthValueFunction("NTH_VALUE", operand, ignoreNulls, n, columns, true)).toString());
    }

    @Override
    public boolean supportsNthValue() {
        return true;
    }

    @Override
    public boolean supportsNthValueIgnoreNulls() {
        return true;
    }

    @Override
    public boolean supportsListAgg() {
        return true;
    }

    @Override
    public String createTrigger(String triggerName, org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming timing,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent event,
            org.eclipse.daanse.sql.model.schema.TableReference table,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerScope scope, String whenCondition, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank for SQL Server CREATE TRIGGER");
        }
        StringBuilder sb = new StringBuilder("CREATE TRIGGER ");
        sb.append(quoteIdentifier(triggerName));
        sb.append(" ON ").append(qualified(table));
        // SQL Server: AFTER (or FOR), INSTEAD OF — no BEFORE.
        if (timing == org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming.INSTEAD_OF) {
            sb.append(" INSTEAD OF ");
        } else {
            sb.append(" AFTER ");
        }
        sb.append(event);
        // Body should already include the {@code AS} keyword; if not, prepend.
        String trimmed = body.stripLeading();
        if (!trimmed.regionMatches(true, 0, "AS", 0, 2) && !trimmed.regionMatches(true, 0, "WITH", 0, 4)) {
            sb.append(" AS ");
        } else {
            sb.append(' ');
        }
        sb.append(body);
        return sb.toString();
    }

    // -------------------- Trigger procedures --------------------

    @Override
    public Optional<String> createTriggerProcedure(String procedureName, String schemaName, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank for SQL Server trigger procedure");
        }
        String qualified = schemaName != null && !schemaName.isBlank()
                ? quoteIdentifier(schemaName, procedureName).toString()
                : quoteIdentifier(procedureName).toString();
        return Optional.of("CREATE OR ALTER PROCEDURE " + qualified + " AS " + body);
    }

    @Override
    public String createTriggerUsingProcedure(String triggerName, String schemaName,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming timing,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent event,
            org.eclipse.daanse.sql.model.schema.TableReference table,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerScope scope, String whenCondition,
            String procedureName) {
        String qualified = schemaName != null && !schemaName.isBlank()
                ? quoteIdentifier(schemaName, procedureName).toString()
                : quoteIdentifier(procedureName).toString();
        return createTrigger(triggerName, timing, event, table, scope, whenCondition, "AS EXEC " + qualified);
    }

    /** SQL Server: {@code DROP PROCEDURE [IF EXISTS] schema.procedureName}. */
    @Override
    public Optional<String> dropProcedure(String procedureName, String schemaName, boolean ifExists) {
        String qualified = schemaName != null && !schemaName.isBlank()
                ? quoteIdentifier(schemaName, procedureName).toString()
                : quoteIdentifier(procedureName).toString();
        return Optional.of("DROP PROCEDURE " + (ifExists ? "IF EXISTS " : "") + qualified);
    }

    // -------------------- DDL — ALTER / RENAME (SQL-Server-specific)
    // --------------------

    @Override
    public String alterTableAddColumn(TableReference table, ColumnDefinition column) {
        StringBuilder sb = new StringBuilder("ALTER TABLE ");
        sb.append(qualified(table));
        sb.append(" ADD ");
        sb.append(quoteIdentifier(column.column().name()));
        sb.append(' ').append(nativeType(column.columnMetaData()));
        if (column.columnMetaData().nullability() == ColumnMetaData.Nullability.NO_NULLS) {
            sb.append(" NOT NULL");
        }
        column.columnMetaData().columnDefault().ifPresent(d -> sb.append(" DEFAULT ").append(d));
        return sb.toString();
    }

    /**
     * SQL Server: {@code ALTER TABLE x ALTER COLUMN c <type>} (no {@code TYPE}
     * keyword).
     */
    @Override
    public String alterColumnType(TableReference table, String columnName, ColumnMetaData newMeta) {
        if (newMeta == null) {
            throw new IllegalArgumentException("newMeta must not be null for ALTER COLUMN");
        }
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" ALTER COLUMN ")
                .append(quoteIdentifier(columnName)).append(' ').append(nativeType(newMeta)).toString();
    }

    @Override
    public String alterColumnSetNullability(TableReference table, String columnName, boolean nullable) {
        throw new UnsupportedOperationException(
                "SQL Server requires the column type when altering nullability — use the "
                        + "alterColumnSetNullability(table, column, nullable, currentMeta) overload.");
    }

    @Override
    public String alterColumnSetNullability(TableReference table, String columnName, boolean nullable,
            ColumnMetaData currentMeta) {
        if (currentMeta == null) {
            throw new IllegalArgumentException("currentMeta must not be null for SQL Server ALTER COLUMN");
        }
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" ALTER COLUMN ")
                .append(quoteIdentifier(columnName)).append(' ').append(nativeType(currentMeta))
                .append(nullable ? " NULL" : " NOT NULL").toString();
    }

    @Override
    public String alterColumnSetDefault(TableReference table, String columnName, String defaultExpression) {
        if (defaultExpression == null || defaultExpression.isBlank()) {
            throw new IllegalArgumentException("defaultExpression must not be blank for SET DEFAULT");
        }
        String dfName = "DF_" + table.name() + "_" + columnName;
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" ADD CONSTRAINT ")
                .append(quoteIdentifier(dfName)).append(" DEFAULT (").append(defaultExpression).append(") FOR ")
                .append(quoteIdentifier(columnName)).toString();
    }

    /**
     * Drops the assumed {@code DF_
     *
    <table>
     * _<col>} default-constraint produced by {@link #alterColumnSetDefault}.
     */
    @Override
    public String alterColumnDropDefault(TableReference table, String columnName) {
        String dfName = "DF_" + table.name() + "_" + columnName;
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" DROP CONSTRAINT ")
                .append(quoteIdentifier(dfName)).toString();
    }

    @Override
    public String renameColumn(TableReference table, String oldName, String newName) {
        return spRename(qualified(table) + "." + quoteIdentifier(oldName), newName, "COLUMN");
    }

    /** SQL Server: {@code EXEC sp_rename 'schema.table', 'new'}. */
    @Override
    public String renameTable(TableReference table, String newName) {
        return spRename(qualified(table), newName, null);
    }

    /** SQL Server: {@code EXEC sp_rename 'schema.table.idx', 'new', 'INDEX'}. */
    @Override
    public String renameIndex(String oldName, String newName, TableReference table) {
        if (table == null) {
            throw new IllegalArgumentException("table must not be null for SQL Server sp_rename INDEX");
        }
        return spRename(qualified(table) + "." + quoteIdentifier(oldName), newName, "INDEX");
    }

    /**
     * SQL Server:
     * {@code EXEC sp_rename 'schema.table.constraint', 'new', 'OBJECT'}.
     */
    @Override
    public String renameConstraint(TableReference table, String oldName, String newName) {
        return spRename(qualified(table) + "." + quoteIdentifier(oldName), newName, "OBJECT");
    }

    /**
     * @param objectType the sp_rename type ({@code COLUMN}/{@code INDEX}/
     *                   {@code OBJECT}), or {@code null} for the table-rename
     *                   overload (which omits the third argument entirely)
     */
    private String spRename(String quotedSourcePath, String newName, String objectType) {
        StringBuilder sb = new StringBuilder("EXEC sp_rename '");
        sb.append(quotedSourcePath);
        sb.append("', '").append(newName).append("'");
        if (objectType != null)
            sb.append(", '").append(objectType).append("'");
        return sb.toString();
    }
}
