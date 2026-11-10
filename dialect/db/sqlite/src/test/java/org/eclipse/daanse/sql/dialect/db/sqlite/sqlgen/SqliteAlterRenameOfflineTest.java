/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.sqlite.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.db.sqlite.SqliteDialect;
import org.junit.jupiter.api.Test;

/**
 * SQLite gained {@code ALTER TABLE ... RENAME TO} and
 * {@code ALTER TABLE ... RENAME COLUMN} in 3.25 — the SQL-99 default already
 * renders both correctly, so no override is needed. Index, constraint and
 * view rename have no SQLite equivalent at all.
 */
class SqliteAlterRenameOfflineTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "PUBLIC");
    private static final TableReference T = new TableReference(Optional.of(S), "EMPLOYEES", TableReference.TYPE_TABLE);
    private static final TableReference V = new TableReference(Optional.of(S), "V_EMP", TableReference.TYPE_VIEW);

    private final SqliteDialect dialect = new SqliteDialect();

    @Test
    void renameTable_inherits_ANSI_default() {
        assertThat(dialect.ddlGenerator().supportsRenameTable()).isTrue();
        assertThat(dialect.ddlGenerator().renameTable(T, "STAFF"))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" RENAME TO \"STAFF\"");
    }

    @Test
    void renameColumn_inherits_ANSI_default() {
        assertThat(dialect.ddlGenerator().supportsRenameColumn()).isTrue();
        assertThat(dialect.ddlGenerator().renameColumn(T, "OLD", "NEW"))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" RENAME COLUMN \"OLD\" TO \"NEW\"");
    }

    @Test
    void renameIndex_unsupported() {
        assertThat(dialect.ddlGenerator().supportsRenameIndex()).isFalse();
        assertThat(dialect.ddlGenerator().renameIndex("IDX_OLD", "IDX_NEW", T)).isNull();
    }

    @Test
    void renameConstraint_unsupported() {
        assertThat(dialect.ddlGenerator().supportsRenameConstraint()).isFalse();
        assertThat(dialect.ddlGenerator().renameConstraint(T, "OLD_FK", "NEW_FK")).isNull();
    }

    @Test
    void renameView_unsupported() {
        assertThat(dialect.ddlGenerator().supportsRenameView()).isFalse();
        assertThat(dialect.ddlGenerator().renameView(V, "V_STAFF")).isNull();
    }
}
