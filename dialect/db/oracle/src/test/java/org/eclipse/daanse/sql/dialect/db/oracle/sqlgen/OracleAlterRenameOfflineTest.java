/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.oracle.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.junit.jupiter.api.Test;

/**
 * Oracle uses {@code MODIFY (...)} for column changes; renames are SQL-99
 * (default).
 */
class OracleAlterRenameOfflineTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "HR");
    private static final TableReference T = new TableReference(Optional.of(S), "EMPLOYEES", TableReference.TYPE_TABLE);

    private final OracleDialect dialect = new OracleDialect();

    private static ColumnMetaData meta(JDBCType jdbc, OptionalInt size) {
        return new ColumnMetaDataRecord(jdbc, jdbc.getName(), size, OptionalInt.empty(), OptionalInt.empty(),
                ColumnMetaData.Nullability.NULLABLE, OptionalInt.empty(), Optional.empty(), Optional.empty(),
                ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    @Test
    void alterColumnType_uses_MODIFY() {
        assertThat(dialect.ddlGenerator().alterColumnType(T, "SALARY", meta(JDBCType.DECIMAL, OptionalInt.of(12))))
                .startsWith("ALTER TABLE \"HR\".\"EMPLOYEES\" MODIFY (\"SALARY\" ").endsWith(")");
    }

    @Test
    void alterColumnSetNullability_uses_MODIFY() {
        assertThat(dialect.ddlGenerator().alterColumnSetNullability(T, "EMAIL", false))
                .isEqualTo("ALTER TABLE \"HR\".\"EMPLOYEES\" MODIFY (\"EMAIL\" NOT NULL)");
        assertThat(dialect.ddlGenerator().alterColumnSetNullability(T, "EMAIL", true))
                .isEqualTo("ALTER TABLE \"HR\".\"EMPLOYEES\" MODIFY (\"EMAIL\" NULL)");
    }

    @Test
    void alterColumnSetDefault_uses_MODIFY_DEFAULT() {
        assertThat(dialect.ddlGenerator().alterColumnSetDefault(T, "REGION", "'EU'"))
                .isEqualTo("ALTER TABLE \"HR\".\"EMPLOYEES\" MODIFY (\"REGION\" DEFAULT 'EU')");
    }

    @Test
    void alterColumnDropDefault_emits_MODIFY_DEFAULT_NULL() {
        assertThat(dialect.ddlGenerator().alterColumnDropDefault(T, "REGION"))
                .isEqualTo("ALTER TABLE \"HR\".\"EMPLOYEES\" MODIFY (\"REGION\" DEFAULT NULL)");
    }

    @Test
    void renames_inherit_ANSI_default() {
        assertThat(dialect.ddlGenerator().renameColumn(T, "OLD", "NEW"))
                .isEqualTo("ALTER TABLE \"HR\".\"EMPLOYEES\" RENAME COLUMN \"OLD\" TO \"NEW\"");
        assertThat(dialect.ddlGenerator().renameTable(T, "STAFF"))
                .isEqualTo("ALTER TABLE \"HR\".\"EMPLOYEES\" RENAME TO \"STAFF\"");
        assertThat(dialect.ddlGenerator().renameIndex("IDX_OLD", "IDX_NEW", T))
                .isEqualTo("ALTER INDEX \"IDX_OLD\" RENAME TO \"IDX_NEW\"");
        assertThat(dialect.ddlGenerator().renameConstraint(T, "OLD_FK", "NEW_FK"))
                .isEqualTo("ALTER TABLE \"HR\".\"EMPLOYEES\" RENAME CONSTRAINT \"OLD_FK\" TO \"NEW_FK\"");
    }
}
