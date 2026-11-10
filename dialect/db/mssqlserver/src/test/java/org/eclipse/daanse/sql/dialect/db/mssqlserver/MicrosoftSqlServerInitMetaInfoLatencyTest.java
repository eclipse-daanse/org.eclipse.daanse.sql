/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mssqlserver;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@code createMetaInfo} vs lightweight dialect-init read latency on SQL Server
 * 2022.
 */
@Testcontainers
class MicrosoftSqlServerInitMetaInfoLatencyTest {

    @Container
    @SuppressWarnings("resource")
    static final MSSQLServerContainer<?> CONTAINER = new MSSQLServerContainer<>(
            "mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    private static String jdbcUrl;
    private static String user;
    private static String password;
    private final DatabaseService service = new DatabaseServiceImpl();

    @Test
    void measure() throws SQLException {
        jdbcUrl = CONTAINER.getJdbcUrl();
        user = CONTAINER.getUsername();
        password = CONTAINER.getPassword();
        DataSource ds = ds();
        System.out.println();
        System.out.println("=== SQL Server 2022: createMetaInfo() vs lightweight dialect-init reads ===");
        System.out.printf("%-30s %15s %15s %10s%n", "scenario", "createMetaInfo", "dialect-init", "ratio");

        for (int n : new int[] { 0, 10, 100, 500 }) {
            resetSchema();
            populate(n);
            for (int i = 0; i < 2; i++) {
                service.createMetaInfo(ds);
                lightweight(ds);
            }
            long meta = bestOf(3, () -> service.createMetaInfo(ds));
            long light = bestOf(3, () -> lightweight(ds));
            System.out.printf("%-30s %12.3f ms %12.3f ms %8.1fx%n", n + " tables", meta / 1_000_000.0,
                    light / 1_000_000.0, light == 0 ? 0.0 : (double) meta / light);
        }
        System.out.println();
    }

    private DataSource ds() {
        return new javax.sql.DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(jdbcUrl, user, password);
            }

            @Override
            public Connection getConnection(String u, String p) throws SQLException {
                return DriverManager.getConnection(jdbcUrl, u, p);
            }

            @Override
            public java.io.PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(java.io.PrintWriter w) {
            }

            @Override
            public void setLoginTimeout(int s) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public java.util.logging.Logger getParentLogger() {
                return null;
            }

            @Override
            public <T> T unwrap(Class<T> i) {
                return null;
            }

            @Override
            public boolean isWrapperFor(Class<?> i) {
                return false;
            }
        };
    }

    private static void lightweight(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            md.getIdentifierQuoteString();
            md.getDatabaseProductName();
            md.getDatabaseProductVersion();
            md.getDatabaseMajorVersion();
            md.getDatabaseMinorVersion();
            md.isReadOnly();
            md.getMaxColumnNameLength();
            md.getSQLKeywords();
            for (int t : new int[] { java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.TYPE_SCROLL_INSENSITIVE,
                    java.sql.ResultSet.TYPE_SCROLL_SENSITIVE }) {
                for (int conc : new int[] { java.sql.ResultSet.CONCUR_READ_ONLY,
                        java.sql.ResultSet.CONCUR_UPDATABLE }) {
                    md.supportsResultSetConcurrency(t, conc);
                }
            }
        }
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    private static long bestOf(int rounds, SqlRunnable r) throws SQLException {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < rounds; i++) {
            long t0 = System.nanoTime();
            r.run();
            long t = System.nanoTime() - t0;
            if (t < best)
                best = t;
        }
        return best;
    }

    private static void resetSchema() throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl, user, password); Statement s = c.createStatement()) {
            // Drop all FKs first, then iterate and drop tables explicitly
            java.util.List<String> fks = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = c.createStatement()
                    .executeQuery("SELECT 'ALTER TABLE [' + OBJECT_SCHEMA_NAME(parent_object_id) + '].[' "
                            + " + OBJECT_NAME(parent_object_id) + '] DROP CONSTRAINT [' + name + ']'"
                            + " FROM sys.foreign_keys")) {
                while (rs.next())
                    fks.add(rs.getString(1));
            }
            for (String sql : fks)
                s.execute(sql);

            java.util.List<String> tables = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = c.createStatement().executeQuery("SELECT name FROM sys.tables")) {
                while (rs.next())
                    tables.add(rs.getString(1));
            }
            for (String t : tables)
                s.execute("DROP TABLE [" + t + "]");
        }
    }

    private static void populate(int n) throws SQLException {
        if (n == 0)
            return;
        try (Connection c = DriverManager.getConnection(jdbcUrl, user, password); Statement s = c.createStatement()) {
            for (int i = 0; i < n; i++) {
                s.execute("CREATE TABLE T_" + i + " (ID INT PRIMARY KEY, NAME NVARCHAR(50),"
                        + " VAL DECIMAL(12,3), BIRTHDAY DATE, CREATED DATETIME2)");
                if (i > 0) {
                    s.execute("ALTER TABLE T_" + i + " ADD CONSTRAINT FK_" + i + " FOREIGN KEY (ID) REFERENCES T_"
                            + (i - 1) + "(ID)");
                }
                s.execute("CREATE INDEX IDX_" + i + "_NAME ON T_" + i + "(NAME)");
            }
        }
    }
}
