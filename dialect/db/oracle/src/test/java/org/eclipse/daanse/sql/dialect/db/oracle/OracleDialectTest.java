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
package org.eclipse.daanse.sql.dialect.db.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OracleDialectTest {
    private Connection connection = mock(Connection.class);
    private DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    private OracleDialect dialect;

    @BeforeEach
    public void setUp() throws Exception {
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("ORACLE");
        dialect = new OracleDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData.fromConnection(connection));
    }

    /** Oracle has no VARBINARY: a binary of a length is RAW, one without a length or beyond 2000 bytes a BLOB. */
    @Test
    void aBinaryColumnIsRawOrABlob() {
        assertEquals("RAW(100)", dialect.binaryTypeName(java.util.OptionalInt.of(100)));
        assertEquals("BLOB", dialect.binaryTypeName(java.util.OptionalInt.empty()));
        assertEquals("BLOB", dialect.binaryTypeName(java.util.OptionalInt.of(100_000)));
    }

    /** Oracle counts a VARCHAR2 in bytes by default: a text column says it counts characters. */
    @Test
    void aTextColumnCountsCharacters() {
        assertEquals("VARCHAR2(400 CHAR)", dialect.nativeType(text(java.sql.JDBCType.NVARCHAR, 400)));
        assertEquals("VARCHAR2(255 CHAR)", dialect.nativeType(text(java.sql.JDBCType.VARCHAR, -1)));
        assertEquals("CLOB", dialect.nativeType(text(java.sql.JDBCType.VARCHAR, 8_000)));
        assertEquals("CHAR(3 CHAR)", dialect.nativeType(text(java.sql.JDBCType.CHAR, 3)));
    }

    private static org.eclipse.daanse.sql.model.schema.ColumnMetaData text(java.sql.JDBCType type, int size) {
        org.eclipse.daanse.sql.model.schema.ColumnMetaData meta = org.mockito.Mockito
                .mock(org.eclipse.daanse.sql.model.schema.ColumnMetaData.class);
        when(meta.dataType()).thenReturn(type);
        when(meta.columnSize()).thenReturn(size < 0 ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(size));
        return meta;
    }

    /** Oracle reads ANSI joins; comma joins were the inherited default, not an Oracle limit. */
    @Test
    void anInnerJoinIsWrittenWithJoinOn() {
        assertTrue(dialect.allowsJoinOn());
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
        assertEquals("table.column IS NOT NULL AND REGEXP_LIKE(table.column, '^(.*a.*)$', 'i')", sql);
    }

    @Test
    void testGenerateRegularExpression_MultilineFlag() throws Exception {
        assertTrue(dialect.regexGenerator().generateRegularExpression("table.column", "(?m).*a.*").isEmpty(),
                "under (?m) Oracle's ^/$ anchor per line, not per value - no correct rendering");
    }

    @Test
    void testGenerateRegularExpression_CaseSensitive() throws Exception {
        String sql = dialect.regexGenerator().generateRegularExpression("table.column", ".*a.*").get().toString();
        assertEquals("table.column IS NOT NULL AND REGEXP_LIKE(table.column, '^(.*a.*)$', '')", sql);
    }
}
//End OracleDialectTest.java
