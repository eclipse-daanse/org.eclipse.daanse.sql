/*
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * For more information please visit the Project: Hitachi Vantara - Mondrian
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial adapt parts of Syntax.class
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.dialect.db.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import org.eclipse.daanse.sql.jdbc.api.meta.IdentifierInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostgreSqlDialectTest {
    private Connection connection = mock(Connection.class);
    private DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    private IdentifierInfo identifierInfo = mock(IdentifierInfo.class);
    private PostgreSqlDialect dialect;

    @BeforeEach
    protected void setUp() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("POSTGRESQL");
        dialect = new PostgreSqlDialect(
                org.eclipse.daanse.sql.dialect.api.DialectInitData.fromConnection(connection));
    }

    @Test
    void testAllowsRegularExpressionInWhereClause() {
        assertTrue(dialect.allowsRegularExpressionInWhereClause());
    }

    @Test
    void testGenerateRegularExpression_InvalidRegex() throws Exception {
        assertTrue(dialect.regexGenerator().generateRegularExpression("table.column", "(a").isEmpty(),
                "Invalid regex should be ignored");
    }

    @Test
    void testGenerateRegularExpression_CaseInsensitive() throws Exception {
        String sql = dialect.regexGenerator().generateRegularExpression("table.column", "(?i)|(?u).*a.*").get()
                .toString();
        assertEquals("cast(table.column as text) is not null and cast(table.column as text) ~ '(?i).*a.*'", sql);
    }

    @Test
    void testGenerateRegularExpression_CaseSensitive() throws Exception {
        String sql = dialect.regexGenerator().generateRegularExpression("table.column", ".*a.*").get().toString();
        assertEquals("cast(table.column as text) is not null and cast(table.column as text) ~ '.*a.*'", sql);
    }

}
//End PostgreSqlDialectTest.java
