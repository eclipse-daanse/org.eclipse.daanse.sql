/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.h2.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.junit.jupiter.api.Test;

/**
 * H2 takes the SQL-99 default verbatim for table/column/index/constraint/view
 * rename — no overrides in {@link H2Dialect} at all. Sequence rename is
 * pinned off: H2 has no {@code ALTER SEQUENCE ... RENAME TO}, so this locks
 * the flag at false rather than leaving it to be flipped on without checking.
 */
class H2AlterRenameOfflineTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "PUBLIC");
    private static final TableReference T = new TableReference(Optional.of(S), "EMPLOYEES", TableReference.TYPE_TABLE);
    private static final TableReference V = new TableReference(Optional.of(S), "V_EMP", TableReference.TYPE_VIEW);

    private final H2Dialect dialect = new H2Dialect();

    @Test
    void renameTable_uses_ANSI_default() {
        assertThat(dialect.ddlGenerator().renameTable(T, "STAFF"))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" RENAME TO \"STAFF\"");
    }

    @Test
    void renameColumn_uses_ANSI_default() {
        assertThat(dialect.ddlGenerator().renameColumn(T, "OLD", "NEW"))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" RENAME COLUMN \"OLD\" TO \"NEW\"");
    }

    @Test
    void renameIndex_uses_ANSI_default() {
        assertThat(dialect.ddlGenerator().renameIndex("IDX_OLD", "IDX_NEW", T))
                .isEqualTo("ALTER INDEX \"IDX_OLD\" RENAME TO \"IDX_NEW\"");
    }

    @Test
    void renameConstraint_uses_ANSI_default() {
        assertThat(dialect.ddlGenerator().renameConstraint(T, "OLD_FK", "NEW_FK"))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" RENAME CONSTRAINT \"OLD_FK\" TO \"NEW_FK\"");
    }

    @Test
    void renameView_uses_ANSI_default() {
        assertThat(dialect.ddlGenerator().renameView(V, "V_STAFF"))
                .isEqualTo("ALTER VIEW \"PUBLIC\".\"V_EMP\" RENAME TO \"V_STAFF\"");
    }

    @Test
    void renameSequence_pinned_off_no_ALTER_SEQUENCE_RENAME_in_H2() {
        assertThat(dialect.ddlGenerator().supportsRenameSequence()).isFalse();
        assertThat(dialect.ddlGenerator().renameSequence("PUBLIC", "SEQ_EMP", "SEQ_STAFF")).isEmpty();
    }
}
