/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.postgresql.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.junit.jupiter.api.Test;

class PostgreSqlAlterRenameOfflineTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "PUBLIC");
    private static final TableReference T = new TableReference(Optional.of(S), "EMPLOYEES", TableReference.TYPE_TABLE);

    private final PostgreSqlDialect dialect = new PostgreSqlDialect();

    private static ColumnMetaData meta(JDBCType jdbc, OptionalInt size) {
        return new ColumnMetaDataRecord(jdbc, jdbc.getName(), size, OptionalInt.empty(), OptionalInt.empty(),
                ColumnMetaData.Nullability.NULLABLE, OptionalInt.empty(), Optional.empty(), Optional.empty(),
                ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    @Test
    void alterColumnType_emits_ansi_TYPE_form() {
        assertThat(dialect.ddlGenerator().alterColumnType(T, "SALARY", meta(JDBCType.DECIMAL, OptionalInt.of(12))))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" ALTER COLUMN \"SALARY\" TYPE DECIMAL(12)");
    }

    @Test
    void alterColumnSetNullability_uses_SET_NOT_NULL() {
        assertThat(dialect.ddlGenerator().alterColumnSetNullability(T, "EMAIL", false))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" ALTER COLUMN \"EMAIL\" SET NOT NULL");
        assertThat(dialect.ddlGenerator().alterColumnSetNullability(T, "EMAIL", true))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" ALTER COLUMN \"EMAIL\" DROP NOT NULL");
    }

    @Test
    void alterColumnSetDefault_emits_SET_DEFAULT() {
        assertThat(dialect.ddlGenerator().alterColumnSetDefault(T, "REGION", "'EU'"))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" ALTER COLUMN \"REGION\" SET DEFAULT 'EU'");
    }

    @Test
    void alterColumnDropDefault_emits_DROP_DEFAULT() {
        assertThat(dialect.ddlGenerator().alterColumnDropDefault(T, "REGION"))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" ALTER COLUMN \"REGION\" DROP DEFAULT");
    }

    @Test
    void renameColumn_emits_ANSI_form() {
        assertThat(dialect.ddlGenerator().renameColumn(T, "OLD", "NEW"))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" RENAME COLUMN \"OLD\" TO \"NEW\"");
    }

    @Test
    void renameTable_emits_RENAME_TO() {
        assertThat(dialect.ddlGenerator().renameTable(T, "STAFF"))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" RENAME TO \"STAFF\"");
    }

    @Test
    void renameIndex_emits_ALTER_INDEX() {
        assertThat(dialect.ddlGenerator().renameIndex("IDX_OLD", "IDX_NEW", T))
                .isEqualTo("ALTER INDEX \"IDX_OLD\" RENAME TO \"IDX_NEW\"");
    }

    @Test
    void renameConstraint_emits_RENAME_CONSTRAINT() {
        assertThat(dialect.ddlGenerator().renameConstraint(T, "OLD_FK", "NEW_FK"))
                .isEqualTo("ALTER TABLE \"PUBLIC\".\"EMPLOYEES\" RENAME CONSTRAINT \"OLD_FK\" TO \"NEW_FK\"");
    }
}
