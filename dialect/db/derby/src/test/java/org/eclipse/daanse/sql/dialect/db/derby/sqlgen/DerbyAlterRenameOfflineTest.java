/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.derby.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.db.derby.DerbyDialect;
import org.junit.jupiter.api.Test;

/**
 * Derby spells table/column/index rename with its own statement shapes
 * ({@code RENAME TABLE}, {@code RENAME COLUMN table.col TO new},
 * {@code RENAME INDEX}) rather than the SQL-99 {@code ALTER ... RENAME ...}
 * default, and has no constraint/view/trigger/sequence rename at all.
 */
class DerbyAlterRenameOfflineTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "PUBLIC");
    private static final TableReference T = new TableReference(Optional.of(S), "EMPLOYEES", TableReference.TYPE_TABLE);
    private static final TableReference V = new TableReference(Optional.of(S), "V_EMP", TableReference.TYPE_VIEW);

    private final DerbyDialect dialect = new DerbyDialect();

    @Test
    void renameTable_uses_RENAME_TABLE() {
        assertThat(dialect.ddlGenerator().renameTable(T, "STAFF"))
                .isEqualTo("RENAME TABLE \"PUBLIC\".\"EMPLOYEES\" TO \"STAFF\"");
    }

    @Test
    void renameColumn_uses_RENAME_COLUMN_with_qualified_table() {
        assertThat(dialect.ddlGenerator().renameColumn(T, "OLD", "NEW"))
                .isEqualTo("RENAME COLUMN \"PUBLIC\".\"EMPLOYEES\".\"OLD\" TO \"NEW\"");
    }

    @Test
    void renameIndex_uses_RENAME_INDEX_unqualified() {
        assertThat(dialect.ddlGenerator().renameIndex("IDX_OLD", "IDX_NEW", T))
                .isEqualTo("RENAME INDEX \"IDX_OLD\" TO \"IDX_NEW\"");
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

    @Test
    void renameTrigger_unsupported() {
        assertThat(dialect.ddlGenerator().supportsRenameTrigger()).isFalse();
        assertThat(dialect.ddlGenerator().renameTrigger("TRG_AUDIT", T, "TRG_LOG")).isNull();
    }

    @Test
    void renameSequence_unsupported() {
        assertThat(dialect.ddlGenerator().supportsRenameSequence()).isFalse();
        assertThat(dialect.ddlGenerator().renameSequence("PUBLIC", "SEQ_EMP", "SEQ_STAFF")).isEmpty();
    }
}
