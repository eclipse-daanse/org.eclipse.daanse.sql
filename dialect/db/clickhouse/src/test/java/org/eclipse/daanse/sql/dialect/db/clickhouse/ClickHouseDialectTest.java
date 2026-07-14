/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.dialect.db.clickhouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import org.eclipse.daanse.sql.model.sql.BitOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ClickHouseDialectTest {

    private Connection connection = mock(Connection.class);
    private DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    private ClickHouseDialect dialect;

    @BeforeEach
    protected void setUp() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("ClickHouse");
        when(metaData.getDatabaseProductVersion()).thenReturn("23.0");
        dialect = new ClickHouseDialect(
                org.eclipse.daanse.sql.dialect.api.DialectInitData.fromConnection(connection));
    }

    @Test
    void testGetDialectName() {
        assertEquals("clickhouse", dialect.name());
    }

    @Test
    void testRequiresDrillthroughMaxRowsInLimit() {
        assertTrue(dialect.requiresDrillthroughMaxRowsInLimit());
    }

    @Test
    void capability_flags_reflect_clickhouse_constraints() {
        // ClickHouse: no FK / sequences, no DROP CONSTRAINT IF EXISTS, no
        // CREATE OR REPLACE VIEW. Most other IF EXISTS forms ARE supported.
        assertFalse(dialect.supportsSequences());
        assertFalse(dialect.supportsDropConstraintIfExists());
        assertFalse(dialect.supportsCreateOrReplaceView());
        assertTrue(dialect.supportsDropTableIfExists());
        assertTrue(dialect.supportsCreateTableIfNotExists());
    }

    @Nested
    @DisplayName("String Literal Quoting Tests")
    class StringLiteralQuotingTests {

        @Test
        void testQuoteStringLiteral_Simple() {
            StringBuilder buf = new StringBuilder();
            dialect.quoteStringLiteral(buf, "test");
            assertEquals("'test'", buf.toString());
        }

        @Test
        void testQuoteStringLiteral_WithSingleQuote() {
            StringBuilder buf = new StringBuilder();
            dialect.quoteStringLiteral(buf, "don't");
            assertEquals("'don\\'t'", buf.toString());
        }

        @Test
        void testQuoteStringLiteral_WithBackslash() {
            StringBuilder buf = new StringBuilder();
            dialect.quoteStringLiteral(buf, "path\\to\\file");
            assertEquals("'path\\\\to\\\\file'", buf.toString());
        }

        @Test
        void testQuoteStringLiteral_WithBothQuoteAndBackslash() {
            StringBuilder buf = new StringBuilder();
            dialect.quoteStringLiteral(buf, "it's a\\path");
            assertEquals("'it\\'s a\\\\path'", buf.toString());
        }
    }

    @Nested
    @DisplayName("Bit Aggregation Tests")
    class BitAggregationTests {

        @ParameterizedTest
        @EnumSource(BitOperation.class)
        void testSupportsBitAggregation(BitOperation operation) {
            assertTrue(dialect.supportsBitAggregation(operation));
        }

        @Test
        void testGenerateBitAggregation_AND() {
            String result = dialect.generateBitAggregation(BitOperation.AND, "column1").orElseThrow();
            assertEquals("groupBitAnd(column1)", result);
        }

        @Test
        void testGenerateBitAggregation_OR() {
            String result = dialect.generateBitAggregation(BitOperation.OR, "column1").orElseThrow();
            assertEquals("groupBitOr(column1)", result);
        }

        @Test
        void testGenerateBitAggregation_XOR() {
            String result = dialect.generateBitAggregation(BitOperation.XOR, "column1").orElseThrow();
            assertEquals("groupBitXor(column1)", result);
        }

        @Test
        void testGenerateBitAggregation_NAND() {
            String result = dialect.generateBitAggregation(BitOperation.NAND, "column1").orElseThrow();
            assertEquals("NOT(groupBitAnd(column1))", result);
        }

        @Test
        void testGenerateBitAggregation_NOR() {
            String result = dialect.generateBitAggregation(BitOperation.NOR, "column1").orElseThrow();
            assertEquals("NOT(groupBitOr(column1))", result);
        }

        @Test
        void testGenerateBitAggregation_NXOR() {
            String result = dialect.generateBitAggregation(BitOperation.NXOR, "column1").orElseThrow();
            assertEquals("NOT(groupBitXor(column1))", result);
        }
    }

    @Nested
    @DisplayName("Window Function Tests")
    class WindowFunctionTests {

        @Test
        void testSupportsNthValue() {
            assertTrue(dialect.supportsNthValue());
        }

        @Test
        void testSupportsListAgg() {
            assertTrue(dialect.supportsListAgg());
        }

        @Test
        void testGenerateListAgg() {
            String result = dialect.generateListAgg("column1", false, null, null, null, null).orElseThrow();
            assertEquals("groupArrayArray( column1)", result);
        }
    }
}
