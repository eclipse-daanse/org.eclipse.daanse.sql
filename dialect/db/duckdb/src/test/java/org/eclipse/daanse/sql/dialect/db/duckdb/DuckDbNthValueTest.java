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
 */
package org.eclipse.daanse.sql.dialect.db.duckdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The n-th value is an aggregate over the rows of the group -- an ordered list and its n-th element -- not the
 * window NTH_VALUE ... OVER, which in a grouped query runs over the groups (V-82).
 */
class DuckDbNthValueTest {

    @Test
    void theNthValueIsTheNthElementOfTheGroupsList() {
        assertEquals("list_extract(list(\"VALUE\") FILTER (WHERE \"VALUE\" IS NOT NULL), 2)",
                new DuckDbDialect().generateNthValueAgg("\"VALUE\"", true, 2, List.of()).orElseThrow());
        assertEquals("list_extract(list(\"VALUE\"), 1)",
                new DuckDbDialect().generateNthValueAgg("\"VALUE\"", false, 0, List.of()).orElseThrow());
    }
}
