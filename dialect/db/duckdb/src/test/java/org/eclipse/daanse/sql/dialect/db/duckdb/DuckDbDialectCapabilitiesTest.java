/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.duckdb;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DuckDbDialectCapabilitiesTest {

    @Test
    void drillthroughMaxRowsMustRenderAsLimit() {
        // duckdb_jdbc silently ignores Statement.setMaxRows, so the drill-through
        // row limit must be inlined as LIMIT n (same treatment as ClickHouse).
        assertTrue(new DuckDbDialect().requiresDrillthroughMaxRowsInLimit());
    }
}
