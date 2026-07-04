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
import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.sql.statement.api.exec.StatementExecutionException;
import org.eclipse.daanse.sql.statement.api.result.Row;

/**
 * A {@link Row} backed by a {@link ResultSet} cursor. One instance is reused per result and
 * always reflects the result set's current row; the type list (from the rendered query)
 * drives typed reads, with {@code java.sql} metadata as the fallback.
 */
final class JdbcRow implements Row {

    private final ResultSet resultSet;
    private final List<BestFitColumnType> columnTypes;

    JdbcRow(ResultSet resultSet, List<BestFitColumnType> columnTypes) {
        this.resultSet = resultSet;
        this.columnTypes = columnTypes;
    }

    @Override
    public int columnCount() {
        try {
            return resultSet.getMetaData().getColumnCount();
        } catch (SQLException e) {
            throw new StatementExecutionException("could not read result metadata", e);
        }
    }

    @Override
    public Object get(int columnIndex) {
        BestFitColumnType type = columnIndex >= 0 && columnIndex < columnTypes.size() ? columnTypes.get(columnIndex)
                : null;
        try {
            return ColumnAccessors.read(resultSet, columnIndex + 1, type);
        } catch (SQLException e) {
            throw new StatementExecutionException("could not read column " + columnIndex, e);
        }
    }

    @Override
    public Object get(String columnLabel) {
        try {
            return ColumnAccessors.read(resultSet, resultSet.findColumn(columnLabel), null);
        } catch (SQLException e) {
            throw new StatementExecutionException("could not read column '" + columnLabel + "'", e);
        }
    }
}
