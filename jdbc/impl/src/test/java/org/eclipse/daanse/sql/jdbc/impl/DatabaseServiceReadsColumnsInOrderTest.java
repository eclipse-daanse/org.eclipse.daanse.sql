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
*   Stefan Bischof (bipolis.org) - initial
*/
package org.eclipse.daanse.sql.jdbc.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

/**
 * The column metadata is read left to right, as JDBC asks of a portable reader. Oracle returns
 * {@code COLUMN_DEF} (column 13 of {@code getColumns}) as a LONG stream, which a read of any later column of the row
 * consumes: reading it afterwards fails with "ORA-17027: Stream has already been closed". H2 stands in here behind a
 * result set that keeps that rule.
 */
class DatabaseServiceReadsColumnsInOrderTest {

    @Test
    void theColumnDefaultIsReadBeforeTheColumnsAfterIt() throws SQLException {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (Connection c = h2.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE T (ID INTEGER NOT NULL, LABEL VARCHAR(20) DEFAULT 'none')");
        }

        MetaInfo metaInfo = new DatabaseServiceImpl().createMetaInfo(columnDefaultAsLong(h2));

        assertThat(metaInfo.structureInfo().columns())
                .filteredOn(column -> "LABEL".equals(column.column().name()))
                .singleElement()
                .extracting(ColumnDefinition::columnMetaData)
                .satisfies(meta -> assertThat(meta.columnDefault()).hasValueSatisfying(
                        value -> assertThat(value).contains("none")));
    }

    /** The data source, its getColumns result sets keeping Oracle's rule for the LONG {@code COLUMN_DEF}. */
    private static DataSource columnDefaultAsLong(DataSource dataSource) {
        return proxy(DataSource.class, dataSource, (method, result) -> "getConnection".equals(method)
                ? proxy(Connection.class, (Connection) result, (m, r) -> "getMetaData".equals(m)
                        ? proxy(DatabaseMetaData.class, (DatabaseMetaData) r,
                                (mm, rr) -> "getColumns".equals(mm) ? longColumnDefault((ResultSet) rr) : rr)
                        : r)
                : result);
    }

    private static ResultSet longColumnDefault(ResultSet rows) throws SQLException {
        int columnDefault = rows.findColumn("COLUMN_DEF");
        int[] furthest = { 0 };
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] { ResultSet.class },
                (proxy, method, args) -> {
                    if ("next".equals(method.getName())) {
                        furthest[0] = 0;
                    } else if (method.getName().startsWith("get") && args != null && args.length == 1
                            && (args[0] instanceof String || args[0] instanceof Integer)) {
                        int column = args[0] instanceof String label ? rows.findColumn(label) : (Integer) args[0];
                        if (column == columnDefault && furthest[0] > columnDefault) {
                            throw new SQLException("ORA-17027: Stream has already been closed");
                        }
                        furthest[0] = Math.max(furthest[0], column);
                    }
                    return invoke(rows, method, args);
                });
    }

    private interface Wrap {
        Object apply(String method, Object result) throws SQLException;
    }

    private static <T> T proxy(Class<T> type, T target, Wrap wrap) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> wrap.apply(method.getName(), invoke(target, method, args))));
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
