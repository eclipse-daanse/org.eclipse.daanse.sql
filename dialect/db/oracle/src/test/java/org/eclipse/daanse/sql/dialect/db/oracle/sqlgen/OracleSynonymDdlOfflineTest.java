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

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.SynonymDefinition;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.junit.jupiter.api.Test;

class OracleSynonymDdlOfflineTest {

    private final OracleDialect dialect = new OracleDialect();

    @Test
    void supportsSynonyms() {
        assertThat(dialect.supportsSynonyms()).isTrue();
    }

    @Test
    void createPrivateSynonym() {
        assertThat(dialect.createSynonym(SynonymDefinition.of("HR", "EMP", "HR", "EMPLOYEES"), false))
                .contains("CREATE SYNONYM \"HR\".\"EMP\" FOR \"HR\".\"EMPLOYEES\"");
    }

    @Test
    void createOrReplace() {
        assertThat(dialect.createSynonym(SynonymDefinition.of("HR", "EMP", "HR", "EMPLOYEES"), true))
                .contains("CREATE OR REPLACE SYNONYM \"HR\".\"EMP\" FOR \"HR\".\"EMPLOYEES\"");
    }

    @Test
    void createPublicSynonymHasNoSchema() {
        assertThat(dialect.createSynonym(
                new SynonymDefinition("PUBLIC", "EMP", null, "HR", "EMPLOYEES", null, true), false))
                .contains("CREATE PUBLIC SYNONYM \"EMP\" FOR \"HR\".\"EMPLOYEES\"");
    }

    @Test
    void createSynonymOverDbLink() {
        assertThat(dialect.createSynonym(
                new SynonymDefinition("HR", "EMP_R", null, "HR", "EMPLOYEES", "HQ.EXAMPLE.COM", false), false))
                .contains("CREATE SYNONYM \"HR\".\"EMP_R\" FOR \"HR\".\"EMPLOYEES\"@HQ.EXAMPLE.COM");
    }

    @Test
    void targetInOtherCatalogIsNotExpressible() {
        assertThat(dialect.createSynonym(
                new SynonymDefinition("HR", "EMP", "OTHERDB", "HR", "EMPLOYEES", null, false), false)).isEmpty();
    }

    @Test
    void dropHasNoIfExists() {
        assertThat(dialect.dropSynonym("HR", "EMP", false, true)).contains("DROP SYNONYM \"HR\".\"EMP\"");
    }

    @Test
    void dropPublicSynonym() {
        assertThat(dialect.dropSynonym("PUBLIC", "EMP", true, false)).contains("DROP PUBLIC SYNONYM \"EMP\"");
    }
}
