/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.dialect.api.type;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.eclipse.daanse.sql.model.type.BestFitColumnType;

public interface TypeMapper {

    /**
     * @return the best Java type for reading this column
     * @throws SQLException if metadata cannot be retrieved
     */
    BestFitColumnType getType(ResultSetMetaData metaData, int columnIndex) throws SQLException;

    /**
     * @param valueString the string representation of the value
     * @return true if an exponent suffix is needed
     */
    boolean needsExponent(Object value, String valueString);

    /**
     * @param type        ResultSet type (e.g., TYPE_FORWARD_ONLY,
     *                    TYPE_SCROLL_INSENSITIVE)
     * @param concurrency ResultSet concurrency (e.g., CONCUR_READ_ONLY,
     *                    CONCUR_UPDATABLE)
     * @return true if the combination is supported
     */
    boolean supportsResultSetConcurrency(int type, int concurrency);
}
