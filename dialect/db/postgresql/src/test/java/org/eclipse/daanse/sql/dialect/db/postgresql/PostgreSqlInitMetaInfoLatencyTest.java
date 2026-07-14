/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.postgresql;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * {@code createMetaInfo} vs lightweight dialect-init read latency on PostgreSQL
 * 15.
 */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class PostgreSqlInitMetaInfoLatencyTest {

    private static final String DATABASE = "bench";
    private static final String USER = "postgres";
    private static final String PASSWORD = "secret";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:15")
            .withEnv("POSTGRES_PASSWORD", PASSWORD).withEnv("POSTGRES_DB", DATABASE).withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private static String jdbcUrl;
    private final DatabaseService service = new DatabaseServiceImpl();

    @BeforeAll
    static void setUp() throws Exception {
        POSTGRES.start();
        Class.forName("org.postgresql.Driver");
        jdbcUrl = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + DATABASE;
    }

    @Test
    void measure() throws SQLException {
        DataSource ds = ds();
        System.out.println();
        System.out.println("=== PostgreSQL 15: createMetaInfo() vs lightweight dialect-init reads ===");
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
                return DriverManager.getConnection(jdbcUrl, USER, PASSWORD);
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
        try (Connection c = DriverManager.getConnection(jdbcUrl, USER, PASSWORD); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA public CASCADE");
            s.execute("CREATE SCHEMA public");
        }
    }

    private static void populate(int n) throws SQLException {
        if (n == 0)
            return;
        try (Connection c = DriverManager.getConnection(jdbcUrl, USER, PASSWORD); Statement s = c.createStatement()) {
            for (int i = 0; i < n; i++) {
                s.execute("CREATE TABLE t_" + i + " (id INT PRIMARY KEY, name VARCHAR(50),"
                        + " val NUMERIC(12,3), birthday DATE, created TIMESTAMP)");
                if (i > 0) {
                    s.execute("ALTER TABLE t_" + i + " ADD CONSTRAINT fk_" + i + " FOREIGN KEY (id) REFERENCES t_"
                            + (i - 1) + "(id)");
                }
                s.execute("CREATE INDEX idx_" + i + "_name ON t_" + i + "(name)");
            }
        }
    }
}
