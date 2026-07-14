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
package org.eclipse.daanse.sql.dialect.db.oracle;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming;
import org.eclipse.daanse.sql.model.sql.BitOperation;
import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.eclipse.daanse.sql.dialect.api.generator.StatementHint;
import org.eclipse.daanse.sql.model.sql.OrderedColumn;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;
import org.eclipse.daanse.sql.dialect.db.common.DialectUtil;

/**
 * @author jhyde
 * @since Nov 23, 2008
 */
public class OracleDialect extends AbstractJdbcDialect {

    private static final String SUPPORTED_PRODUCT_NAME = "ORACLE";

    private volatile org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator cachedMergeGenerator;
    private volatile org.eclipse.daanse.sql.dialect.api.generator.ReturningGenerator cachedReturningGenerator;

    /** JDBC-free constructor for SQL generation. */
    public OracleDialect() {
        super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults());
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public OracleDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    /**
     * Oracle 9i+: SQL-2003
     * {@code MERGE INTO target USING ... ON ... WHEN MATCHED ... WHEN NOT MATCHED ...}.
     */
    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator mergeGenerator() {
        var local = cachedMergeGenerator;
        if (local != null)
            return local;
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
                        .append(" T USING (SELECT ");
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(values.get(i)).append(" AS ").append(quoteIdentifier(spec.insertColumns().get(i)));
                }
                sb.append(" FROM dual) S ON (");
                for (int i = 0; i < spec.keyColumns().size(); i++) {
                    if (i > 0)
                        sb.append(" AND ");
                    sb.append("T.").append(quoteIdentifier(spec.keyColumns().get(i))).append(" = S.")
                            .append(quoteIdentifier(spec.keyColumns().get(i)));
                }
                sb.append(")");
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
                sb.append(")");
                return java.util.Optional.of(sb.toString());
            }
        };
        cachedMergeGenerator = local;
        return local;
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.ReturningGenerator returningGenerator() {
        var local = cachedReturningGenerator;
        if (local != null)
            return local;
        if (!dialectVersion.isUnknownOrAtLeast(23, 0)) {
            local = super.returningGenerator();
            cachedReturningGenerator = local;
            return local;
        }
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
                    // Oracle 23c does NOT support RETURNING * — only explicit columns.
                    return java.util.Optional.empty();
                }
                StringBuilder sb = new StringBuilder(" RETURNING ");
                boolean first = true;
                for (String c : columns) {
                    if (!first)
                        sb.append(", ");
                    sb.append(quoteIdentifier(c));
                    first = false;
                }
                return java.util.Optional.of(sb.toString());
            }
        };
        cachedReturningGenerator = local;
        return local;
    }

    @Override
    public boolean supportsDropTableCascade() {
        return false;
    }

    /** Oracle: no {@code IF NOT EXISTS} on any DDL. */
    @Override
    public boolean supportsCreateTableIfNotExists() {
        return false;
    }

    @Override
    public boolean supportsCreateIndexIfNotExists() {
        return false;
    }

    @Override
    public boolean supportsDropIndexIfExists() {
        return false;
    }

    @Override
    public boolean supportsDropViewIfExists() {
        return false;
    }

    @Override
    public boolean supportsDropConstraintIfExists() {
        return false;
    }

    /** Oracle 11g+ supports {@code CREATE OR REPLACE TRIGGER}. */
    @Override
    public boolean supportsCreateOrReplaceTrigger() {
        return true;
    }

    @Override
    public boolean supportsDropTableIfExists() {
        return false;
    }

    @Override
    public boolean allowsFromAlias() {
        return false;
    }

    @Override
    public StringBuilder generateInline(List<String> columnNames, List<String> columnTypes, List<String[]> valueList) {
        return generateInlineGeneric(columnNames, columnTypes, valueList, " from dual", false);
    }

    @Override
    public boolean supportsGroupingSets() {
        return true;
    }

    @Override
    public StringBuilder generateOrderByNulls(CharSequence expr, boolean ascending, boolean collateNullsLast) {
        return generateOrderByNullsAnsi(expr, ascending, collateNullsLast);
    }

    @Override
    public boolean allowsJoinOn() {
        return false;
    }

    @Override
    public boolean allowsRegularExpressionInWhereClause() {
        return true;
    }

    @Override
    public Optional<String> generateRegularExpression(String source, String javaRegex) {
        try {
            Pattern.compile(javaRegex);
        } catch (PatternSyntaxException e) {
            // Not a valid Java regex. Too risky to continue.
            return Optional.empty();
        }
        javaRegex = DialectUtil.cleanUnicodeAwareCaseFlag(javaRegex);
        StringBuilder mappedFlags = new StringBuilder();
        String[][] mapping = new String[][] { { "c", "c" }, { "i", "i" }, { "m", "m" } };
        javaRegex = extractEmbeddedFlags(javaRegex, mapping, mappedFlags);

        final Matcher escapeMatcher = DialectUtil.ESCAPE_PATTERN.matcher(javaRegex);
        while (escapeMatcher.find()) {
            javaRegex = javaRegex.replace(escapeMatcher.group(1), escapeMatcher.group(2));
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(source);
        sb.append(" IS NOT NULL AND ");
        sb.append("REGEXP_LIKE(");
        sb.append(source);
        sb.append(", ");
        quoteStringLiteral(sb, javaRegex);
        sb.append(", ");
        quoteStringLiteral(sb, mappedFlags.toString());
        sb.append(")");
        return Optional.of(sb.toString());
    }

    /**
     * @param metaData    Resultset metadata
     * @param columnIndex index of the column in the result set
     * @return For Types.NUMERIC and Types.DECIMAL, getType() will return a
     *         Type.INT, Type.DOUBLE, or Type.OBJECT based on scale, precision, and
     *         column name.
     * @throws SQLException
     */
    @Override
    public BestFitColumnType getType(ResultSetMetaData metaData, int columnIndex) throws SQLException {
        final int columnType = metaData.getColumnType(columnIndex + 1);
        final int precision = metaData.getPrecision(columnIndex + 1);
        final int scale = metaData.getScale(columnIndex + 1);
        final String columnName = metaData.getColumnName(columnIndex + 1);
        BestFitColumnType type;

        if (columnType == Types.NUMERIC || columnType == Types.DECIMAL) {
            type = getNumericDecimalType(columnType, precision, scale, columnName);
        } else {
            type = super.getType(metaData, columnIndex);
        }
        logTypeInfo(metaData, columnIndex, type);
        return type;
    }

    private BestFitColumnType getNumericDecimalType(final int columnType, final int precision, final int scale,
            final String columnName) {
        if (scale == -127 && precision != 0) {
            // non zero precision w/ -127 scale means float in Oracle.
            return BestFitColumnType.DOUBLE;
        } else if (columnType == Types.NUMERIC && (scale == 0 || scale == -127) && precision == 0
                && columnName.startsWith("m")) {
            // In GROUPING SETS queries, Oracle
            // loosens the type of columns compared to mere GROUP BY
            // queries. We need integer GROUP BY columns to remain integers,
            // otherwise the segments won't be found; but if we convert
            // measure (whose column names are like "m0", "m1") to integers,
            // data loss will occur. DOUBLE (not OBJECT) avoids the integer
            // truncation while keeping measure values on the same double
            // path every other dialect uses (OBJECT let Oracle BigDecimals
            // through to the cell layer, where exact half-way decimal sums
            // format differently than double-summed values).
            return BestFitColumnType.DOUBLE;
        } else if (scale == -127 && precision == 0) {
            return BestFitColumnType.INT;
        } else if (scale == 0 && (precision == 38 || precision == 0)) {
            // NUMBER(38, 0) is conventionally used in
            // Oracle for integers of unspecified precision, so let's be
            // bold and assume that they can fit into an int.
            return BestFitColumnType.INT;
        } else if (scale == 0 && precision <= 9) {
            // An int (up to 2^31 = 2.1B) can hold any NUMBER(10, 0) value
            // (up to 10^9 = 1B).
            return BestFitColumnType.INT;
        } else {
            return BestFitColumnType.DOUBLE;
        }
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }

    @Override
    protected boolean supportsNullsOrdering() {
        return true; // Oracle supports NULLS FIRST/LAST
    }

    /**
     * Oracle spells statement hints as an optimizer block directly after the {@code SELECT}
     * keyword: {@code /*+ name(arg1, arg2) name2 *}{@code /} (trailing space). Hints are
     * joined by a space; a hint without arguments is the bare name. Any {@code *}{@code /}
     * inside a name or argument is stripped so the block cannot terminate early.
     */
    @Override
    public StringBuilder selectHint(List<StatementHint> hints) {
        if (hints.isEmpty()) {
            return new StringBuilder();
        }
        StringBuilder sb = new StringBuilder("/*+ ");
        boolean first = true;
        for (StatementHint hint : hints) {
            if (!first) {
                sb.append(' ');
            }
            first = false;
            sb.append(stripCommentEnd(hint.name()));
            if (!hint.arguments().isEmpty()) {
                sb.append('(');
                for (int i = 0; i < hint.arguments().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(stripCommentEnd(hint.arguments().get(i)));
                }
                sb.append(')');
            }
        }
        return sb.append(" */ ");
    }

    /** Neutralizes a premature comment terminator inside a hint name/argument. */
    private static String stripCommentEnd(String s) {
        return s.replace("*/", "");
    }

    // Unified BitOperation methods

    @Override
    public StringBuilder generateKnownFunction(KnownFunction function, List<? extends CharSequence> arguments) {
        return switch (function) {
        case SUBSTRING -> {
            if (arguments.size() < 2 || arguments.size() > 3) {
                throw new IllegalArgumentException("SUBSTRING expects 2..3 argument(s), got " + arguments.size());
            }
            StringBuilder sb = new StringBuilder("SUBSTR(").append(arguments.get(0)).append(", ")
                    .append(arguments.get(1));
            if (arguments.size() == 3) {
                sb.append(", ").append(arguments.get(2));
            }
            yield sb.append(")");
        }
        case INDEX_OF -> {
            if (arguments.size() != 2) {
                throw new IllegalArgumentException("INDEX_OF expects 2 argument(s), got " + arguments.size());
            }
            // Oracle INSTR takes (haystack, needle) - swapped vs INDEX_OF(needle, haystack).
            yield new StringBuilder("INSTR(").append(arguments.get(1)).append(", ").append(arguments.get(0))
                    .append(")");
        }
        default -> super.generateKnownFunction(function, arguments);
        };
    }

    @Override
    public java.util.Optional<String> generateBitAggregation(BitOperation operation, CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        StringBuilder result = switch (operation) {
        case AND -> buf.append("BIT_AND_AGG(").append(operand).append(")");
        case OR -> buf.append("BIT_OR_AGG(").append(operand).append(")");
        case XOR -> buf.append("BIT_XOR(").append(operand).append(")");
        case NAND, NOR, NXOR ->
            throw new UnsupportedOperationException("Oracle does not support " + operation + " bit aggregation");
        };
        return java.util.Optional.of(result.toString());
    }

    @Override
    public boolean supportsBitAggregation(BitOperation operation) {
        return switch (operation) {
        case AND, OR, XOR -> true;
        case NAND, NOR, NXOR -> false;
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
        buf.append("LISTAGG");
        buf.append("( ");
        if (distinct) {
            buf.append("DISTINCT ");
        }
        if (coalesce != null) {
            buf.append("COALESCE(").append(operand).append(", '").append(coalesce).append("')");
        } else {
            buf.append(operand);
        }
        buf.append(", '");
        if (separator != null) {
            buf.append(separator);
        } else {
            buf.append(", ");
        }
        buf.append("'");
        if (onOverflowTruncate != null) {
            buf.append(" ON OVERFLOW TRUNCATE '").append(onOverflowTruncate).append("' WITHOUT COUNT)");
        } else {
            buf.append(")");
        }
        if (columns != null && !columns.isEmpty()) {
            buf.append(" WITHIN GROUP (ORDER BY ");
            buf.append(buildOrderedColumnsClause(columns));
            buf.append(")");
        }
        // LISTAGG(NAME, ', ') WITHIN GROUP (ORDER BY ID)
        // LISTAGG(COALESCE(NAME, 'null'), ', ') WITHIN GROUP (ORDER BY ID)
        // LISTAGG(ID, ', ') WITHIN GROUP (ORDER BY ID) OVER (ORDER BY ID)
        // LISTAGG(ID, ';' ON OVERFLOW TRUNCATE 'etc' WITHOUT COUNT) WITHIN GROUP (ORDER
        // BY ID)
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

    // -------------------- Trigger procedures --------------------

    @Override
    public Optional<String> createTriggerProcedure(String procedureName, String schemaName, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank for Oracle trigger procedure");
        }
        String qualified = schemaName != null && !schemaName.isBlank()
                ? quoteIdentifier(schemaName, procedureName).toString()
                : quoteIdentifier(procedureName).toString();
        return Optional.of("CREATE OR REPLACE PROCEDURE " + qualified + " AS " + body);
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
        return createTrigger(triggerName, timing, event, table, scope, whenCondition, "BEGIN " + qualified + "; END;");
    }

    /** Oracle: {@code DROP PROCEDURE schema.procedureName}. */
    @Override
    public Optional<String> dropProcedure(String procedureName, String schemaName, boolean ifExists) {
        String qualified = schemaName != null && !schemaName.isBlank()
                ? quoteIdentifier(schemaName, procedureName).toString()
                : quoteIdentifier(procedureName).toString();
        // Oracle has no IF EXISTS on DROP PROCEDURE. Callers are expected to
        // handle "doesn't exist" themselves; keep ifExists for parity.
        return Optional.of("DROP PROCEDURE " + qualified);
    }

    @Override
    public String addForeignKeyConstraint(TableReference table, String constraintName, java.util.List<String> fkColumns,
            TableReference referencedTable, java.util.List<String> referencedColumns, String onDelete,
            String onUpdate) {
        StringBuilder sb = new StringBuilder("ALTER TABLE ");
        sb.append(qualified(table));
        sb.append(" ADD CONSTRAINT ").append(quoteIdentifier(constraintName));
        sb.append(" FOREIGN KEY (");
        appendQuotedCsv(sb, fkColumns);
        sb.append(") REFERENCES ").append(qualified(referencedTable)).append(" (");
        appendQuotedCsv(sb, referencedColumns);
        sb.append(")");
        if (onDelete != null && !onDelete.isBlank())
            sb.append(" ON DELETE ").append(onDelete);
        // Oracle: no ON UPDATE clause — silently drop {@code onUpdate}.
        return sb.toString();
    }

    // -------------------- DDL — ALTER (Oracle: MODIFY syntax) --------------------

    /**
     * Oracle: {@code ALTER TABLE x ADD c <type> [NOT NULL] [DEFAULT …]} — no
     * {@code COLUMN} keyword.
     */
    @Override
    public String alterTableAddColumn(TableReference table,
            org.eclipse.daanse.sql.model.schema.ColumnDefinition column) {
        StringBuilder sb = new StringBuilder("ALTER TABLE ");
        sb.append(qualified(table));
        sb.append(" ADD ");
        sb.append(quoteIdentifier(column.column().name()));
        sb.append(' ').append(nativeType(column.columnMetaData()));
        column.columnMetaData().columnDefault().ifPresent(d -> sb.append(" DEFAULT ").append(d));
        if (column.columnMetaData().nullability() == ColumnMetaData.Nullability.NO_NULLS) {
            sb.append(" NOT NULL");
        }
        return sb.toString();
    }

    /** Oracle: {@code ALTER TABLE x MODIFY (c <type>)}. */
    @Override
    public String alterColumnType(TableReference table, String columnName, ColumnMetaData newMeta) {
        if (newMeta == null) {
            throw new IllegalArgumentException("newMeta must not be null for ALTER COLUMN");
        }
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" MODIFY (")
                .append(quoteIdentifier(columnName)).append(' ').append(nativeType(newMeta)).append(")").toString();
    }

    /** Oracle: {@code ALTER TABLE x MODIFY (c [NULL | NOT NULL])}. */
    @Override
    public String alterColumnSetNullability(TableReference table, String columnName, boolean nullable) {
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" MODIFY (")
                .append(quoteIdentifier(columnName)).append(nullable ? " NULL" : " NOT NULL").append(")").toString();
    }

    /** Oracle: {@code ALTER TABLE x MODIFY (c DEFAULT <expr>)}. */
    @Override
    public String alterColumnSetDefault(TableReference table, String columnName, String defaultExpression) {
        if (defaultExpression == null || defaultExpression.isBlank()) {
            throw new IllegalArgumentException("defaultExpression must not be blank for SET DEFAULT");
        }
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" MODIFY (")
                .append(quoteIdentifier(columnName)).append(" DEFAULT ").append(defaultExpression).append(")")
                .toString();
    }

    /** Oracle: {@code MODIFY (c DEFAULT NULL)} clears the default. */
    @Override
    public String alterColumnDropDefault(TableReference table, String columnName) {
        return new StringBuilder("ALTER TABLE ").append(qualified(table)).append(" MODIFY (")
                .append(quoteIdentifier(columnName)).append(" DEFAULT NULL)").toString();
    }

    // RENAME COLUMN/TABLE/INDEX/CONSTRAINT inherit the SQL-99 default.
}
