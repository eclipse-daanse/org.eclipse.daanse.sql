/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.JDBCType;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.IdentifierQuotingPolicy;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.junit.jupiter.api.Test;

class MySqlQuotingPolicyTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "TEST");
    private static final TableReference EMP = new TableReference(Optional.of(S), "EMPLOYEES",
            TableReference.TYPE_TABLE);

    private MySqlDialect dialectNever() {
        MySqlDialect d = new MySqlDialect();
        d.setQuotingPolicy(IdentifierQuotingPolicy.NEVER);
        return d;
    }

    private static ColumnMetaData intColumn() {
        return new ColumnMetaDataRecord(JDBCType.INTEGER, "INT", OptionalInt.of(10), OptionalInt.empty(),
                OptionalInt.empty(), ColumnMetaData.Nullability.NO_NULLS, OptionalInt.empty(), Optional.empty(),
                Optional.empty(), ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    @Test
    void alterColumnType_unquoted() {
        assertThat(dialectNever().ddlGenerator().alterColumnType(EMP, "EMAIL", intColumn())).doesNotContain("`")
                .contains("EMPLOYEES").contains("EMAIL");
    }

    @Test
    void alterColumnSetNullability_with_meta_unquoted() {
        // MySQL requires the 4-arg overload (needs current type for MODIFY).
        assertThat(dialectNever().ddlGenerator().alterColumnSetNullability(EMP, "EMAIL", false, intColumn()))
                .doesNotContain("`").contains("EMPLOYEES").contains("EMAIL");
    }

    @Test
    void renameIndex_unquoted() {
        assertThat(dialectNever().ddlGenerator().renameIndex("IDX_OLD", "IDX_NEW", EMP)).doesNotContain("`")
                .contains("EMPLOYEES").contains("IDX_OLD").contains("IDX_NEW");
    }

    @Test
    void createTriggerProcedure_unquoted() {
        assertThat(
                dialectNever().ddlGenerator().createTriggerProcedure("AUDIT_PROC", "TEST", "BEGIN END").orElseThrow())
                .doesNotContain("`").contains("AUDIT_PROC").contains("TEST");
    }

    @Test
    void dropProcedure_unquoted() {
        assertThat(dialectNever().ddlGenerator().dropProcedure("AUDIT_PROC", "TEST", true).orElseThrow())
                .doesNotContain("`").contains("AUDIT_PROC").contains("TEST");
    }

    @Test
    void primaryKey_unquoted() {
        assertThat(dialectNever().ddlGenerator().addPrimaryKeyConstraint(EMP, "PK_EMP", List.of("ID")))
                .doesNotContain("`").contains("EMPLOYEES").contains("PK_EMP").contains("ID");
    }
}
