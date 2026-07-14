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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class DialectInitDataLatencyTest {

    @Test
    void fromDataSource_under_50ms_on_H2() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:bench" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        // Warm-up: amortise H2 first-connection setup, JIT, etc.
        for (int i = 0; i < 3; i++) {
            DialectInitData.fromDataSource(ds);
        }

        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long t0 = System.nanoTime();
            DialectInitData data = DialectInitData.fromDataSource(ds);
            long t = System.nanoTime() - t0;
            if (t < best) best = t;
            assertThat(data).isNotNull();
            assertThat(data.productName()).isEqualTo("H2");
        }
        long bestMs = best / 1_000_000L;
        assertThat(bestMs)
                .as("DialectInitData.fromDataSource(H2 in-mem) best-of-5 should be well under 50 ms (was %d ms)", bestMs)
                .isLessThan(50L);
        // Also assert the snapshot has the expected shape.
        DialectInitData data = DialectInitData.fromDataSource(ds);
        assertThat(data.quoteIdentifierString()).isEqualTo("\"");
        assertThat(data.databaseMajorVersion()).isPositive();
        assertThat(data.sqlKeywordsLower()).isNotEmpty(); // H2 reports its own keywords
    }
}
