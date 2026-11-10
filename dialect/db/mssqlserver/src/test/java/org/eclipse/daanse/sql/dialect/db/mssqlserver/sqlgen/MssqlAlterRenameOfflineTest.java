/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mssqlserver.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.junit.jupiter.api.Test;

class MssqlAlterRenameOfflineTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "dbo");
    private static final TableReference T = new TableReference(Optional.of(S), "EMPLOYEES", TableReference.TYPE_TABLE);

    private final MicrosoftSqlServerDialect dialect = new MicrosoftSqlServerDialect();

    private static ColumnMetaData meta(JDBCType jdbc, OptionalInt size, ColumnMetaData.Nullability n) {
        return new ColumnMetaDataRecord(jdbc, jdbc.getName(), size, OptionalInt.empty(), OptionalInt.empty(), n,
                OptionalInt.empty(), Optional.empty(), Optional.empty(), ColumnMetaData.AutoIncrement.UNKNOWN,
                ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    @Test
    void alterColumnType_omits_TYPE_keyword() {
        String sql = dialect.ddlGenerator().alterColumnType(T, "SALARY",
                meta(JDBCType.DECIMAL, OptionalInt.of(12), ColumnMetaData.Nullability.NULLABLE));
        assertThat(sql).startsWith("ALTER TABLE ");
        assertThat(sql).contains("EMPLOYEES");
        assertThat(sql).contains("ALTER COLUMN");
        assertThat(sql).contains("SALARY");
        assertThat(sql).contains("DECIMAL(12)");
        assertThat(sql).doesNotContain(" TYPE ");
    }

    @Test
    void alterColumnSetNullability_typeFree_throws() {
        assertThatThrownBy(() -> dialect.ddlGenerator().alterColumnSetNullability(T, "EMAIL", false))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("requires the column type");
    }

    @Test
    void alterColumnSetNullability_typeAware_restates_type() {
        ColumnMetaData m = meta(JDBCType.VARCHAR, OptionalInt.of(100), ColumnMetaData.Nullability.NULLABLE);
        String setNotNull = dialect.ddlGenerator().alterColumnSetNullability(T, "EMAIL", false, m);
        assertThat(setNotNull).contains("ALTER COLUMN");
        assertThat(setNotNull).contains("EMAIL");
        assertThat(setNotNull).contains("VARCHAR(100)");
        assertThat(setNotNull).endsWith(" NOT NULL");

        String dropNotNull = dialect.ddlGenerator().alterColumnSetNullability(T, "EMAIL", true, m);
        assertThat(dropNotNull).endsWith(" NULL");
    }

    @Test
    void alterColumnSetDefault_creates_named_constraint() {
        String sql = dialect.ddlGenerator().alterColumnSetDefault(T, "REGION", "'EU'");
        assertThat(sql).contains("ADD CONSTRAINT");
        assertThat(sql).contains("DF_EMPLOYEES_REGION");
        assertThat(sql).contains("DEFAULT ('EU')");
        assertThat(sql).contains("FOR ");
        assertThat(sql).contains("REGION");
    }

    @Test
    void alterColumnDropDefault_drops_assumed_constraint() {
        String sql = dialect.ddlGenerator().alterColumnDropDefault(T, "REGION");
        assertThat(sql).contains("DROP CONSTRAINT");
        assertThat(sql).contains("DF_EMPLOYEES_REGION");
    }

    @Test
    void renameColumn_uses_sp_rename_with_COLUMN() {
        String sql = dialect.ddlGenerator().renameColumn(T, "OLD", "NEW");
        assertThat(sql).startsWith("EXEC sp_rename '");
        assertThat(sql).contains("EMPLOYEES");
        assertThat(sql).contains("OLD");
        assertThat(sql).endsWith(", 'NEW', 'COLUMN'");
    }

    @Test
    void renameTable_uses_sp_rename() {
        String sql = dialect.ddlGenerator().renameTable(T, "STAFF");
        assertThat(sql).startsWith("EXEC sp_rename '");
        assertThat(sql).contains("EMPLOYEES");
        assertThat(sql).endsWith(", 'STAFF'");
    }

    @Test
    void renameIndex_uses_sp_rename_with_INDEX() {
        String sql = dialect.ddlGenerator().renameIndex("IDX_OLD", "IDX_NEW", T);
        assertThat(sql).startsWith("EXEC sp_rename '");
        assertThat(sql).contains("EMPLOYEES");
        assertThat(sql).contains("IDX_OLD");
        assertThat(sql).endsWith(", 'IDX_NEW', 'INDEX'");
    }

    @Test
    void renameConstraint_uses_sp_rename_with_OBJECT() {
        String sql = dialect.ddlGenerator().renameConstraint(T, "OLD_FK", "NEW_FK");
        assertThat(sql).startsWith("EXEC sp_rename '");
        assertThat(sql).contains("OLD_FK");
        assertThat(sql).endsWith(", 'NEW_FK', 'OBJECT'");
    }
}
