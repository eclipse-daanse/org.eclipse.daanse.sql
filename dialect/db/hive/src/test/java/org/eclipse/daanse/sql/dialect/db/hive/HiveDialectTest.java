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
package org.eclipse.daanse.sql.dialect.db.hive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HiveDialectTest {

    private HiveDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new HiveDialect();
    }

    @Test
    void testName() {
        assertEquals("hive", dialect.name());
    }

    @Test
    void testQuoteIdentifierString() {
        assertEquals("`", dialect.getQuoteIdentifierString());
    }

    @Test
    void testSupportsResultSetConcurrency() {
        assertFalse(dialect.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
    }

    @Test
    void testRequiresAliasForFromQuery() {
        assertTrue(dialect.requiresAliasForFromQuery());
    }

    @Test
    void testRequiresOrderByAlias() {
        assertTrue(dialect.requiresOrderByAlias());
    }

    @Test
    void testAllowsOrderByAlias() {
        assertTrue(dialect.allowsOrderByAlias());
    }

    @Test
    void testRequiresGroupByAlias() {
        assertFalse(dialect.requiresGroupByAlias());
    }

    @Test
    void testAllowsCompoundCountDistinct() {
        assertTrue(dialect.allowsCompoundCountDistinct());
    }

    @Test
    void testAllowsJoinOn() {
        assertFalse(dialect.allowsJoinOn());
    }

    @Test
    void testRequiresUnionOrderByExprInSelect() {
        assertFalse(dialect.requiresUnionOrderByExprInSelect());
    }

    @Test
    void testRequiresUnionOrderByOrdinal() {
        assertFalse(dialect.requiresUnionOrderByOrdinal());
    }

    @Test
    void testQuoteDateLiteral() {
        StringBuilder buf = new StringBuilder();
        dialect.quoteDateLiteral(buf, java.sql.Date.valueOf("2024-01-15"));
        assertEquals("'2024-01-15'", buf.toString());
    }

    @Test
    void testQuoteTimestampLiteral() {
        StringBuilder buf = new StringBuilder();
        // The public overload normalizes through Timestamp.toString(), hence the ".0".
        dialect.quoteTimestampLiteral(buf, "2024-01-15 10:30:00");
        assertEquals("cast( '2024-01-15 10:30:00.0' as timestamp )", buf.toString());
    }

    @Test
    void testQuoteTimestampLiteral_InvalidFormat() {
        StringBuilder buf = new StringBuilder();
        assertThrows(NumberFormatException.class, () -> dialect.quoteTimestampLiteral(buf, "invalid"));
    }

    @Test
    void testGenerateOrderByNulls_AscNullsLast() {
        assertEquals("ISNULL(column1) ASC, column1 ASC", dialect.generateOrderByNulls("column1", true, true).toString());
    }

    @Test
    void testGenerateOrderByNulls_AscNullsFirst() {
        assertEquals("column1 ASC", dialect.generateOrderByNulls("column1", true, false).toString());
    }

    @Test
    void testGenerateOrderByNulls_DescNullsLast() {
        assertEquals("column1 DESC", dialect.generateOrderByNulls("column1", false, true).toString());
    }

    @Test
    void testGenerateOrderByNulls_DescNullsFirst() {
        assertEquals("ISNULL(column1) DESC, column1 DESC", dialect.generateOrderByNulls("column1", false, false).toString());
    }

    @Test
    void testGenerateInline() {
        String sql = dialect
                .generateInline(java.util.List.of("c1"), java.util.List.of("String"),
                        java.util.List.<String[]>of(new String[] { "a" }))
                .toString();
        assertTrue(sql.startsWith("select * from ("));
        assertTrue(sql.endsWith(") x limit 1"));
        assertFalse(sql.contains("dual")); // no built-in dual table in Hive — verified against Hive 4
    }
}
