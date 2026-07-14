/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mssqlserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.IdentifierQuotingPolicy;
import org.junit.jupiter.api.Test;

class MicrosoftSqlServerQuotingPolicyTest {

    private static final SchemaReference DBO = new SchemaReference(Optional.empty(), "dbo");
    private static final TableReference EMP = new TableReference(Optional.of(DBO), "EMPLOYEES",
            TableReference.TYPE_TABLE);
    private static final TableReference DEPT = new TableReference(Optional.of(DBO), "DEPARTMENTS",
            TableReference.TYPE_TABLE);

    private MicrosoftSqlServerDialect dialectNever() {
        MicrosoftSqlServerDialect d = new MicrosoftSqlServerDialect();
        d.setQuotingPolicy(IdentifierQuotingPolicy.NEVER);
        return d;
    }

    @Test
    void renameTable_via_spRename_unquoted() {
        assertThat(dialectNever().ddlGenerator().renameTable(EMP, "PERSON")).doesNotContainPattern("\\[[^]]*\\]")   // no
                                                                                                                    // [bracket]-quoted
                                                                                                                    // identifiers
                .contains("EMPLOYEES").contains("PERSON");
    }

    @Test
    void renameIndex_via_spRename_unquoted() {
        assertThat(dialectNever().ddlGenerator().renameIndex("IDX_OLD", "IDX_NEW", EMP))
                .doesNotContainPattern("\\[[^]]*\\]").contains("EMPLOYEES").contains("IDX_OLD").contains("IDX_NEW");
    }

    @Test
    void renameConstraint_via_spRename_unquoted() {
        assertThat(dialectNever().ddlGenerator().renameConstraint(EMP, "PK_OLD", "PK_NEW"))
                .doesNotContainPattern("\\[[^]]*\\]").contains("EMPLOYEES").contains("PK_OLD").contains("PK_NEW");
    }

    @Test
    void createTrigger_unquoted() {
        assertThat(dialectNever().ddlGenerator().createTrigger("TRG_EMP",
                org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming.AFTER,
                org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent.INSERT, EMP,
                org.eclipse.daanse.sql.model.schema.Trigger.TriggerScope.ROW, null, "INSERT INTO LOG VALUES (1)"))
                .doesNotContainPattern("\\[[^]]*\\]").contains("TRG_EMP").contains("EMPLOYEES");
    }

    @Test
    void dropTable_ifExists_unquoted() {
        assertThat(dialectNever().ddlGenerator().dropTable(EMP, true)).doesNotContainPattern("\\[[^]]*\\]")
                .contains("EMPLOYEES");
    }

    @Test
    void primaryKey_constraint_unquoted() {
        assertThat(dialectNever().ddlGenerator().addPrimaryKeyConstraint(EMP, "PK_EMP", List.of("ID")))
                .doesNotContainPattern("\\[[^]]*\\]").contains("EMPLOYEES").contains("PK_EMP").contains("ID");
    }

    @Test
    void foreignKey_constraint_unquoted() {
        assertThat(dialectNever().ddlGenerator().addForeignKeyConstraint(EMP, "FK_EMP_DEPT", List.of("DEPT_ID"), DEPT,
                List.of("ID"), "NO ACTION", "NO ACTION")).doesNotContainPattern("\\[[^]]*\\]").contains("EMPLOYEES")
                .contains("FK_EMP_DEPT").contains("DEPARTMENTS");
    }
}
