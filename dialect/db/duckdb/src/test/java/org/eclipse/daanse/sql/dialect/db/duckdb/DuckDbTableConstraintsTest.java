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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Test;

/**
 * DuckDB cannot add a check to an existing table, nor a unique constraint once another table refers
 * to it; both are declared in {@code CREATE TABLE}, the same clauses {@code ALTER TABLE ... ADD} uses
 * elsewhere.
 */
class DuckDbTableConstraintsTest {

    private static final TableReference CUSTOMER = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "main")), "CUSTOMER");

    private final DdlGenerator ddl = new DuckDbDialect().ddlGenerator();

    @Test
    void uniqueAndCheckCannotBeAddedLater() {
        assertThat(ddl.supportsAddUniqueConstraint()).isFalse();
        assertThat(ddl.supportsAddCheckConstraint()).isFalse();
    }

    @Test
    void uniqueAndCheckAreClausesOfCreateTable() {
        assertThat(ddl.uniqueConstraintClause("UC_EMAIL", List.of("EMAIL")))
                .isEqualTo("CONSTRAINT \"UC_EMAIL\" UNIQUE (\"EMAIL\")");
        assertThat(ddl.checkConstraintClause("CK_ID", "\"ID\" > 0"))
                .isEqualTo("CONSTRAINT \"CK_ID\" CHECK (\"ID\" > 0)");
    }

    @Test
    void theAlterTableFormIsBuiltFromTheClause() {
        assertThat(ddl.addUniqueConstraint(CUSTOMER, "UC_EMAIL", List.of("EMAIL")))
                .endsWith(" ADD " + ddl.uniqueConstraintClause("UC_EMAIL", List.of("EMAIL")))
                .startsWith("ALTER TABLE ");
        assertThat(ddl.addCheckConstraint(CUSTOMER, "CK_ID", "\"ID\" > 0"))
                .endsWith(" ADD " + ddl.checkConstraintClause("CK_ID", "\"ID\" > 0")).startsWith("ALTER TABLE ");
    }
}
