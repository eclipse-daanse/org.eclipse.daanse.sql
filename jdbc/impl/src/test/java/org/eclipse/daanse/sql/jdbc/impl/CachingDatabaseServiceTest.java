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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CachingDatabaseServiceTest {

    private Connection h2KeepAlive;
    private DataSource dataSource;

    private DataSource freshH2() throws Exception {
        // Per-test isolated database. DB_CLOSE_DELAY=-1 + a held Connection
        // keeps the in-memory schema alive across createMetaInfo calls.
        String url = "jdbc:h2:mem:cache_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1";
        h2KeepAlive = DriverManager.getConnection(url, "sa", "");
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(url);
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (h2KeepAlive != null && !h2KeepAlive.isClosed()) h2KeepAlive.close();
    }

    @Test
    void cache_returns_same_instance_within_ttl() throws Exception {
        dataSource = freshH2();
        CachingDatabaseService svc = new CachingDatabaseService(Duration.ofMinutes(1));

        MetaInfo first = svc.createMetaInfo(dataSource);
        MetaInfo second = svc.createMetaInfo(dataSource);

        assertThat(second).as("within TTL the cache returns the same instance").isSameAs(first);
        assertThat(svc.approximateSize()).isEqualTo(1);
    }

    @Test
    void cache_recomputes_after_expiry() throws Exception {
        dataSource = freshH2();
        // Tiny TTL to force re-computation on the second call.
        CachingDatabaseService svc = new CachingDatabaseService(Duration.ofMillis(50));

        MetaInfo first = svc.createMetaInfo(dataSource);
        Thread.sleep(120);
        MetaInfo second = svc.createMetaInfo(dataSource);

        assertThat(second).as("after TTL elapsed the cache returns a fresh snapshot").isNotSameAs(first);
    }

    @Test
    void invalidate_evicts_entry() throws Exception {
        dataSource = freshH2();
        CachingDatabaseService svc = new CachingDatabaseService(Duration.ofMinutes(1));

        MetaInfo first = svc.createMetaInfo(dataSource);
        svc.invalidate(dataSource);
        MetaInfo second = svc.createMetaInfo(dataSource);

        assertThat(second).as("post-invalidate the cache recomputes").isNotSameAs(first);
    }

    @Test
    void invalidateAll_clears_cache() throws Exception {
        dataSource = freshH2();
        CachingDatabaseService svc = new CachingDatabaseService(Duration.ofMinutes(1));

        svc.createMetaInfo(dataSource);
        assertThat(svc.approximateSize()).isEqualTo(1);
        svc.invalidateAll();
        assertThat(svc.approximateSize()).isZero();
    }

    @Test
    void connection_path_does_not_cache() throws Exception {
        dataSource = freshH2();
        CachingDatabaseService svc = new CachingDatabaseService(Duration.ofMinutes(1));

        try (Connection c = dataSource.getConnection()) {
            svc.createMetaInfo(c);
        }
        assertThat(svc.approximateSize())
                .as("connection-keyed snapshots bypass the cache")
                .isZero();
    }

    @Test
    void rejects_zero_or_negative_ttl() {
        assertThatThrownBy(() -> new CachingDatabaseService(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CachingDatabaseService(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
