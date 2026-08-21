/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.clickhouse.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.TableRename;
import org.eclipse.daanse.sql.dialect.db.clickhouse.ClickHouseDialect;
import org.junit.jupiter.api.Test;

/**
 * ClickHouse has no {@code ALTER TABLE ... RENAME TO} — table and view rename
 * both go through the standalone {@code RENAME TABLE} statement, which also
 * batches multiple pairs into one atomic statement. There is no b-tree index
 * DDL and no constraint rename; column rename is the SQL-99 default.
 */
class ClickHouseAlterRenameOfflineTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "PUBLIC");
    private static final TableReference T = new TableReference(Optional.of(S), "EMPLOYEES", TableReference.TYPE_TABLE);
    private static final TableReference V = new TableReference(Optional.of(S), "V_EMP", TableReference.TYPE_VIEW);
    private static final TableReference TBL_A = new TableReference(Optional.of(S), "A", TableReference.TYPE_TABLE);
    private static final TableReference TBL_B = new TableReference(Optional.of(S), "B", TableReference.TYPE_TABLE);

    private final ClickHouseDialect dialect = new ClickHouseDialect();

    @Test
    void renameTable_uses_RENAME_TABLE() {
        assertThat(dialect.ddlGenerator().renameTable(T, "STAFF"))
                .isEqualTo("RENAME TABLE \"PUBLIC\".\"EMPLOYEES\" TO \"STAFF\"");
    }

    @Test
    void renameView_uses_the_same_RENAME_TABLE_form() {
        assertThat(dialect.ddlGenerator().renameView(V, "V_STAFF"))
                .isEqualTo("RENAME TABLE \"PUBLIC\".\"V_EMP\" TO \"V_STAFF\"");
    }

    @Test
    void renameTables_emits_one_atomic_RENAME_TABLE_statement() {
        assertThat(dialect.ddlGenerator().supportsAtomicMultiRenameTable()).isTrue();
        assertThat(dialect.ddlGenerator().renameTables(List.of(
                new TableRename(TBL_A, "B"),
                new TableRename(TBL_B, "C"))))
                .containsExactly("RENAME TABLE \"PUBLIC\".\"A\" TO \"B\", \"PUBLIC\".\"B\" TO \"C\"");
    }

    @Test
    void renameColumn_inherits_ANSI_default() {
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
}
