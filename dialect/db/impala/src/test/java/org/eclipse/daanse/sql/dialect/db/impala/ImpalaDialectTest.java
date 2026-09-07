/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.dialect.db.impala;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImpalaDialectTest {

    private ImpalaDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new ImpalaDialect();
    }

    @Test
    void testName() {
        assertEquals("impala", dialect.name());
    }

    @Test
    void testQuoteIdentifierString() {
        assertEquals("`", dialect.getQuoteIdentifierString());
    }

    @Test
    void testAllowsRegularExpressionInWhereClause() {
        assertTrue(dialect.allowsRegularExpressionInWhereClause());
    }

    @Test
    void testGenerateRegularExpression_InvalidRegex() {
        assertEquals(Optional.empty(), dialect.generateRegularExpression("table.column", "(a"));
    }

    @Test
    void testGenerateRegularExpression_CaseInsensitive() {
        String sql = dialect.generateRegularExpression("table.column", "(?i)|(?u).*a.*").orElseThrow();
        assertTrue(sql.contains("UPPER"));
        assertTrue(sql.contains("cast(table.column as string)"));
        assertTrue(sql.contains("REGEXP"));
        assertTrue(sql.contains("'\\A(?:.*A.*)\\z'"));
    }

    @Test
    void testGenerateRegularExpression_CaseSensitive() {
        String sql = dialect.generateRegularExpression("table.column", ".*1.*").orElseThrow();
        assertFalse(sql.contains("UPPER"));
        assertTrue(sql.contains("cast(table.column as string)"));
        assertTrue(sql.contains("REGEXP"));
        assertTrue(sql.contains("'\\A(?:.*1.*)\\z'"));
    }

    @Test
    void testGenerateOrderByNulls() {
        assertEquals("column1 ASC", dialect.generateOrderByNulls("column1", true, true).toString());
        assertEquals("column1 DESC", dialect.generateOrderByNulls("column1", false, false).toString());
    }

    @Test
    void testGenerateOrderByNulls_NeverEmitsIsnull() {
        // Impala has no single-argument ISNULL(x) — Hive's emulation must not leak through.
        for (boolean asc : new boolean[] { true, false }) {
            for (boolean nullsLast : new boolean[] { true, false }) {
                assertFalse(dialect.generateOrderByNulls("column1", asc, nullsLast).toString().contains("ISNULL"));
            }
        }
    }

    @Test
    void testGenerateOrderItem_NullableNullsLast() {
        assertEquals("CASE WHEN column1 IS NULL THEN 1 ELSE 0 END, column1 ASC",
                dialect.generateOrderItem("column1", true, true, true).toString());
    }

    @Test
    void testGenerateOrderItem_NullableNullsFirst() {
        assertEquals("CASE WHEN column1 IS NULL THEN 0 ELSE 1 END, column1 DESC",
                dialect.generateOrderItem("column1", true, false, false).toString());
    }

    @Test
    void testQuoteStringLiteral() {
        StringBuilder buf = new StringBuilder();
        dialect.quoteStringLiteral(buf, "it's");
        assertEquals("'it\\\\'s'", buf.toString());
    }

    @Test
    void testAllowsMultipleCountDistinct() {
        assertFalse(dialect.allowsMultipleCountDistinct());
    }

    @Test
    void testAllowsCompoundCountDistinct() {
        assertTrue(dialect.allowsCompoundCountDistinct());
    }

    @Test
    void testRequiresOrderByAlias() {
        assertFalse(dialect.requiresOrderByAlias());
    }

    @Test
    void testAllowsOrderByAlias() {
        // Inherited explicitly from HiveDialect — must not flip with requiresOrderByAlias().
        assertTrue(dialect.allowsOrderByAlias());
    }

    @Test
    void testRequiresAliasForFromQuery() {
        assertTrue(dialect.requiresAliasForFromQuery());
    }

    @Test
    void testSupportsGroupByExpressions() {
        assertFalse(dialect.supportsGroupByExpressions());
    }

    @Test
    void testAllowsSelectNotInGroupBy() {
        assertFalse(dialect.allowsSelectNotInGroupBy());
    }

    @Test
    void testAllowsJoinOn() {
        assertFalse(dialect.allowsJoinOn());
    }

    @Test
    void testSupportsDdl() {
        assertTrue(dialect.supportsDdl());
    }
}
