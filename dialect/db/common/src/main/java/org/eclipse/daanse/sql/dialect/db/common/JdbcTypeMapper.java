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

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.daanse.sql.model.type.BestFitColumnType;

/**
 * Default JDBC SQL-type → {@link BestFitColumnType} mapping. NUMERIC/DECIMAL
 * adapt to precision/scale; everything else looks up {@link #DEFAULT_TYPE_MAP}.
 */
final class JdbcTypeMapper {

    static final Map<Integer, BestFitColumnType> DEFAULT_TYPE_MAP;

    static {
        Map<Integer, BestFitColumnType> m = new HashMap<>();
        m.put(Types.SMALLINT, BestFitColumnType.INT);
        m.put(Types.INTEGER, BestFitColumnType.INT);
        m.put(Types.BOOLEAN, BestFitColumnType.INT);
        m.put(Types.DOUBLE, BestFitColumnType.DOUBLE);
        m.put(Types.FLOAT, BestFitColumnType.DOUBLE);
        m.put(Types.BIGINT, BestFitColumnType.DOUBLE);
        DEFAULT_TYPE_MAP = Collections.unmodifiableMap(m);
    }

    private JdbcTypeMapper() {
        // static helpers only
    }

    static BestFitColumnType resolveType(ResultSetMetaData metaData, int columnIndex) throws SQLException {
        final int columnType = metaData.getColumnType(columnIndex + 1);
        BestFitColumnType internalType = null;
        if (columnType != Types.NUMERIC && columnType != Types.DECIMAL) {
            internalType = DEFAULT_TYPE_MAP.get(columnType);
        } else {
            final int precision = metaData.getPrecision(columnIndex + 1);
            final int scale = metaData.getScale(columnIndex + 1);
            if (scale == 0 && precision <= 9) {
                internalType = BestFitColumnType.INT;
            } else {
                internalType = BestFitColumnType.DOUBLE;
            }
        }
        return internalType == null ? BestFitColumnType.OBJECT : internalType;
    }
}
