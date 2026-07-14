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

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;

public final class CachingDatabaseService extends DatabaseServiceImpl {

    private final Duration ttl;
    private final Map<DataSource, Entry> cache = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private record Entry(MetaInfo info, Instant expiresAt) {}

    /**
     * @param ttl how long each snapshot stays valid; entries past their
     *            expiry are recomputed on next access
     */
    public CachingDatabaseService(Duration ttl) {
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be > 0: " + ttl);
        }
    }

    @Override
    public MetaInfo createMetaInfo(DataSource dataSource) throws SQLException {
        Entry e = cache.get(dataSource);
        Instant now = Instant.now();
        if (e != null && now.isBefore(e.expiresAt)) {
            return e.info;
        }
        MetaInfo info = super.createMetaInfo(dataSource);
        cache.put(dataSource, new Entry(info, now.plus(ttl)));
        return info;
    }

    // createMetaInfo(Connection): inherited unchanged — connections from a
    // pool aren't stable cache keys.

    // createMetaInfo(DataSource, MetadataProvider) and
    // createMetaInfo(Connection, MetadataProvider): inherited unchanged —
    // provider-customized snapshots bypass the cache because the same
    // DataSource can yield different MetaInfo depending on the provider.

    /** Force-evict all cached snapshots. */
    public void invalidateAll() {
        cache.clear();
    }

    /** Force-evict the snapshot for {@code dataSource} if cached. */
    public void invalidate(DataSource dataSource) {
        cache.remove(dataSource);
    }

    /** Best-effort entry count for diagnostics. May undercount under concurrent eviction. */
    public int approximateSize() {
        return cache.size();
    }
}
