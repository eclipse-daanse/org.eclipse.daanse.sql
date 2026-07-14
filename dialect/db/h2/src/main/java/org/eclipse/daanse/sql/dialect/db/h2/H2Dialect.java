/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.dialect.db.h2;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.sql.BitOperation;
import org.eclipse.daanse.sql.model.sql.OrderedColumn;
import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class H2Dialect extends AbstractJdbcDialect {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2Dialect.class);

    private static final String SUPPORTED_PRODUCT_NAME = "H2";

    /** JDBC-free constructor for SQL generation. */
    public H2Dialect() {
        super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults());
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public H2Dialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }

    @Override
    protected boolean supportsNullsOrdering() {
        return true; // H2 supports NULLS FIRST/LAST
    }

    // Unified BitOperation methods

    @Override
    public java.util.Optional<String> generateBitAggregation(BitOperation operation, CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        StringBuilder result = switch (operation) {
        case AND -> buf.append("BIT_AND_AGG(").append(operand).append(")");
        case OR -> buf.append("BIT_OR_AGG(").append(operand).append(")");
        case XOR -> buf.append("BIT_XOR_AGG(").append(operand).append(")");
        case NAND -> buf.append("BIT_NAND_AGG(").append(operand).append(")");
        case NOR -> buf.append("BIT_NOR_AGG(").append(operand).append(")");
        case NXOR -> buf.append("BIT_XNOR_AGG(").append(operand).append(")");
        };
        return java.util.Optional.of(result.toString());
    }

    @Override
    public boolean supportsBitAggregation(BitOperation operation) {
        return true; // H2 supports all bit operations
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
        return java.util.Optional.of((buf).toString());
    }

    @Override
    public java.util.Optional<String> generateNthValueAgg(CharSequence operand, boolean ignoreNulls, Integer n,
            List<OrderedColumn> columns) {
        return java.util.Optional
                .of((buildNthValueFunction("NTH_VALUE", operand, ignoreNulls, n, columns, true)).toString());
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

    @Override
    public boolean supportsNthValueIgnoreNulls() {
        return true;
    }

    @Override
    public boolean supportsListAgg() {
        return true;
    }
}
