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
package org.eclipse.daanse.sql.dialect.db.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DerbyDialectTest {

    /** Derby sums an INTEGER column as an INTEGER: the engine widens the operand to BIGINT. */
    @Test
    void aSumKeepsTheTypeOfItsOperand() {
        assertTrue(new DerbyDialect().sumKeepsTheTypeOfItsOperand());
        assertEquals("CAST(x AS BIGINT)", new DerbyDialect().generateKnownFunction(
                org.eclipse.daanse.sql.dialect.api.generator.KnownFunction.TO_WIDE_INTEGER,
                java.util.List.of("x")).toString());
    }

    /**
     * Derby has no EXTRACT ("Syntax error: Encountered FROM", the month of a birth date written as a tree, database
     * run 30.10.2026); its own YEAR(x), MONTH(x) and DAY(x) take the part.
     */
    @Test
    void aPartOfADateIsTakenByDerbysOwnFunction() {
        assertEquals("MONTH(d)", new DerbyDialect().generateKnownFunction(
                org.eclipse.daanse.sql.dialect.api.generator.KnownFunction.MONTH, java.util.List.of("d")).toString());
        assertEquals("YEAR(d)", new DerbyDialect().generateKnownFunction(
                org.eclipse.daanse.sql.dialect.api.generator.KnownFunction.YEAR, java.util.List.of("d")).toString());
    }

    /**
     * Derby refuses an untyped null in a select list ("Syntax error: Encountered null", a union arm without a column,
     * database run 30.10.2026): a null of a known type is spelled typed.
     */
    @Test
    void aNullOfAKnownTypeIsSpelledTyped() {
        StringBuilder integer = new StringBuilder();
        new DerbyDialect().quote(integer, null, org.eclipse.daanse.sql.model.type.Datatype.INTEGER);
        assertEquals("CAST(NULL AS INTEGER)", integer.toString());
        // a text null stays untyped: VARCHAR is also the type of a null nothing better is known of (a CASE branch)
        StringBuilder text = new StringBuilder();
        new DerbyDialect().quote(text, null, org.eclipse.daanse.sql.model.type.Datatype.VARCHAR);
        assertEquals("null", text.toString().toLowerCase(java.util.Locale.ROOT));
    }

    private Connection connection = mock(Connection.class);
    private DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    private DerbyDialect dialect;

    @BeforeEach
    protected void setUp() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("Apache Derby");
        when(metaData.getDatabaseProductVersion()).thenReturn("10.15.2.0");
        dialect = new DerbyDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData.fromConnection(connection));
    }

    /** A truth value is stored as SMALLINT, so its literal is 1 or 0: Derby does not compare SMALLINT with BOOLEAN. */
    @Test
    void aTruthValueIsWrittenAsTheNumberItIsStoredAs() {
        for (String[] pair : new String[][] { { "true", "1" }, { "FALSE", "0" }, { "1", "1" }, { "0", "0" } }) {
            StringBuilder buf = new StringBuilder();
            dialect.quoteBooleanLiteral(buf, pair[0]);
            org.junit.jupiter.api.Assertions.assertEquals(pair[1], buf.toString(), pair[0]);
        }
    }

    /** Derby cuts a quotient's scale; the dividend is read as DOUBLE. */
    @Test
    void aDivisionReadsItsDividendAsDouble() {
        org.junit.jupiter.api.Assertions.assertEquals("CAST(x AS DOUBLE)",
                dialect.functionGenerator().quotientDividend("x").toString());
    }

    /** AVG over an INTEGER column is an integer in Derby; the operand is read as DOUBLE. */
    @Test
    void anAverageReadsItsOperandAsDouble() {
        org.junit.jupiter.api.Assertions.assertEquals("CAST(x AS DOUBLE)",
                dialect.aggregationGenerator().averageOperand("x").toString());
    }

    /** Derby deadlocks on DDL run at once; a loader writes one statement at a time. */
    @Test
    void loadingIsOneWriterAtATime() {
        org.junit.jupiter.api.Assertions.assertFalse(dialect.supportsParallelLoading());
    }

    @Test
    void testGetDialectName() {
        assertEquals("derby", dialect.name());
    }

    @Test
    void testRequiresAliasForFromQuery() {
        assertTrue(dialect.requiresAliasForFromQuery());
    }

    @Test
    void testAllowsMultipleCountDistinct() {
        assertFalse(dialect.allowsMultipleCountDistinct());
    }

    @Test
    void testSupportsGroupByExpressions() {
        assertFalse(dialect.supportsGroupByExpressions());
    }

    @Test
    void testAllowsFieldAs() {
        // Derby supports (quoted) select-list aliases, including ones with spaces.
        assertTrue(dialect.allowsFieldAlias());
    }

    @Test
    void testQuoteDateLiteral() {
        StringBuilder buf = new StringBuilder();
        dialect.quoteDateLiteral(buf, java.sql.Date.valueOf("2024-01-15"));
        assertEquals("DATE('2024-01-15')", buf.toString());
    }

}
