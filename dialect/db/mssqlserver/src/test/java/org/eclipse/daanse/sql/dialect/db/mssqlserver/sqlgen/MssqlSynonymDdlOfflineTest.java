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

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.SynonymDefinition;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.junit.jupiter.api.Test;

class MssqlSynonymDdlOfflineTest {

    private final MicrosoftSqlServerDialect dialect = new MicrosoftSqlServerDialect();

    @Test
    void supportsSynonyms() {
        assertThat(dialect.supportsSynonyms()).isTrue();
    }

    @Test
    void createLocalSynonym() {
        assertThat(dialect.createSynonym(SynonymDefinition.of("dbo", "emp", "hr", "employees"), false))
                .contains("CREATE SYNONYM \"dbo\".\"emp\" FOR \"hr\".\"employees\"");
    }

    @Test
    void orReplaceIsIgnored() {
        assertThat(dialect.createSynonym(SynonymDefinition.of("dbo", "emp", "hr", "employees"), true))
                .contains("CREATE SYNONYM \"dbo\".\"emp\" FOR \"hr\".\"employees\"");
    }

    @Test
    void targetInOtherDatabase() {
        assertThat(dialect.createSynonym(
                new SynonymDefinition("dbo", "ord", "otherdb", "sales", "orders", null, false), false))
                .contains("CREATE SYNONYM \"dbo\".\"ord\" FOR \"otherdb\".\"sales\".\"orders\"");
    }

    @Test
    void linkedServerWithAllParts() {
        assertThat(dialect.createSynonym(
                new SynonymDefinition("dbo", "ord", "otherdb", "sales", "orders", "remotesrv", false), false))
                .contains("CREATE SYNONYM \"dbo\".\"ord\" FOR \"remotesrv\".\"otherdb\".\"sales\".\"orders\"");
    }

    @Test
    void missingMiddlePartsStayEmpty() {
        assertThat(dialect.createSynonym(
                new SynonymDefinition("dbo", "ord", null, "sales", "orders", "remotesrv", false), false))
                .contains("CREATE SYNONYM \"dbo\".\"ord\" FOR \"remotesrv\"..\"sales\".\"orders\"");
        assertThat(dialect.createSynonym(
                new SynonymDefinition("dbo", "ord", "otherdb", null, "orders", null, false), false))
                .contains("CREATE SYNONYM \"dbo\".\"ord\" FOR \"otherdb\"..\"orders\"");
    }

    @Test
    void publicSynonymIsNotExpressible() {
        assertThat(dialect.createSynonym(
                new SynonymDefinition("PUBLIC", "emp", null, "hr", "employees", null, true), false)).isEmpty();
        assertThat(dialect.dropSynonym("PUBLIC", "emp", true, false)).isEmpty();
    }

    @Test
    void dropIfExists() {
        assertThat(dialect.dropSynonym("dbo", "emp", false, true)).contains("DROP SYNONYM IF EXISTS \"dbo\".\"emp\"");
        assertThat(dialect.dropSynonym("dbo", "emp", false, false)).contains("DROP SYNONYM \"dbo\".\"emp\"");
    }
}
