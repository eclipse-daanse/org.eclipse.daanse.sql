/*
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * For more information please visit the Project: Hitachi Vantara - Mondrian
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial adapt parts of Syntax.class
 *   Stefan Bischof (bipolis.org) - initial
 */

package org.eclipse.daanse.sql.dialect.db.mariadb;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;

public class MariaDBDialect extends MySqlDialect {

    private static final String SUPPORTED_PRODUCT_NAME = "MARIADB";

    private volatile org.eclipse.daanse.sql.dialect.api.generator.ReturningGenerator cachedReturningGenerator;

    /** JDBC-free constructor for SQL generation. */
    public MariaDBDialect() {
        super();
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public MariaDBDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    @Override
    public boolean supportsSequences() {
        return true;
    }

    /**
     * MariaDB accepts {@code IF NOT EXISTS} on {@code CREATE INDEX} (MySQL parent
     * disables).
     */
    @Override
    public boolean supportsCreateIndexIfNotExists() {
        return true;
    }

    /**
     * MariaDB accepts {@code IF EXISTS} on {@code DROP INDEX} (MySQL parent
     * disables).
     */
    @Override
    public boolean supportsDropIndexIfExists() {
        return true;
    }

    @Override
    public boolean supportsDropConstraintIfExists() {
        return dialectVersion.isUnknownOrAtLeast(10, 5);
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.generator.ReturningGenerator returningGenerator() {
        var local = cachedReturningGenerator;
        if (local != null)
            return local;
        if (!dialectVersion.isUnknownOrAtLeast(10, 5)) {
            local = super.returningGenerator();
            cachedReturningGenerator = local;
            return local;
        }
        local = new org.eclipse.daanse.sql.dialect.api.generator.ReturningGenerator() {
            @Override
            public boolean supportsReturning() {
                return true;
            }

            @Override
            public java.util.Optional<String> returning(java.util.List<String> columns) {
                if (columns == null || columns.isEmpty())
                    return java.util.Optional.empty();
                if (columns.size() == 1 && "*".equals(columns.get(0))) {
                    return java.util.Optional.of(" RETURNING *");
                }
                StringBuilder sb = new StringBuilder(" RETURNING ");
                boolean first = true;
                for (String c : columns) {
                    if (!first)
                        sb.append(", ");
                    sb.append(quoteIdentifier(c));
                    first = false;
                }
                return java.util.Optional.of(sb.toString());
            }
        };
        cachedReturningGenerator = local;
        return local;
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }

    @Override
    public org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding caseFolding() {
        return org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding.PRESERVE;
    }
}
