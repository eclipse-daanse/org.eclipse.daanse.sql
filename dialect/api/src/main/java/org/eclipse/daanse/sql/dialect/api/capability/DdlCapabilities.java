/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.api.capability;

/**
 * @param supportsDdl                whether DDL is permitted at all
 * @param dropTableCascade           {@code DROP TABLE … CASCADE}
 * @param sequences                  {@code CREATE/DROP SEQUENCE} +
 *                                   {@code NEXT VALUE FOR}
 * @param dropIndexRequiresTable     {@code DROP INDEX … ON table}
 * @param createTableIfNotExists     {@code CREATE TABLE IF NOT EXISTS}
 * @param createIndexIfNotExists     {@code CREATE INDEX IF NOT EXISTS}
 * @param dropIndexIfExists          {@code DROP INDEX IF EXISTS}
 * @param createOrReplaceView        {@code CREATE OR REPLACE VIEW}
 * @param createOrReplaceTrigger     {@code CREATE OR REPLACE TRIGGER}
 * @param dropViewIfExists           {@code DROP VIEW IF EXISTS}
 * @param dropConstraintIfExists     {@code ALTER TABLE … DROP CONSTRAINT IF EXISTS}
 * @param dropTableIfExists          {@code DROP TABLE IF EXISTS}
 * @param dropSchemaIfExists         {@code DROP SCHEMA IF EXISTS}
 * @param requiresDropSchemaRestrict {@code DROP SCHEMA name RESTRICT} required
 * @param maxColumnNameLength        driver-reported column-name limit
 */
public record DdlCapabilities(boolean supportsDdl, boolean dropTableCascade, boolean sequences,
        boolean dropIndexRequiresTable, boolean createTableIfNotExists, boolean createIndexIfNotExists,
        boolean dropIndexIfExists, boolean createOrReplaceView, boolean createOrReplaceTrigger,
        boolean dropViewIfExists, boolean dropConstraintIfExists, boolean dropTableIfExists, boolean dropSchemaIfExists,
        boolean requiresDropSchemaRestrict, int maxColumnNameLength) {

    /** All supported, no special requirements — default for modern engines. */
    public static DdlCapabilities full() {
        return new DdlCapabilities(true, true, true, false, true, true, true, true, true, true, true, true, true, false,
                128);
    }

    /** Most conservative — DDL allowed but no convenience clauses. */
    public static DdlCapabilities minimal() {
        return new DdlCapabilities(true, false, false, true, false, false, false, false, false, false, false, false,
                false, true, 30);
    }
}
