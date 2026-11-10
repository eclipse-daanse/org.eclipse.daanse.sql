/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.derby;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.IdentifierQuotingPolicy;
import org.junit.jupiter.api.Test;

class DerbyDialectQuotingPolicyTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "TEST");
    private static final TableReference EMP = new TableReference(Optional.of(S), "EMPLOYEES",
            TableReference.TYPE_TABLE);
    private static final TableReference DEPT = new TableReference(Optional.of(S), "DEPARTMENTS",
            TableReference.TYPE_TABLE);

    private DerbyDialect dialectNever() {
        DerbyDialect d = new DerbyDialect();
        d.setQuotingPolicy(IdentifierQuotingPolicy.NEVER);
        return d;
    }

    @Test
    void primaryKey_unquoted() {
        assertThat(dialectNever().ddlGenerator().addPrimaryKeyConstraint(EMP, "PK_EMP", List.of("ID")))
                .doesNotContain("\"").contains("EMPLOYEES").contains("PK_EMP").contains("ID");
    }

    @Test
    void uniqueConstraint_unquoted() {
        assertThat(dialectNever().ddlGenerator().addUniqueConstraint(EMP, "UQ_EMP", List.of("EMAIL")))
                .doesNotContain("\"").contains("EMPLOYEES").contains("UQ_EMP").contains("EMAIL");
    }

    @Test
    void foreignKey_unquoted() {
        assertThat(dialectNever().ddlGenerator().addForeignKeyConstraint(EMP, "FK_EMP_DEPT", List.of("DEPT_ID"), DEPT,
                List.of("ID"), "NO ACTION", "NO ACTION")).doesNotContain("\"").contains("EMPLOYEES")
                .contains("FK_EMP_DEPT").contains("DEPARTMENTS");
    }

    @Test
    void renameTable_unquoted() {
        assertThat(dialectNever().ddlGenerator().renameTable(EMP, "PERSON")).doesNotContain("\"").contains("EMPLOYEES")
                .contains("PERSON");
    }

    @Test
    void createIndex_unquoted() {
        assertThat(dialectNever().ddlGenerator().createIndex("IDX_EMP", EMP, List.of("NAME"), false, false))
                .doesNotContain("\"").contains("EMPLOYEES").contains("IDX_EMP").contains("NAME");
    }

    @Test
    void dropConstraint_unquoted() {
        assertThat(dialectNever().ddlGenerator().dropConstraint(EMP, "PK_EMP", true)).doesNotContain("\"")
                .contains("EMPLOYEES").contains("PK_EMP");
    }
}
