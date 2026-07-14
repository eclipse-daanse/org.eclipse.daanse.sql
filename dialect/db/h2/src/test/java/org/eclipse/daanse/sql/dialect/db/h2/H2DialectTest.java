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
package org.eclipse.daanse.sql.dialect.db.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.sql.BitOperation;
import org.eclipse.daanse.sql.model.sql.NullsOrder;
import org.eclipse.daanse.sql.model.sql.SortDirection;
import org.eclipse.daanse.sql.model.sql.OrderedColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class H2DialectTest {

    private Connection connection = mock(Connection.class);
    private DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    private H2Dialect dialect;

    @BeforeEach
    protected void setUp() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");
        when(metaData.getDatabaseProductVersion()).thenReturn("2.2.224");
        dialect = new H2Dialect(org.eclipse.daanse.sql.dialect.api.DialectInitData.fromConnection(connection));
    }

    @Test
    void testGetDialectName() {
        assertEquals("h2", dialect.name());
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
            assertEquals("BIT_AND_AGG(column1)", result);
        }

        @Test
        void testGenerateBitAggregation_OR() {
            String result = dialect.generateBitAggregation(BitOperation.OR, "column1").orElseThrow();
            assertEquals("BIT_OR_AGG(column1)", result);
        }

        @Test
        void testGenerateBitAggregation_XOR() {
            String result = dialect.generateBitAggregation(BitOperation.XOR, "column1").orElseThrow();
            assertEquals("BIT_XOR_AGG(column1)", result);
        }

        @Test
        void testGenerateBitAggregation_NAND() {
            String result = dialect.generateBitAggregation(BitOperation.NAND, "column1").orElseThrow();
            assertEquals("BIT_NAND_AGG(column1)", result);
        }

        @Test
        void testGenerateBitAggregation_NOR() {
            String result = dialect.generateBitAggregation(BitOperation.NOR, "column1").orElseThrow();
            assertEquals("BIT_NOR_AGG(column1)", result);
        }

        @Test
        void testGenerateBitAggregation_NXOR() {
            String result = dialect.generateBitAggregation(BitOperation.NXOR, "column1").orElseThrow();
            assertEquals("BIT_XNOR_AGG(column1)", result);
        }
    }

    @Nested
    @DisplayName("Window Function Support Tests")
    class WindowFunctionSupportTests {

        @Test
        void testSupportsPercentileDisc() {
            assertTrue(dialect.supportsPercentileDisc());
        }

        @Test
        void testSupportsPercentileCont() {
            assertTrue(dialect.supportsPercentileCont());
        }

        @Test
        void testSupportsNthValue() {
            assertTrue(dialect.supportsNthValue());
        }

        @Test
        void testSupportsNthValueIgnoreNulls() {
            assertTrue(dialect.supportsNthValueIgnoreNulls());
        }

        @Test
        void testSupportsListAgg() {
            assertTrue(dialect.supportsListAgg());
        }
    }

    @Nested
    @DisplayName("ListAgg Generation Tests")
    class ListAggGenerationTests {

        @Test
        void testGenerateListAgg_Simple() {
            String result = dialect.generateListAgg("column1", false, null, null, null, null).orElseThrow();
            assertEquals("LISTAGG( column1, ', ')", result);
        }

        @Test
        void testGenerateListAgg_WithDistinct() {
            String result = dialect.generateListAgg("column1", true, null, null, null, null).orElseThrow();
            assertEquals("LISTAGG( DISTINCT column1, ', ')", result);
        }

        @Test
        void testGenerateListAgg_WithSeparator() {
            String result = dialect.generateListAgg("column1", false, ";", null, null, null).orElseThrow();
            assertEquals("LISTAGG( column1, ';')", result);
        }

        @Test
        void testGenerateListAgg_WithCoalesce() {
            String result = dialect.generateListAgg("column1", false, null, "N/A", null, null).orElseThrow();
            assertEquals("LISTAGG( COALESCE(column1, 'N/A'), ', ')", result);
        }

        @Test
        void testGenerateListAgg_WithOverflowTruncate() {
            String result = dialect.generateListAgg("column1", false, null, null, "...", null).orElseThrow();
            assertEquals("LISTAGG( column1, ', ' ON OVERFLOW TRUNCATE '...' WITHOUT COUNT)", result);
        }

        @Test
        void testGenerateListAgg_WithOrderBy_DbDefault() {
            List<OrderedColumn> columns = List.of(new OrderedColumn("id", null));
            String result = dialect.generateListAgg("column1", false, null, null, null, columns).orElseThrow();
            assertEquals("LISTAGG( column1, ', ') WITHIN GROUP (ORDER BY id)", result);
        }

        @Test
        void testGenerateListAgg_WithOrderByAsc() {
            List<OrderedColumn> columns = List.of(new OrderedColumn("id", null, SortDirection.ASC));
            String result = dialect.generateListAgg("column1", false, null, null, null, columns).orElseThrow();
            assertEquals("LISTAGG( column1, ', ') WITHIN GROUP (ORDER BY id ASC)", result);
        }

        @Test
        void testGenerateListAgg_WithOrderByDesc() {
            List<OrderedColumn> columns = List.of(new OrderedColumn("id", null, SortDirection.DESC));
            String result = dialect.generateListAgg("column1", false, null, null, null, columns).orElseThrow();
            assertEquals("LISTAGG( column1, ', ') WITHIN GROUP (ORDER BY id DESC)", result);
        }

        @Test
        void testGenerateListAgg_WithNullsFirst() {
            List<OrderedColumn> columns = List
                    .of(new OrderedColumn("id", null, Optional.empty(), Optional.of(NullsOrder.FIRST)));
            String result = dialect.generateListAgg("column1", false, null, null, null, columns).orElseThrow();
            assertEquals("LISTAGG( column1, ', ') WITHIN GROUP (ORDER BY id NULLS FIRST)", result);
        }

        @Test
        void testGenerateListAgg_WithNullsLast() {
            List<OrderedColumn> columns = List
                    .of(new OrderedColumn("id", null, Optional.empty(), Optional.of(NullsOrder.LAST)));
            String result = dialect.generateListAgg("column1", false, null, null, null, columns).orElseThrow();
            assertEquals("LISTAGG( column1, ', ') WITHIN GROUP (ORDER BY id NULLS LAST)", result);
        }

        @Test
        void testGenerateListAgg_WithDescNullsFirst() {
            List<OrderedColumn> columns = List
                    .of(new OrderedColumn("id", null, Optional.of(SortDirection.DESC), Optional.of(NullsOrder.FIRST)));
            String result = dialect.generateListAgg("column1", false, null, null, null, columns).orElseThrow();
            assertEquals("LISTAGG( column1, ', ') WITHIN GROUP (ORDER BY id DESC NULLS FIRST)", result);
        }

        @Test
        void testGenerateListAgg_WithAscNullsLast() {
            List<OrderedColumn> columns = List
                    .of(new OrderedColumn("id", null, Optional.of(SortDirection.ASC), Optional.of(NullsOrder.LAST)));
            String result = dialect.generateListAgg("column1", false, null, null, null, columns).orElseThrow();
            assertEquals("LISTAGG( column1, ', ') WITHIN GROUP (ORDER BY id ASC NULLS LAST)", result);
        }
    }

    @Nested
    @DisplayName("OrderedColumn Factory Method Tests")
    class OrderedColumnFactoryTests {

        @Test
        void testOrderedColumn_FactoryAsc() {
            List<OrderedColumn> columns = List.of(OrderedColumn.asc("id"));
            String result = dialect.generateListAgg("column1", false, null, null, null, columns).orElseThrow();
            assertEquals("LISTAGG( column1, ', ') WITHIN GROUP (ORDER BY id ASC)", result);
        }

        @Test
        void testOrderedColumn_FactoryDesc() {
            List<OrderedColumn> columns = List.of(OrderedColumn.desc("id"));
            String result = dialect.generateListAgg("column1", false, null, null, null, columns).orElseThrow();
            assertEquals("LISTAGG( column1, ', ') WITHIN GROUP (ORDER BY id DESC)", result);
        }

        @Test
        void testOrderedColumn_FactoryOf() {
            List<OrderedColumn> columns = List.of(OrderedColumn.of("id", SortDirection.DESC, NullsOrder.FIRST));
            String result = dialect.generateListAgg("column1", false, null, null, null, columns).orElseThrow();
            assertEquals("LISTAGG( column1, ', ') WITHIN GROUP (ORDER BY id DESC NULLS FIRST)", result);
        }

        @Test
        void testOrderedColumn_FactoryOfNullDirection() {
            List<OrderedColumn> columns = List.of(OrderedColumn.of("id", null, NullsOrder.LAST));
            String result = dialect.generateListAgg("column1", false, null, null, null, columns).orElseThrow();
            assertEquals("LISTAGG( column1, ', ') WITHIN GROUP (ORDER BY id NULLS LAST)", result);
        }
    }

    @Nested
    @DisplayName("Percentile Generation Tests")
    class PercentileGenerationTests {

        @Test
        void testGeneratePercentileDisc() {
            String result = dialect.generatePercentileDisc(0.5, false, "table1", "column1").orElseThrow();
            assertTrue(result.contains("PERCENTILE_DISC"));
            assertTrue(result.contains("0.5"));
        }

        @Test
        void testGeneratePercentileCont() {
            String result = dialect.generatePercentileCont(0.75, false, "table1", "column1").orElseThrow();
            assertTrue(result.contains("PERCENTILE_CONT"));
            assertTrue(result.contains("0.75"));
        }
    }
}
