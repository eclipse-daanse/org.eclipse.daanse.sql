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

import org.eclipse.daanse.sql.dialect.api.Dialect;

/**
 * Default DDL string emission ({@code TRUNCATE}, {@code DROP TABLE},
 * {@code CREATE/DROP SCHEMA}). Calls back to the dialect for identifier quoting
 * and feature-detection flags.
 */
final class JdbcDdlEmitter {

    private final Dialect dialect;

    JdbcDdlEmitter(Dialect dialect) {
        this.dialect = dialect;
    }

    String clearTable(String schemaName, String tableName) {
        return new StringBuilder("TRUNCATE TABLE ").append(dialect.quoteIdentifier(schemaName, tableName)).toString();
    }

    String dropTable(String schemaName, String tableName, boolean ifExists) {
        StringBuilder sb = new StringBuilder("DROP TABLE ");
        if (ifExists && dialect.supportsDropTableIfExists()) {
            sb.append("IF EXISTS ");
        }
        return sb.append(dialect.quoteIdentifier(schemaName, tableName)).toString();
    }

    String createSchema(String schemaName, boolean ifNotExists) {
        StringBuilder sb = new StringBuilder("CREATE SCHEMA ");
        if (ifNotExists) {
            sb.append("IF NOT EXISTS ");
        }
        return sb.append(dialect.quoteIdentifier(schemaName)).toString();
    }

    String dropSchema(String schemaName, boolean ifExists, boolean cascade) {
        StringBuilder sb = new StringBuilder("DROP SCHEMA ");
        if (ifExists && dialect.supportsDropSchemaIfExists()) {
            sb.append("IF EXISTS ");
        }
        sb.append(dialect.quoteIdentifier(schemaName));
        if (dialect.requiresDropSchemaRestrict()) {
            sb.append(" RESTRICT");
        } else if (cascade && dialect.supportsDropTableCascade()) {
            sb.append(" CASCADE");
        }
        return sb.toString();
    }
}
