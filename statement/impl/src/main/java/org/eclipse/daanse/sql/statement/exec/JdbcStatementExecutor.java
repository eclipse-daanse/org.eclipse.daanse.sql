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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.sql.statement.api.render.BoundParameter;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.api.exec.StatementExecutionException;
import org.eclipse.daanse.sql.statement.api.exec.StatementExecutor;
import org.eclipse.daanse.sql.statement.api.result.RowMapper;

/**
 * JDBC-backed {@link StatementExecutor} over a single {@link Connection}. The connection is
 * not owned (callers manage its lifecycle); statements and result sets are closed per call.
 */
public final class JdbcStatementExecutor implements StatementExecutor {

    private final Connection connection;

    public JdbcStatementExecutor(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public <T> List<T> query(RenderedSql sql, RowMapper<T> mapper) {
        try (PreparedStatement ps = connection.prepareStatement(sql.sql())) {
            bind(ps, sql.parameters());
            try (ResultSet rs = ps.executeQuery()) {
                JdbcRow row = new JdbcRow(rs, sql.columnTypes());
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(row));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new StatementExecutionException("query failed: " + sql.sql(), e);
        }
    }

    @Override
    public int update(RenderedSql sql) {
        try (PreparedStatement ps = connection.prepareStatement(sql.sql())) {
            bind(ps, sql.parameters());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new StatementExecutionException("update failed: " + sql.sql(), e);
        }
    }

    @Override
    public int[] batch(RenderedSql sql, List<Object[]> rows) {
        int n = sql.parameters().size();
        try (PreparedStatement ps = connection.prepareStatement(sql.sql())) {
            for (Object[] row : rows) {
                if (row.length != n) {
                    throw new StatementExecutionException(
                            "batch row has " + row.length + " values but statement has " + n + " parameters", null);
                }
                for (int i = 0; i < n; i++) {
                    ps.setObject(i + 1, row[i]);
                }
                ps.addBatch();
            }
            return ps.executeBatch();
        } catch (SQLException e) {
            throw new StatementExecutionException("batch failed: " + sql.sql(), e);
        }
    }

    /** Binds immediate parameter values; unbound markers must be supplied via {@link #batch}. */
    private static void bind(PreparedStatement ps, List<BoundParameter> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            BoundParameter p = parameters.get(i);
            if (!p.bound()) {
                throw new StatementExecutionException(
                        "parameter " + (i + 1) + " is an unbound marker; use batch(...) instead", null);
            }
            ps.setObject(i + 1, p.value());
        }
    }
}
