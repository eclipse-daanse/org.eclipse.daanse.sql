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
