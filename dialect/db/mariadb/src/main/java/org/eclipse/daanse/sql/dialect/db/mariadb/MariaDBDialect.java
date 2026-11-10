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

import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.model.schema.TableReference;

public class MariaDBDialect extends MySqlDialect {

    private static final String SUPPORTED_PRODUCT_NAME = "MARIADB";

    /**
     * {@code TINYINT(1)}.
     *
     * <p>
     * MariaDB reads BOOLEAN as an alias for TINYINT(1); saying so outright keeps
     * the emitted DDL the same as what the server stores.
     */
    @Override
    public String booleanTypeName() {
        return "TINYINT(1)";
    }

    /**
     * {@code DATETIME}.
     *
     * <p>
     * MariaDB's TIMESTAMP only reaches from 1970 to 2038, like MySQL's. DATETIME
     * spans years 1000 to 9999.
     */
    @Override
    public String timestampTypeName() {
        return "DATETIME";
    }

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

    /**
     * MariaDB's native {@code RENAME COLUMN} landed in 10.5.2 — not comparable to
     * MySQL's inherited 8.0 threshold, since MariaDB's own 10.x/11.x numbering
     * would always satisfy {@code isUnknownOrAtLeast(8, 0)}. Below 10.5,
     * {@link #renameColumn(TableReference, String, String)} falls back to null.
     */
    @Override
    protected boolean supportsNativeRenameColumn() {
        return dialectVersion.isUnknownOrAtLeast(10, 5);
    }

    @Override
    public boolean supportsRenameSequence() {
        return dialectVersion.isUnknownOrAtLeast(10, 5);
    }

    /**
     * MariaDB ≥ 10.5.2: {@code ALTER TABLE "schema"."seq" RENAME TO "new"} —
     * sequences live in the table namespace, so there is no
     * {@code ALTER SEQUENCE ... RENAME TO} (verified live).
     */
    @Override
    public java.util.Optional<String> renameSequence(String schemaName, String name, String newName) {
        if (!supportsSequences() || !supportsRenameSequence()) {
            return java.util.Optional.empty();
        }
        String qualified = schemaName != null && !schemaName.isBlank() ? quoteIdentifier(schemaName, name)
                : quoteIdentifier(name);
        return java.util.Optional.of("ALTER TABLE " + qualified + " RENAME TO " + quoteIdentifier(newName));
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
