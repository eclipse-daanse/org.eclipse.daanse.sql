/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.IdentifierQuotingPolicy;
import org.junit.jupiter.api.Test;

class MariaDBQuotingPolicyTest {

    private static final SchemaReference S = new SchemaReference(Optional.empty(), "TEST");
    private static final TableReference EMP = new TableReference(Optional.of(S), "EMPLOYEES",
            TableReference.TYPE_TABLE);

    private MariaDBDialect dialectNever() {
        MariaDBDialect d = new MariaDBDialect();
        d.setQuotingPolicy(IdentifierQuotingPolicy.NEVER);
        return d;
    }

    @Test
    void primaryKey_unquoted() {
        assertThat(dialectNever().ddlGenerator().addPrimaryKeyConstraint(EMP, "PK_EMP", List.of("ID")))
                .doesNotContain("`").contains("EMPLOYEES").contains("PK_EMP").contains("ID");
    }

    @Test
    void renameIndex_unquoted() {
        assertThat(dialectNever().ddlGenerator().renameIndex("IDX_OLD", "IDX_NEW", EMP)).doesNotContain("`")
                .contains("EMPLOYEES").contains("IDX_OLD").contains("IDX_NEW");
    }

    @Test
    void createTriggerProcedure_unquoted() {
        // MariaDB inherits MySQL's CREATE PROCEDURE wrapper.
        assertThat(
                dialectNever().ddlGenerator().createTriggerProcedure("AUDIT_PROC", "TEST", "BEGIN END").orElseThrow())
                .doesNotContain("`").contains("AUDIT_PROC").contains("TEST");
    }

    @Test
    void dropProcedure_unquoted() {
        assertThat(dialectNever().ddlGenerator().dropProcedure("AUDIT_PROC", "TEST", true).orElseThrow())
                .doesNotContain("`").contains("AUDIT_PROC").contains("TEST");
    }
}
