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
package org.eclipse.daanse.sql.statement.api.result;

import java.math.BigDecimal;

import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;

/**
 * A read-only view of one result row, decoupled from {@code java.sql}.
 * <p>
 * Columns are addressed positionally — most naturally by the
 * {@link ProjectionRef} handle returned by the builder at projection time, so
 * the same handle used to build the query is used to read it back. Values are
 * already converted according to the projection's column type. The typed
 * convenience getters return boxed values that are {@code null} for SQL
 * {@code NULL}.
 * <p>
 * A {@code Row} is only valid during a single {@link RowMapper#map(Row)} call;
 * do not retain it.
 */
public interface Row {

    /** @return the number of columns */
    int columnCount();

    /**
     * @param columnIndex zero-based column position (aligns with
     *                    {@link ProjectionRef#ordinal()})
     */
    Object get(int columnIndex);

    /** @param columnLabel the column label/alias as exposed by the result */
    Object get(String columnLabel);

    /** Reads the column the handle refers to. */
    default Object get(ProjectionRef ref) {
        return get(ref.ordinal());
    }

    default String getString(int columnIndex) {
        Object v = get(columnIndex);
        return v == null ? null : v.toString();
    }

    default String getString(ProjectionRef ref) {
        return getString(ref.ordinal());
    }

    default Integer getInt(int columnIndex) {
        Object v = get(columnIndex);
        return v == null ? null : ((Number) v).intValue();
    }

    default Integer getInt(ProjectionRef ref) {
        return getInt(ref.ordinal());
    }

    default Long getLong(int columnIndex) {
        Object v = get(columnIndex);
        return v == null ? null : ((Number) v).longValue();
    }

    default Long getLong(ProjectionRef ref) {
        return getLong(ref.ordinal());
    }

    default Double getDouble(int columnIndex) {
        Object v = get(columnIndex);
        return v == null ? null : ((Number) v).doubleValue();
    }

    default Double getDouble(ProjectionRef ref) {
        return getDouble(ref.ordinal());
    }

    default BigDecimal getDecimal(int columnIndex) {
        Object v = get(columnIndex);
        if (v == null) {
            return null;
        }
        return v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }

    default BigDecimal getDecimal(ProjectionRef ref) {
        return getDecimal(ref.ordinal());
    }
}
