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
package org.eclipse.daanse.sql.statement.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.junit.jupiter.api.Test;

/** A temporal column is read with its getter, whatever class the driver's getObject would hand over. */
class ColumnAccessorsTest {

    @Test
    void aTimestampIsReadAsATimestamp() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        Timestamp timestamp = Timestamp.valueOf("2005-05-29 00:00:00");
        when(rs.getObject(1)).thenReturn(new Object() {
            @Override
            public String toString() {
                return "a driver's own timestamp class";
            }
        });
        when(rs.getTimestamp(1)).thenReturn(timestamp);
        assertEquals(timestamp, ColumnAccessors.read(rs, 1, BestFitColumnType.TIMESTAMP));
    }

    @Test
    void aDateIsReadAsADate() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        Date date = Date.valueOf("2008-04-29");
        when(rs.getObject(1)).thenReturn(date.toLocalDate());
        when(rs.getDate(1)).thenReturn(date);
        assertEquals(date, ColumnAccessors.read(rs, 1, BestFitColumnType.DATE));
    }
}
