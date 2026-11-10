/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.jdbc.impl;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class DialectInitMetaInfoLatencyTest {

    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURE_ROUNDS = 5;

    private final DatabaseService service = new DatabaseServiceImpl();

    @Test
    void measure() throws SQLException {
        System.out.println();
        System.out.println("=== createMetaInfo() vs lightweight dialect-init reads ===");
        System.out.printf("%-30s %15s %15s %10s%n", "scenario", "createMetaInfo", "dialect-init", "ratio");

        for (int n : new int[] { 0, 10, 100, 500, 1000 }) {
            DataSource ds = freshDb();
            populate(ds, n);

            // warm
            for (int i = 0; i < WARMUP_ROUNDS; i++) {
                service.createMetaInfo(ds);
                lightweightDialectInit(ds);
            }

            long meta = bestOf(MEASURE_ROUNDS, () -> service.createMetaInfo(ds));
            long light = bestOf(MEASURE_ROUNDS, () -> lightweightDialectInit(ds));

            System.out.printf("%-30s %12.3f ms %12.3f ms %8.1fx%n",
                    n + " tables", meta / 1_000_000.0, light / 1_000_000.0,
                    light == 0 ? 0.0 : (double) meta / light);
        }
        System.out.println();
    }

    /** Reads only what {@code AbstractJdbcDialect(Connection)} consults. */
    private static void lightweightDialectInit(DataSource ds) throws SQLException {
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
            // supportedResultSetConcurrency matrix — same as MetaInfo path
            for (int t : new int[] { java.sql.ResultSet.TYPE_FORWARD_ONLY,
                    java.sql.ResultSet.TYPE_SCROLL_INSENSITIVE,
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
            if (t < best) best = t;
        }
        return best;
    }

    private DataSource freshDb() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:bench" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static void populate(DataSource ds, int tableCount) throws SQLException {
        if (tableCount == 0) return;
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            for (int i = 0; i < tableCount; i++) {
                s.execute("CREATE TABLE T_" + i
                        + " (ID INT PRIMARY KEY, NAME VARCHAR(50), VAL DECIMAL(12,3),"
                        + " BIRTHDAY DATE, CREATED TIMESTAMP)");
                if (i > 0) {
                    s.execute("ALTER TABLE T_" + i
                            + " ADD CONSTRAINT FK_" + i
                            + " FOREIGN KEY (ID) REFERENCES T_" + (i - 1) + "(ID)");
                }
                s.execute("CREATE INDEX IDX_" + i + "_NAME ON T_" + i + "(NAME)");
            }
            c.commit();
        }
    }

    /** Independent sanity test — confirms MetaInfo loads at all on a fresh DB. */
    @Test
    void smokeTest_metaInfoNotEmpty() throws SQLException {
        MetaInfo info = service.createMetaInfo(freshDb());
        if (info == null) throw new AssertionError("MetaInfo was null");
    }
}
