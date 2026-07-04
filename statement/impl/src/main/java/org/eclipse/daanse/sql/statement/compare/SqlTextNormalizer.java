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
package org.eclipse.daanse.sql.statement.compare;

/** Small SQL-text utilities shared by the query-mapping layer and its dual-run tests. */
public final class SqlTextNormalizer {

    private SqlTextNormalizer() {
    }

    /**
     * Collapse every run of whitespace to a single space and trim. Used to compare two SQL
     * strings for equivalence when one may be single-line and the other formatted onto multiple
     * indented lines.
     */
    public static String normalizeWhitespace(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
