/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.oracle;

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

class OracleQuotingPolicyTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "RT");
    private static final TableReference EMP = new TableReference(Optional.of(S), "EMPLOYEES",
            TableReference.TYPE_TABLE);
    private static final TableReference DEPT = new TableReference(Optional.of(S), "DEPARTMENTS",
            TableReference.TYPE_TABLE);

    private OracleDialect dialectNever() {
        OracleDialect d = new OracleDialect();
        d.setQuotingPolicy(IdentifierQuotingPolicy.NEVER);
        return d;
    }

    private static ColumnMetaData numColumn() {
        return new ColumnMetaDataRecord(JDBCType.NUMERIC, "NUMBER", OptionalInt.of(10), OptionalInt.empty(),
                OptionalInt.empty(), ColumnMetaData.Nullability.NO_NULLS, OptionalInt.empty(), Optional.empty(),
                Optional.empty(), ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    @Test
    void alterTableAddColumn_unquoted() {
        assertThat(dialectNever().ddlGenerator().alterTableAddColumn(EMP,
                new org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord(
                        new org.eclipse.daanse.sql.model.schema.ColumnReference(Optional.of(EMP), "NEW_COL"),
                        numColumn())))
                .doesNotContain("\"").contains("EMPLOYEES").contains("NEW_COL");
    }

    @Test
    void alterColumnType_unquoted() {
        assertThat(dialectNever().ddlGenerator().alterColumnType(EMP, "EMAIL", numColumn())).doesNotContain("\"")
                .contains("EMPLOYEES").contains("EMAIL");
    }

    @Test
    void alterColumnSetNullability_unquoted() {
        assertThat(dialectNever().ddlGenerator().alterColumnSetNullability(EMP, "EMAIL", false)).doesNotContain("\"")
                .contains("EMPLOYEES").contains("EMAIL");
    }

    @Test
    void alterColumnSetDefault_unquoted() {
        assertThat(dialectNever().ddlGenerator().alterColumnSetDefault(EMP, "STATUS", "'A'")).doesNotContain("\"")
                .contains("EMPLOYEES").contains("STATUS");
    }

    @Test
    void addForeignKey_unquoted() {
        assertThat(dialectNever().ddlGenerator().addForeignKeyConstraint(EMP, "FK_EMP_DEPT", List.of("DEPT_ID"), DEPT,
                List.of("ID"), "NO ACTION", "NO ACTION")).doesNotContain("\"").contains("EMPLOYEES")
                .contains("FK_EMP_DEPT").contains("DEPARTMENTS");
    }

    @Test
    void createTriggerProcedure_unquoted() {
        assertThat(dialectNever().ddlGenerator().createTriggerProcedure("AUDIT_PROC", "RT", "BEGIN NULL; END;")
                .orElseThrow()).doesNotContain("\"").contains("AUDIT_PROC").contains("RT");
    }

    @Test
    void dropProcedure_unquoted() {
        assertThat(dialectNever().ddlGenerator().dropProcedure("AUDIT_PROC", "RT", true).orElseThrow())
                .doesNotContain("\"").contains("AUDIT_PROC").contains("RT");
    }
}
