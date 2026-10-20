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

    // -------------------- AVG --------------------

    /**
     * The operand of an {@code avg} aggregate as this dialect needs it to compute the mean: the operand itself, unless
     * the engine averages whole numbers to a whole number. SQL Server returns 31 for the mean of 31 and 32 over an
     * INTEGER column, where every other engine, MDX and SSAS give 31.5 (database run 19./20.10., F-10.6 2).
     */
    default CharSequence averageOperand(CharSequence operand) {
        return operand;
    }

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
    /**
     * The n-th value of the rows of the group, in the order of the columns, as an ordered array aggregate:
     * {@code <arrayExtractOpen>ARRAY_AGG(operand ORDER BY ...) FILTER (WHERE operand IS NOT NULL)<arrayExtractClose>}.
     * An aggregate, not the window function {@code NTH_VALUE ... OVER (ORDER BY ...)}: a window in a grouped
     * query runs over the groups, so a cell showed the values of other members' groups (V-82). n counts from 1; a
     * group with fewer values gives NULL.
     *
     * @param arrayAgg the dialect's ordered array aggregate, {@code ARRAY_AGG} or {@code list}
     * @param open     what stands before the array, {@code "("} or {@code "list_extract("}
     * @param close    what follows it and picks the element: {@code ")[" + n + "]"} or {@code ", " + n + ")"}
     */
    default StringBuilder buildNthValueOfArray(String arrayAgg, CharSequence operand, boolean ignoreNulls,
            List<OrderedColumn> columns, String open, String close) {
        StringBuilder buf = new StringBuilder(64);
        buf.append(open).append(arrayAgg).append('(').append(operand);
        if (columns != null && !columns.isEmpty()) {
            buf.append(" ORDER BY ").append(buildOrderedColumnsClause(columns));
        }
        buf.append(')');
        if (ignoreNulls) {
            buf.append(" FILTER (WHERE ").append(operand).append(" IS NOT NULL)");
        }
        buf.append(close);
        return buf;
    }

    /** n as the element to pick: at least 1. */
    static int nthIndex(Integer n) {
        return n == null || n < 1 ? 1 : n;
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
