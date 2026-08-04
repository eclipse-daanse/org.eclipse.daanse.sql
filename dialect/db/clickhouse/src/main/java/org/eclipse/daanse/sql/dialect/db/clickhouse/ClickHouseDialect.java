/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * History:
 *  This files came from the mondrian project. Some of the Flies
 *  (mostly the Tests) did not have License Header.
 *  But the Project is EPL Header. 2002-2022 Hitachi Vantara.
 *
 * Contributors:
 *   Hitachi Vantara.
 *   SmartCity Jena - initial  Java 8, Junit5
 */
package org.eclipse.daanse.sql.dialect.db.clickhouse;

import java.util.List;

import org.eclipse.daanse.sql.model.sql.BitOperation;
import org.eclipse.daanse.sql.model.sql.OrderedColumn;
import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;

public class ClickHouseDialect extends AbstractJdbcDialect {

    /** ClickHouse rejects plain {@code CREATE INDEX} without a data-skipping {@code TYPE}
     *  clause (INCORRECT_QUERY 80) — b-tree index DDL is not expressible. */
    @Override
    public boolean supportsIndexDdl() {
        return false;
    }

    private static final String SUPPORTED_PRODUCT_NAME = "CLICKHOUSE";

    /**
     * {@code SMALLINT}.
     *
     * <p>
     * ClickHouse maps SMALLINT onto Int16 and reads it back as a number.
     */
    @Override
    public String booleanTypeName() {
        return "SMALLINT";
    }

    /**
     * No.
     *
     * <p>
     * ClickHouse refuses {@code setAutoCommit(false)} with a
     * {@code SQLFeatureNotSupportedException} — there is nothing to turn off,
     * every statement stands alone. A writer that wraps a table load in one
     * transaction cannot even begin.
     */
    @Override
    public boolean supportsTransactions() {
        return false;
    }

    /**
     * Wraps a nullable column's type in {@code Nullable(…)}.
     *
     * <p>
     * ClickHouse columns are not nullable by default and there is no
     * {@code NULL} modifier to add afterwards — nullability is part of the type
     * itself. A column declared {@code Int32} rejects every null; the same
     * column declared {@code Nullable(Int32)} accepts them.
     */
    @Override
    public String nativeType(org.eclipse.daanse.sql.model.schema.ColumnMetaData meta) {
        String type = super.nativeType(meta);
        if (meta.nullability() == org.eclipse.daanse.sql.model.schema.ColumnMetaData.Nullability.NULLABLE) {
            return "Nullable(" + type + ")";
        }
        return type;
    }

    /**
     * Appends {@code ENGINE = MergeTree() ORDER BY (…)}.
     *
     * <p>
     * ClickHouse refuses a table without one: <em>Code 42, ORDER BY or PRIMARY
     * KEY clause is missing</em>. MergeTree is the general-purpose engine, and
     * it sorts by the primary key where the schema declares one. Where it does
     * not, {@code ORDER BY tuple()} says "no ordering" — the legal way to
     * express what every other database means by a table without a key.
     *
     * <p>
     * The {@code NOT NULL} the base implementation appends is left out here:
     * nullability rides in the type (see {@link #nativeType}), and ClickHouse
     * rejects the suffix.
     */
    @Override
    public String createTable(org.eclipse.daanse.sql.model.schema.TableReference table,
            List<org.eclipse.daanse.sql.model.schema.ColumnDefinition> columns,
            org.eclipse.daanse.sql.model.schema.PrimaryKey primaryKey, boolean ifNotExists) {
        StringBuilder sb = new StringBuilder();
        sb.append(ifNotExists && supportsCreateTableIfNotExists() ? "CREATE TABLE IF NOT EXISTS " : "CREATE TABLE ");
        sb.append(qualified(table)).append(" (\n");
        boolean first = true;
        for (org.eclipse.daanse.sql.model.schema.ColumnDefinition cd : columns) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append("  ").append(quoteIdentifier(cd.column().name()));
            sb.append(' ').append(nativeType(cd.columnMetaData()));
            cd.columnMetaData().columnDefault().ifPresent(d -> sb.append(" DEFAULT ").append(d));
        }
        sb.append("\n)");
        sb.append(createTableSuffix(primaryKey == null ? List.of()
                : primaryKey.columns().stream().map(c -> quoteIdentifier(c.name()).toString()).toList()));
        return sb.toString();
    }

    /**
     * {@code ENGINE = MergeTree() ORDER BY (…)}. MergeTree is the
     * general-purpose engine and sorts by the key where there is one; where
     * there is none, {@code ORDER BY tuple()} is how ClickHouse spells "no
     * ordering".
     */
    @Override
    public String createTableSuffix(List<String> quotedOrderByColumns) {
        if (quotedOrderByColumns == null || quotedOrderByColumns.isEmpty()) {
            return " ENGINE = MergeTree() ORDER BY tuple()";
        }
        return " ENGINE = MergeTree() ORDER BY (" + String.join(", ", quotedOrderByColumns) + ")";
    }

    /** JDBC-free constructor for SQL generation. */
    public ClickHouseDialect() {
        super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults());
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public ClickHouseDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    @Override
    public boolean supportsSequences() {
        return false;
    }

    @Override
    public boolean supportsDropConstraintIfExists() {
        return false;
    }

    @Override
    public boolean supportsCreateOrReplaceView() {
        return false;
    }

    @Override
    public boolean requiresDrillthroughMaxRowsInLimit() {
        return true;
    }

    /**
     * ClickHouse rejects a bare {@code UNION} when the server-side
     * {@code union_default_mode} setting is empty (the default):
     * {@code Code: 558 DB::Exception: Expected ALL or DISTINCT in SelectWithUnion
     * query}. Spell the duplicate-eliminating union explicitly.
     */
    @Override
    public String unionDistinctKeyword() {
        return "union distinct";
    }

    @Override
    public void quoteStringLiteral(StringBuilder buf, String s) {
        buf.append('\'');

        String s0 = s.replace("\\", "\\\\");
        s0 = s0.replace("'", "\\'");
        buf.append(s0);

        buf.append('\'');
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }

    // Unified BitOperation methods

    @Override
    public java.util.Optional<String> generateBitAggregation(BitOperation operation, CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        StringBuilder result = switch (operation) {
        case AND -> buf.append("groupBitAnd(").append(operand).append(")");
        case OR -> buf.append("groupBitOr(").append(operand).append(")");
        case XOR -> buf.append("groupBitXor(").append(operand).append(")");
        case NAND -> buf.append("NOT(groupBitAnd(").append(operand).append("))");
        case NOR -> buf.append("NOT(groupBitOr(").append(operand).append("))");
        case NXOR -> buf.append("NOT(groupBitXor(").append(operand).append("))");
        };
        return java.util.Optional.of(result.toString());
    }

    @Override
    public boolean supportsBitAggregation(BitOperation operation) {
        return true; // ClickHouse supports all bit operations
    }

    @Override
    public java.util.Optional<String> generateListAgg(CharSequence operand, boolean distinct, String separator,
            String coalesce, String onOverflowTruncate, List<OrderedColumn> columns) {
        StringBuilder buf = new StringBuilder(64);
        buf.append("groupArrayArray");
        buf.append("( ");
        buf.append(operand);
        buf.append(")");
        // groupArrayArray(page_visits)
        return java.util.Optional.of((buf).toString());
    }

    @Override
    public java.util.Optional<String> generateNthValueAgg(CharSequence operand, boolean ignoreNulls, Integer n,
            List<OrderedColumn> columns) {
        return java.util.Optional
                .of((buildNthValueFunction("nth_value", operand, ignoreNulls, n, columns, false)).toString());
    }

    @Override
    public boolean supportsNthValue() {
        return true;
    }

    @Override
    public boolean supportsListAgg() {
        return true;
    }
}
