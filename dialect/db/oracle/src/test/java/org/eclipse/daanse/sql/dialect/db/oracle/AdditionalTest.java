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
package org.eclipse.daanse.sql.dialect.db.oracle;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.junit.jupiter.api.Test;

class AdditionalTest {

    @Test
    void testOracleTypeMapQuirks() throws SQLException {

        ResultSetMetaData resultSet = mock(ResultSetMetaData.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(resultSet.getColumnName(1)).thenReturn("c0");
        when(resultSet.getColumnType(1)).thenReturn(Types.NUMERIC);
        when(resultSet.getPrecision(1)).thenReturn(0);
        when(resultSet.getScale(1)).thenReturn(0);

        Dialect oracleDialect = new OracleDialect(
                org.eclipse.daanse.sql.dialect.api.DialectInitData.fromConnection(connection));

        assertSame(BestFitColumnType.INT, oracleDialect.getType(resultSet, 0),
                "Oracle dialect NUMERIC type with 0 precision, 0 scale should map "
                        + "to INT, unless column starts with 'm'");

        resultSet = mock(ResultSetMetaData.class);
        when(resultSet.getColumnName(1)).thenReturn("c0");
        when(resultSet.getColumnType(1)).thenReturn(Types.NUMERIC);
        when(resultSet.getPrecision(1)).thenReturn(5);
        when(resultSet.getScale(1)).thenReturn(-127);

        assertSame(BestFitColumnType.DOUBLE, oracleDialect.getType(resultSet, 0),
                "Oracle dialect NUMERIC type with non-zero precision, -127 scale "
                        + " should map to DOUBLE.  MONDRIAN-1044");

        resultSet = mock(ResultSetMetaData.class);
        when(resultSet.getColumnName(1)).thenReturn("c0");
        when(resultSet.getColumnType(1)).thenReturn(Types.NUMERIC);
        when(resultSet.getPrecision(1)).thenReturn(9);
        when(resultSet.getScale(1)).thenReturn(0);
        assertSame(BestFitColumnType.INT, oracleDialect.getType(resultSet, 0),
                "Oracle dialect NUMERIC type with precision less than 10, 0 scale " + " should map to INT. ");

        resultSet = mock(ResultSetMetaData.class);
        when(resultSet.getColumnName(1)).thenReturn("c0");
        when(resultSet.getColumnType(1)).thenReturn(Types.NUMERIC);
        when(resultSet.getPrecision(1)).thenReturn(38);
        when(resultSet.getScale(1)).thenReturn(0);
        assertSame(BestFitColumnType.INT, oracleDialect.getType(resultSet, 0),
                "Oracle dialect NUMERIC type with precision = 38, scale = 0"
                        + " should map to INT.  38 is a magic number in Oracle "
                        + " for integers of unspecified precision.");

        resultSet = mock(ResultSetMetaData.class);
        when(resultSet.getColumnName(1)).thenReturn("c0");
        when(resultSet.getColumnType(1)).thenReturn(Types.NUMERIC);
        when(resultSet.getPrecision(1)).thenReturn(20);
        when(resultSet.getScale(1)).thenReturn(0);
        assertSame(BestFitColumnType.DOUBLE, oracleDialect.getType(resultSet, 0),
                "Oracle dialect DECIMAL type with precision > 9, scale = 0"
                        + " should map to DOUBLE (unless magic #38)");

        resultSet = mock(ResultSetMetaData.class);
        when(resultSet.getColumnName(1)).thenReturn("c0");
        when(resultSet.getColumnType(1)).thenReturn(Types.NUMERIC);
        when(resultSet.getPrecision(1)).thenReturn(0);
        when(resultSet.getScale(1)).thenReturn(-127);
        assertSame(BestFitColumnType.INT, oracleDialect.getType(resultSet, 0),
                "Oracle dialect NUMBER type with precision =0 , scale = -127"
                        + " should map to INT.  GROUPING SETS queries can shift"
                        + " scale for columns to -127, whether INT or other NUMERIC."
                        + " Assume INT unless the column name indicates it is a measure.");

        resultSet = mock(ResultSetMetaData.class);
        when(resultSet.getColumnName(1)).thenReturn("m0");
        when(resultSet.getColumnType(1)).thenReturn(Types.NUMERIC);
        when(resultSet.getPrecision(1)).thenReturn(0);
        when(resultSet.getScale(1)).thenReturn(-127);
        assertSame(BestFitColumnType.DOUBLE, oracleDialect.getType(resultSet, 0),
                "Oracle dialect NUMBER type with precision =0 , scale = -127"
                        + " should map to DOUBLE if measure name starts with 'm'"
                        + " (not INT — data loss; not OBJECT — BigDecimal leaks to the cell layer)");
    }
}
