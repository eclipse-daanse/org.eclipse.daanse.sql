/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.deparser.api;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

/**
 * Dialect-aware SQL deparsers.
 */
public interface DialectDeparser {

    /**
     * Deparses a SQL statement using the given dialect.
     *
     * @param statement the JSqlParser statement to deparse
     * @param dialect   the database dialect to use for SQL generation
     * @return the generated SQL string
     */
    String deparse(net.sf.jsqlparser.statement.Statement statement, Dialect dialect);
}
