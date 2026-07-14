/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.junit.jupiter.api.Test;

class JdbcDialectTypeMapTest {

    private final AbstractJdbcDialect dialect = new AbstractJdbcDialect(DialectInitData.ansiDefaults()) {
        @Override
        public String name() {
            return null;
        }
    };

    @Test
    void testNumericPrecisionFiveScaleZeroMapsToInt() throws SQLException {
        ResultSetMetaData rs = mock(ResultSetMetaData.class);
        when(rs.getColumnType(1)).thenReturn(Types.NUMERIC);
        when(rs.getPrecision(1)).thenReturn(5);
        when(rs.getScale(1)).thenReturn(0);
        assertEquals(BestFitColumnType.INT, dialect.getType(rs, 0));
    }

    @Test
    void testDecimalPrecisionFiveScaleZeroMapsToInt() throws SQLException {
        ResultSetMetaData rs = mock(ResultSetMetaData.class);
        when(rs.getColumnType(1)).thenReturn(Types.DECIMAL);
        when(rs.getPrecision(1)).thenReturn(5);
        when(rs.getScale(1)).thenReturn(0);
        assertEquals(BestFitColumnType.INT, dialect.getType(rs, 0));
    }
}
