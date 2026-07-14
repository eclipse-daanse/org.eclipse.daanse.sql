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

import java.sql.ResultSet;
import java.sql.SQLException;

import org.eclipse.daanse.sql.model.type.BestFitColumnType;

/**
 * Reads a single column from a {@link ResultSet} as the right Java type for a
 * {@link BestFitColumnType}, returning {@code null} for SQL {@code NULL}. Mirrors the
 * type→getter logic used by the ROLAP {@code SqlStatement} accessors.
 */
final class ColumnAccessors {

    private ColumnAccessors() {
    }

    /**
     * @param rs          the result set, positioned on a row
     * @param oneBasedCol the 1-based JDBC column index
     * @param type        the expected type, or {@code null} to fall back to {@code getObject}
     */
    static Object read(ResultSet rs, int oneBasedCol, BestFitColumnType type) throws SQLException {
        if (type == null) {
            return rs.getObject(oneBasedCol);
        }
        return switch (type) {
            case STRING -> rs.getString(oneBasedCol);
            case INT -> {
                int v = rs.getInt(oneBasedCol);
                yield rs.wasNull() ? null : v;
            }
            case LONG -> {
                long v = rs.getLong(oneBasedCol);
                yield rs.wasNull() ? null : v;
            }
            case DOUBLE -> {
                double v = rs.getDouble(oneBasedCol);
                yield rs.wasNull() ? null : v;
            }
            case DECIMAL -> rs.getBigDecimal(oneBasedCol);
            case OBJECT -> rs.getObject(oneBasedCol);
        };
    }
}
