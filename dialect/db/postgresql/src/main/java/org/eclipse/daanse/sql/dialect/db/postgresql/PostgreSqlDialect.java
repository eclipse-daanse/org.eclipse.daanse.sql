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

package org.eclipse.daanse.sql.dialect.db.postgresql;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming;
import org.eclipse.daanse.sql.model.sql.BitOperation;
import org.eclipse.daanse.sql.model.sql.OrderedColumn;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;
import org.eclipse.daanse.sql.dialect.db.common.DialectUtil;

/**
 * @author jhyde
 * @since Nov 23, 2008
 */
public class PostgreSqlDialect extends AbstractJdbcDialect {

    // Lazy-initialized generator caches — built on first call. Stateless
    // implementations that capture the dialect's quoter / version, so safe
    // to memoize for the dialect's lifetime.
    private volatile org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator cachedPaginationGenerator;
    private volatile org.eclipse.daanse.sql.dialect.api.generator.ReturningGenerator cachedReturningGenerator;
    private volatile org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator cachedMergeGenerator;

    /** JDBC-free constructor for SQL generation. */
    public PostgreSqlDialect() {
        super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults());
    }

    /** Construct from a captured snapshot — preferred path. */
    public PostgreSqlDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    @Override
    public boolean requiresAliasForFromQuery() {
        return true;
    }

    @Override
    public boolean supportsCreateOrReplaceTrigger() {
        if (org.eclipse.daanse.sql.dialect.api.DialectVersion.UNKNOWN.equals(dialectVersion)) {
            return true;
        }
        return dialectVersion.atLeast(14, 0);
    }

    /** PostgreSQL: {@code LIMIT n OFFSET m} — both clauses optional. */
    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator paginationGenerator() {
        var local = cachedPaginationGenerator;
        if (local != null)
            return local;
        local = new org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator() {
            @Override
            public String paginate(java.util.OptionalLong limit, java.util.OptionalLong offset) {
                StringBuilder sb = new StringBuilder();
                limit.ifPresent(l -> {
                    if (l < 0)
                        throw new IllegalArgumentException("limit must be >= 0");
                    sb.append(" LIMIT ").append(l);
                });
                offset.ifPresent(o -> {
                    if (o < 0)
                        throw new IllegalArgumentException("offset must be >= 0");
                    sb.append(" OFFSET ").append(o);
                });
                return sb.toString();
            }
        };
        cachedPaginationGenerator = local;
        return local;
    }

    /** PostgreSQL: {@code RETURNING col, col, ...} — supported since 8.2. */
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
                    return java.util.Optional.of(" RETURNING *");
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
    public org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator mergeGenerator() {
        var local = cachedMergeGenerator;
        if (local != null)
            return local;
        if (!dialectVersion.isUnknownOrAtLeast(9, 5)) {
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
                StringBuilder sb = new StringBuilder("INSERT INTO ").append(qualified(spec.target())).append(" (");
                appendQuotedCsv(sb, spec.insertColumns());
                sb.append(") VALUES (");
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(values.get(i));
                }
                sb.append(") ON CONFLICT (");
                appendQuotedCsv(sb, spec.keyColumns());
                sb.append(") ");
                if (spec.updateColumns().isEmpty()) {
                    sb.append("DO NOTHING");
                } else {
                    sb.append("DO UPDATE SET ");
                    boolean first = true;
                    for (String c : spec.updateColumns()) {
                        if (!first)
                            sb.append(", ");
                        sb.append(quoteIdentifier(c)).append(" = EXCLUDED.").append(quoteIdentifier(c));
                        first = false;
                    }
                }
                return java.util.Optional.of(sb.toString());
            }
        };
        cachedMergeGenerator = local;
        return local;
    }

    /**
     * PostgreSQL:
     * {@code CREATE OR REPLACE FUNCTION schema.name() RETURNS trigger LANGUAGE plpgsql AS $$ body $$}.
     */
    @Override
    public Optional<String> createTriggerProcedure(String procedureName, String schemaName, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank for PG trigger procedure");
        }
        String qualified = schemaName != null && !schemaName.isBlank()
                ? quoteIdentifier(schemaName, procedureName).toString()
                : quoteIdentifier(procedureName).toString();
        return Optional.of("CREATE OR REPLACE FUNCTION " + qualified + "() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + body + " $$");
    }

    /**
     * PostgreSQL: {@code CREATE TRIGGER … EXECUTE FUNCTION schema.procedureName()}.
     */
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
        return createTrigger(triggerName, timing, event, table, scope, whenCondition,
                "EXECUTE FUNCTION " + qualified + "()");
    }

    /** PostgreSQL: {@code DROP FUNCTION [IF EXISTS] schema.procedureName()}. */
    @Override
    public Optional<String> dropProcedure(String procedureName, String schemaName, boolean ifExists) {
        String qualified = schemaName != null && !schemaName.isBlank()
                ? quoteIdentifier(schemaName, procedureName).toString()
                : quoteIdentifier(procedureName).toString();
        return Optional.of("DROP FUNCTION " + (ifExists ? "IF EXISTS " : "") + qualified + "()");
    }

    @Override
    public java.util.List<String> dropTriggerOnTable(String triggerName,
            org.eclipse.daanse.sql.model.schema.TableReference table, boolean ifExists) {
        StringBuilder sb = new StringBuilder("DROP TRIGGER ");
        if (ifExists)
            sb.append("IF EXISTS ");
        sb.append(quoteIdentifier(triggerName));
        if (table != null)
            sb.append(" ON ").append(qualified(table));
        return java.util.List.of(sb.toString());
    }

    @Override
    public StringBuilder generateOrderByNulls(CharSequence expr, boolean ascending, boolean collateNullsLast) {
        // Support for "ORDER BY ... NULLS LAST" was introduced in Postgres 8.3.
        if (productVersion.compareTo("8.3") >= 0) {
            return generateOrderByNullsAnsi(expr, ascending, collateNullsLast);
        } else {
            return super.generateOrderByNulls(expr, ascending, collateNullsLast);
        }
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
        javaRegex = javaRegex.replace("\\Q", "");
        javaRegex = javaRegex.replace("\\E", "");
        final StringBuilder sb = new StringBuilder();
        sb.append("cast(");
        sb.append(source);
        sb.append(" as text) is not null and ");
        sb.append("cast(");
        sb.append(source);
        sb.append(" as text) ~ ");
        quoteStringLiteral(sb, javaRegex);
        return Optional.of(sb.toString());
    }

    @Override
    public BestFitColumnType getType(ResultSetMetaData metaData, int columnIndex) throws SQLException {
        final int precision = metaData.getPrecision(columnIndex + 1);
        final int scale = metaData.getScale(columnIndex + 1);
        final int columnType = metaData.getColumnType(columnIndex + 1);
        final String columnName = metaData.getColumnName(columnIndex + 1);

        // Check for column names starting with "m" (measure columns like "m0", "m1"):
        // In GROUPING SETS queries, Greenplum/PostgreSQL may loosen type metadata for
        // measure
        // columns. Using OBJECT avoids data loss when converting measure values. This
        // pattern
        // matches the handling in OracleDialect.getNumericDecimalType() for similar
        // cases.
        // The "m" prefix check identifies measure columns in aggregation queries.
        if (columnType == Types.NUMERIC && scale == 0 && precision == 0 && columnName.startsWith("m")) {
            // In Greenplum/PostgreSQL, NUMERIC with no precision or scale means floating point.
            // DOUBLE (not OBJECT): the historical data-loss concern was INT truncation of measure
            // values; OBJECT let PostgreSQL BigDecimals through to the cell layer, where exact
            // half-way decimal sums format differently than the double-summed values every other
            // dialect produces (mondrian's GreenplumDialect used DOUBLE here as well).
            logTypeInfo(metaData, columnIndex, BestFitColumnType.DOUBLE);
            return BestFitColumnType.DOUBLE;
        }
        return super.getType(metaData, columnIndex);
    }

    @Override
    public String name() {
        return "postgres";
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding caseFolding() {
        return org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding.LOWER;
    }

    @Override
    protected boolean supportsNullsOrdering() {
        // Support for "ORDER BY ... NULLS LAST" was introduced in Postgres 8.3.
        return productVersion != null && productVersion.compareTo("8.3") >= 0;
    }

    // Unified BitOperation methods

    @Override
    public java.util.Optional<String> generateBitAggregation(BitOperation operation, CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        StringBuilder result = switch (operation) {
        case AND -> buf.append("bit_and(").append(operand).append(")");
        case OR -> buf.append("bit_or(").append(operand).append(")");
        case XOR -> buf.append("bit_xor(").append(operand).append(")");
        case NAND -> buf.append("NOT(bit_and(").append(operand).append("))");
        case NOR -> buf.append("NOT(bit_or(").append(operand).append("))");
        case NXOR -> buf.append("NOT(bit_xor(").append(operand).append("))");
        };
        return java.util.Optional.of(result.toString());
    }

    @Override
    public boolean supportsBitAggregation(BitOperation operation) {
        return true; // PostgreSQL supports all bit operations
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
    public java.util.Optional<String> generateNthValueAgg(CharSequence operand, boolean ignoreNulls, Integer n,
            List<OrderedColumn> columns) {
        return java.util.Optional
                .of((buildNthValueFunction("NTH_VALUE", operand, ignoreNulls, n, columns, false)).toString());
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
    public boolean supportsNthValue() {
        return true;
    }
}
