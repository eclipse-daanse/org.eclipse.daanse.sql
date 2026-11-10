/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.common;

import java.sql.Connection;

public class AnsiDialect extends AbstractJdbcDialect {

    private static final String DIALECT_NAME = "ansi";

    /**
     * Construct with a JDBC connection — derives quoting, max name length, etc.
     * from metadata.
     */
    public AnsiDialect(Connection connection) {
        super(initDataFor(connection));
    }

    /** JDBC-free constructor — uses ANSI double-quote and SQL-99 defaults. */
    public AnsiDialect() {
        super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults());
    }

    /** Construct from a captured snapshot. Preferred path. */
    public AnsiDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    private static org.eclipse.daanse.sql.dialect.api.DialectInitData initDataFor(Connection c) {
        try {
            return org.eclipse.daanse.sql.dialect.api.DialectInitData.fromConnection(c);
        } catch (java.sql.SQLException e) {
            return org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults();
        }
    }

    @Override
    public String name() {
        return DIALECT_NAME;
    }
}
