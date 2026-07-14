/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.api.generator;

import org.eclipse.daanse.sql.model.sql.BitOperation;

import java.util.List;

import org.eclipse.daanse.sql.model.sql.OrderedColumn;

public interface AggregationGenerator extends IdentifierQuoter {

    String DESC = " DESC";
    String COMMA = ", ";
    String OPEN_PAREN = "( ";
    String CLOSE_PAREN = " )";
    String ORDER_BY = "ORDER BY ";
    String WITHIN_GROUP = " WITHIN GROUP (";
    String OVER = "OVER ( ";
    String IGNORE_NULLS = " IGNORE NULLS ";
    String RESPECT_NULLS = " RESPECT NULLS ";

    // -------------------- list / string aggregation --------------------

    default java.util.Optional<String> generateListAgg(CharSequence operand, boolean distinct, String separator,
            String coalesce, String onOverflowTruncate, List<OrderedColumn> columns) {
        return java.util.Optional.empty();
    }

    /** Whether list aggregation is supported by this dialect. */
    default boolean supportsListAgg() {
        return false;
    }

    // -------------------- NTH_VALUE --------------------

    default java.util.Optional<String> generateNthValueAgg(CharSequence operand, boolean ignoreNulls, Integer n,
            List<OrderedColumn> columns) {
        return java.util.Optional.empty();
    }

    default boolean supportsNthValue() {
        return false;
    }

    default boolean supportsNthValueIgnoreNulls() {
        return false;
    }

    // -------------------- PERCENTILE --------------------

    /** Discrete percentile aggregate; empty when not supported. */
    default java.util.Optional<String> generatePercentileDisc(double percentile, boolean desc, String tableName,
            String columnName) {
        return java.util.Optional.empty();
    }

    /** Continuous (interpolated) percentile aggregate; empty when not supported. */
    default java.util.Optional<String> generatePercentileCont(double percentile, boolean desc, String tableName,
            String columnName) {
        return java.util.Optional.empty();
    }

    default boolean supportsPercentileDisc() {
        return false;
    }

    default boolean supportsPercentileCont() {
        return false;
    }

    // -------------------- bitwise aggregation --------------------

    default java.util.Optional<String> generateBitAggregation(BitOperation operation, CharSequence operand) {
        return java.util.Optional.empty();
    }

    /** Whether the given bitwise operation is supported. */
    default boolean supportsBitAggregation(BitOperation operation) {
        return false;
    }

    // -------------------- shared formatting helpers --------------------

    default StringBuilder buildPercentileFunction(String functionName, double percentile, boolean desc,
            String tableName, String columnName) {
        StringBuilder buf = new StringBuilder(64);
        buf.append(functionName).append("(").append(percentile).append(")").append(WITHIN_GROUP).append(ORDER_BY);
        if (tableName != null) {
            quoteIdentifier(buf, tableName, columnName);
        } else {
            quoteIdentifier(buf, columnName);
        }
        if (desc) {
            buf.append(DESC);
        }
        buf.append(")");
        return buf;
    }

    /**
     * @param supportsNullsHandling whether the engine accepts {@code IGNORE NULLS}/
     *                              {@code RESPECT NULLS}
     */
    default StringBuilder buildNthValueFunction(String functionName, CharSequence operand, boolean ignoreNulls,
            Integer n, List<OrderedColumn> columns, boolean supportsNullsHandling) {
        StringBuilder buf = new StringBuilder(64);
        buf.append(functionName);
        buf.append(OPEN_PAREN);
        buf.append(operand);
        buf.append(COMMA);
        buf.append(n == null || n < 1 ? 1 : n);
        buf.append(CLOSE_PAREN);
        if (supportsNullsHandling) {
            buf.append(ignoreNulls ? IGNORE_NULLS : RESPECT_NULLS);
        }
        buf.append(OVER);
        if (columns != null && !columns.isEmpty()) {
            buf.append(ORDER_BY);
            buf.append(buildOrderedColumnsClause(columns));
        }
        buf.append(CLOSE_PAREN);
        return buf;
    }

    default CharSequence buildOrderedColumnsClause(List<OrderedColumn> columns) {
        StringBuilder buf = new StringBuilder(64);
        if (columns == null) {
            return buf;
        }
        boolean first = true;
        for (OrderedColumn c : columns) {
            if (!first) {
                buf.append(COMMA);
            }
            if (c.tableName() != null) {
                quoteIdentifier(buf, c.tableName(), c.columnName());
            } else {
                quoteIdentifier(buf, c.columnName());
            }
            c.sortDirection().ifPresent(dir -> buf.append(' ').append(dir.name()));
            c.nullsOrder().ifPresent(no -> buf.append(" NULLS ").append(no.name()));
            first = false;
        }
        return buf;
    }
}
