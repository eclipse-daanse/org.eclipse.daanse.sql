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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.statement.api.exec.StatementExecutionException;
import org.eclipse.daanse.sql.statement.api.exec.StatementExecutor;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.api.result.RowMapper;

/**
 * A {@link StatementExecutor} backed by a {@link DataSource}: each call borrows a fresh
 * {@link Connection} and closes it afterwards (auto-commit — one transaction per call).
 * <p>
 * To run several statements in a single transaction, use {@link #inTransaction(Function)}:
 * it borrows one connection, disables auto-commit, hands a connection-bound executor to the
 * unit of work, then commits on success or rolls back on any exception.
 */
public final class DataSourceStatementExecutor implements StatementExecutor {

    private final DataSource dataSource;

    public DataSourceStatementExecutor(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public <T> List<T> query(RenderedSql sql, RowMapper<T> mapper) {
        try (Connection connection = dataSource.getConnection()) {
            return new JdbcStatementExecutor(connection).query(sql, mapper);
        } catch (SQLException e) {
            throw new StatementExecutionException("query failed: " + sql.sql(), e);
        }
    }

    @Override
    public int update(RenderedSql sql) {
        try (Connection connection = dataSource.getConnection()) {
            return new JdbcStatementExecutor(connection).update(sql);
        } catch (SQLException e) {
            throw new StatementExecutionException("update failed: " + sql.sql(), e);
        }
    }

    @Override
    public int[] batch(RenderedSql sql, List<Object[]> rows) {
        try (Connection connection = dataSource.getConnection()) {
            return new JdbcStatementExecutor(connection).batch(sql, rows);
        } catch (SQLException e) {
            throw new StatementExecutionException("batch failed: " + sql.sql(), e);
        }
    }

    /**
     * Runs a unit of work in a single transaction. The {@code work} receives a
     * {@link StatementExecutor} bound to one connection; its result is returned after
     * {@code commit}. Any exception triggers a {@code rollback} and is rethrown.
     *
     * @param work the transactional unit of work (may return {@code null} for void work)
     * @return the work's result
     */
    public <T> T inTransaction(Function<StatementExecutor, T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(new JdbcStatementExecutor(connection));
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new StatementExecutionException("transaction failed", e);
        }
    }
}
