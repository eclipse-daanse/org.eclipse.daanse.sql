/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mysql.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.JDBCType;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.TableRename;
import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.junit.jupiter.api.Test;

/**
 * MySQL uses {@code MODIFY COLUMN} and table-scoped {@code RENAME INDEX}; no
 * constraint rename.
 */
class MySqlAlterRenameOfflineTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "appdb");
    private static final TableReference T = new TableReference(Optional.of(S), "EMPLOYEES", TableReference.TYPE_TABLE);
    private static final TableReference V = new TableReference(Optional.of(S), "V_EMP", TableReference.TYPE_VIEW);
    private static final TableReference TBL_A = new TableReference(Optional.of(S), "A", TableReference.TYPE_TABLE);
    private static final TableReference TBL_B = new TableReference(Optional.of(S), "B", TableReference.TYPE_TABLE);
    private static final TableReference TBL_TMP = new TableReference(Optional.of(S), "TMP", TableReference.TYPE_TABLE);

    private final MySqlDialect dialect = new MySqlDialect();

    /** MySQL 8.0+ — the ANSI {@code RENAME COLUMN}/{@code RENAME TABLE} path. */
    private final MySqlDialect dialect80 = new MySqlDialect(
            DialectInitData.ansiDefaults().withQuoteIdentifierString("`").withVersion(8, 0));

    /** Pre-8.0 MySQL — the {@code CHANGE}-based rename fallback. */
    private final MySqlDialect dialect57 = new MySqlDialect(
            DialectInitData.ansiDefaults().withQuoteIdentifierString("`").withVersion(5, 7));

    private static ColumnMetaData meta(JDBCType jdbc, OptionalInt size, ColumnMetaData.Nullability n) {
        return new ColumnMetaDataRecord(jdbc, jdbc.getName(), size, OptionalInt.empty(), OptionalInt.empty(), n,
                OptionalInt.empty(), Optional.empty(), Optional.empty(), ColumnMetaData.AutoIncrement.UNKNOWN,
                ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    @Test
    void alterColumnType_uses_MODIFY_COLUMN() {
        assertThat(dialect.ddlGenerator().alterColumnType(T, "SALARY",
                meta(JDBCType.DECIMAL, OptionalInt.of(12), ColumnMetaData.Nullability.NULLABLE)))
                .startsWith("ALTER TABLE `appdb`.`EMPLOYEES` MODIFY COLUMN `SALARY` ").doesNotContain(" TYPE ");
    }

    @Test
    void alterColumnSetNullability_typeFree_throws() {
        assertThatThrownBy(() -> dialect.ddlGenerator().alterColumnSetNullability(T, "EMAIL", false))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void alterColumnSetNullability_typeAware_restates_type() {
        ColumnMetaData m = meta(JDBCType.VARCHAR, OptionalInt.of(100), ColumnMetaData.Nullability.NULLABLE);
        assertThat(dialect.ddlGenerator().alterColumnSetNullability(T, "EMAIL", false, m))
                .startsWith("ALTER TABLE `appdb`.`EMPLOYEES` MODIFY COLUMN `EMAIL` ").endsWith(" NOT NULL")
                .contains("VARCHAR(100)");
    }

    @Test
    void renameIndex_uses_ALTER_TABLE_RENAME_INDEX() {
        assertThat(dialect.ddlGenerator().renameIndex("IDX_OLD", "IDX_NEW", T))
                .isEqualTo("ALTER TABLE `appdb`.`EMPLOYEES` RENAME INDEX `IDX_OLD` TO `IDX_NEW`");
    }

    @Test
    void renameConstraint_returns_null() {
        assertThat(dialect.ddlGenerator().supportsRenameConstraint()).isFalse();
        assertThat(dialect.ddlGenerator().renameConstraint(T, "OLD_FK", "NEW_FK")).isNull();
    }

    @Test
    void renameColumn_and_renameTable_inherit_ANSI_form() {
        assertThat(dialect.ddlGenerator().renameColumn(T, "OLD", "NEW"))
                .isEqualTo("ALTER TABLE `appdb`.`EMPLOYEES` RENAME COLUMN `OLD` TO `NEW`");
        assertThat(dialect.ddlGenerator().renameTable(T, "STAFF"))
                .isEqualTo("ALTER TABLE `appdb`.`EMPLOYEES` RENAME TO `STAFF`");
    }

    @Test
    void renameView_uses_RENAME_TABLE() {
        assertThat(dialect.ddlGenerator().renameView(V, "V_STAFF"))
                .isEqualTo("RENAME TABLE `appdb`.`V_EMP` TO `V_STAFF`");
    }

    @Test
    void renameTables_swap_emits_one_atomic_RENAME_TABLE_statement() {
        assertThat(dialect.ddlGenerator().supportsAtomicMultiRenameTable()).isTrue();
        assertThat(dialect.ddlGenerator().renameTables(List.of(
                new TableRename(TBL_A, "TMP"),
                new TableRename(TBL_B, "A"),
                new TableRename(TBL_TMP, "B"))))
                .containsExactly(
                        "RENAME TABLE `appdb`.`A` TO `TMP`, `appdb`.`B` TO `A`, `appdb`.`TMP` TO `B`");
    }

    @Test
    void renameColumn_8_0_uses_ANSI_RENAME_COLUMN() {
        assertThat(dialect80.ddlGenerator().renameColumn(T, "OLD", "NEW"))
                .isEqualTo("ALTER TABLE `appdb`.`EMPLOYEES` RENAME COLUMN `OLD` TO `NEW`");
    }

    @Test
    void renameColumn_pre_8_0_without_metadata_returns_null_but_capability_stays_true() {
        assertThat(dialect57.ddlGenerator().supportsRenameColumn()).isTrue();
        assertThat(dialect57.ddlGenerator().renameColumn(T, "OLD", "NEW")).isNull();
    }

    @Test
    void renameColumn_pre_8_0_with_metadata_uses_CHANGE() {
        ColumnMetaData m = meta(JDBCType.DECIMAL, OptionalInt.of(12), ColumnMetaData.Nullability.NO_NULLS);
        assertThat(dialect57.ddlGenerator().renameColumn(T, "OLD", "NEW", m))
                .isEqualTo("ALTER TABLE `appdb`.`EMPLOYEES` CHANGE `OLD` `NEW` DECIMAL(12) NOT NULL");
    }
}
