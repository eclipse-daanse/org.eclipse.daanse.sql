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
