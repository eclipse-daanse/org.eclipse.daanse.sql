/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/
package org.eclipse.daanse.sql.dialect.db.h2.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.SynonymDefinition;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.junit.jupiter.api.Test;

/** Synonym DDL of the H2 dialect — as text and executed against H2. */
class H2SynonymDdlTest {

    private final H2Dialect dialect = new H2Dialect();

    @Test
    void createAndDrop() {
        assertThat(dialect.createSynonym(SynonymDefinition.of("PUBLIC", "EMP", "PUBLIC", "EMPLOYEES"), false))
                .contains("CREATE SYNONYM \"PUBLIC\".\"EMP\" FOR \"PUBLIC\".\"EMPLOYEES\"");
        assertThat(dialect.createSynonym(SynonymDefinition.of("PUBLIC", "EMP", "PUBLIC", "EMPLOYEES"), true))
                .contains("CREATE OR REPLACE SYNONYM \"PUBLIC\".\"EMP\" FOR \"PUBLIC\".\"EMPLOYEES\"");
        assertThat(dialect.dropSynonym("PUBLIC", "EMP", false, true))
                .contains("DROP SYNONYM IF EXISTS \"PUBLIC\".\"EMP\"");
    }

    @Test
    void remoteAndPublicTargetsAreNotExpressible() {
        assertThat(dialect.createSynonym(
                new SynonymDefinition("PUBLIC", "EMP", null, "PUBLIC", "EMPLOYEES", "HQ", false), false)).isEmpty();
        assertThat(dialect.createSynonym(
                new SynonymDefinition("PUBLIC", "EMP", "OTHERDB", "PUBLIC", "EMPLOYEES", null, false), false))
                .isEmpty();
        assertThat(dialect.createSynonym(
                new SynonymDefinition("PUBLIC", "EMP", null, "PUBLIC", "EMPLOYEES", null, true), false)).isEmpty();
    }

    @Test
    void generatedDdlRunsOnH2() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:h2SynonymDdl", "sa", "");
                Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA HR");
            s.execute("CREATE TABLE HR.EMPLOYEES (ID INT PRIMARY KEY, NAME VARCHAR(50))");
            s.execute("INSERT INTO HR.EMPLOYEES VALUES (1, 'Ann')");

            SynonymDefinition emp = SynonymDefinition.of("PUBLIC", "EMP", "HR", "EMPLOYEES");
            s.execute(dialect.createSynonym(emp, false).orElseThrow());
            s.execute(dialect.createSynonym(emp, true).orElseThrow());
            try (ResultSet rs = s.executeQuery("SELECT NAME FROM PUBLIC.EMP")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("Ann");
            }

            s.execute(dialect.dropSynonym("PUBLIC", "EMP", false, false).orElseThrow());
            s.execute(dialect.dropSynonym("PUBLIC", "EMP", false, true).orElseThrow());
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SYNONYMS WHERE SYNONYM_NAME = 'EMP'")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }
}
