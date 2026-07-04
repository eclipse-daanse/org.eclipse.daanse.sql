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
package org.eclipse.daanse.sql.statement.api.exec;

import java.util.List;

import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.api.result.RowMapper;

/**
 * Executes already-rendered SQL and maps result rows. This is the adaptable
 * seam: the JDBC implementation is {@code JdbcStatementExecutor}, but other
 * backends (a test double, a remote service, a cached source) can implement the
 * same contract.
 */
public interface StatementExecutor {

    /**
     * Runs a query and maps each row.
     *
     * @param sql    the rendered query (carries the SQL text and column types)
     * @param mapper maps each {@link org.eclipse.daanse.sql.statement.api.result.Row}
     *               to a {@code T}
     * @return the mapped rows, in result order
     */
    <T> List<T> query(RenderedSql sql, RowMapper<T> mapper);

    /**
     * Runs a write statement (INSERT/UPDATE/DELETE/DDL).
     *
     * @param sql the rendered statement (its bound parameters, if any, are applied)
     * @return the affected-row count
     */
    int update(RenderedSql sql);

    /**
     * Runs a parameterized write statement once per value-row as a JDBC batch. The
     * statement should be rendered with parameter markers (see
     * {@code Expressions.paramMarker}); each {@code Object[]} supplies the values
     * for one row, in placeholder order.
     *
     * @param sql  the rendered statement (its placeholder count must match each
     *             row's length)
     * @param rows the per-row parameter values
     * @return the per-row affected-row counts (as from
     *         {@link java.sql.PreparedStatement#executeBatch()})
     */
    int[] batch(RenderedSql sql, List<Object[]> rows);
}
