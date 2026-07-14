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
package org.eclipse.daanse.sql.dialect.db.sqlite;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;

public class SqliteDialect extends AbstractJdbcDialect {

    private static final String SUPPORTED_PRODUCT_NAME = "SQLITE";

    private volatile org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator cachedPaginationGenerator;
    private volatile org.eclipse.daanse.sql.dialect.api.generator.ReturningGenerator cachedReturningGenerator;
    private volatile org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator cachedMergeGenerator;

    /** JDBC-free constructor for SQL generation. */
    public SqliteDialect() {
        super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults());
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public SqliteDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    @Override
    public boolean supportsSequences() {
        return false;
    }

    @Override
    public boolean supportsDropConstraintIfExists() {
        // SQLite can't drop constraints at all — fall back to drop+recreate.
        return false;
    }

    @Override
    public boolean supportsCreateOrReplaceView() {
        return false;
    }

    @Override
    public boolean supportsDropTableCascade() {
        return false;
    }

    /**
     * SQLite does not support the ANSI derived-column-list aliasing
     * {@code (VALUES ...) AS t (c1, c2)} ("near \"(\": syntax error"); inline
     * datasets render as the generic {@code select ... union all select ...}
     * form instead (SQLite allows SELECT without FROM).
     */
    @Override
    public StringBuilder generateInline(java.util.List<String> columnNames, java.util.List<String> columnTypes,
            java.util.List<String[]> valueList) {
        return generateInlineGeneric(columnNames, columnTypes, valueList, null, false);
    }

    @Override
    public StringBuilder generateOrderByNulls(CharSequence expr, boolean ascending, boolean collateNullsLast) {
        return generateOrderByNullsAnsi(expr, ascending, collateNullsLast);
    }

    /**
     * SQLite has no static column types: for expression columns (no source table,
     * e.g. {@code sum(x)}) the xerial driver derives the reported JDBC type from
     * the CURRENT ROW's storage class. A {@code sum()} over a NUMERIC-affinity
     * column therefore reports {@code INTEGER} whenever the first row's total
     * happens to be integral, an INT/LONG accessor is chosen once for the whole
     * result, and every later REAL row is silently truncated by {@code getInt()}
     * (e.g. a closure-table salary rollup losing exactly the sum of all
     * fractional parts). For numeric expression columns use the OBJECT accessor,
     * which reads each row with its own storage class. Plain table columns keep
     * the default mapping — their metadata comes from the declared type and is
     * row-independent.
     */
    @Override
    public BestFitColumnType getType(ResultSetMetaData metaData, int columnIndex) throws SQLException {
        final int columnType = metaData.getColumnType(columnIndex + 1);
        switch (columnType) {
        case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.NUMERIC, Types.DECIMAL, Types.REAL,
                Types.FLOAT, Types.DOUBLE: {
            final String table = metaData.getTableName(columnIndex + 1);
            if (table == null || table.isEmpty()) {
                logTypeInfo(metaData, columnIndex, BestFitColumnType.OBJECT);
                return BestFitColumnType.OBJECT;
            }
            break;
        }
        default:
            break;
        }
        return super.getType(metaData, columnIndex);
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding caseFolding() {
        return org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding.PRESERVE;
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator paginationGenerator() {
        var local = cachedPaginationGenerator;
        if (local != null)
            return local;
        local = new org.eclipse.daanse.sql.dialect.api.generator.PaginationGenerator() {
            @Override
            public String paginate(java.util.OptionalLong limit, java.util.OptionalLong offset) {
                if (limit.isEmpty() && offset.isEmpty())
                    return "";
                StringBuilder sb = new StringBuilder();
                if (limit.isPresent()) {
                    long l = limit.getAsLong();
                    if (l < 0)
                        throw new IllegalArgumentException("limit must be >= 0");
                    sb.append(" LIMIT ").append(l);
                } else {
                    sb.append(" LIMIT -1");
                }
                if (offset.isPresent()) {
                    long o = offset.getAsLong();
                    if (o < 0)
                        throw new IllegalArgumentException("offset must be >= 0");
                    sb.append(" OFFSET ").append(o);
                }
                return sb.toString();
            }
        };
        cachedPaginationGenerator = local;
        return local;
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.ReturningGenerator returningGenerator() {
        var local = cachedReturningGenerator;
        if (local != null)
            return local;
        if (!dialectVersion.isUnknownOrAtLeast(3, 35)) {
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
        if (!dialectVersion.isUnknownOrAtLeast(3, 24)) {
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
                sb.append(") DO UPDATE SET ");
                for (int i = 0; i < spec.updateColumns().size(); i++) {
                    if (i > 0)
                        sb.append(", ");
                    String col = quoteIdentifier(spec.updateColumns().get(i)).toString();
                    sb.append(col).append(" = excluded.").append(col);
                }
                return java.util.Optional.of(sb.toString());
            }
        };
        cachedMergeGenerator = local;
        return local;
    }

}
