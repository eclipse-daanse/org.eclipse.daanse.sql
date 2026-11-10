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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.SynonymDefinition;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/** The Oracle dialect's synonym DDL, executed against a live Oracle. */
@Testcontainers
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
@TestInstance(Lifecycle.PER_CLASS)
class OracleSynonymDdlRoundTripTest {

    @Container
    @SuppressWarnings("resource")
    static final OracleContainer CONTAINER = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    private static final String SCHEMA = "TEST";

    private final OracleDialect dialect = new OracleDialect();
    private Connection connection;

    @BeforeAll
    void setUp() throws Exception {
        try (Connection system = DriverManager.getConnection(CONTAINER.getJdbcUrl(), "system",
                CONTAINER.getPassword()); Statement s = system.createStatement()) {
            s.execute("GRANT CREATE SYNONYM, CREATE PUBLIC SYNONYM, DROP PUBLIC SYNONYM TO " + SCHEMA);
        }
        connection = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(),
                CONTAINER.getPassword());
        execute("CREATE TABLE EMPLOYEES (ID NUMBER PRIMARY KEY, NAME VARCHAR2(50))");
        execute("INSERT INTO EMPLOYEES VALUES (1, 'Ann')");
    }

    @AfterAll
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed())
            connection.close();
    }

    @Test
    void privateSynonymCreateReplaceQueryDrop() throws Exception {
        SynonymDefinition emp = SynonymDefinition.of(SCHEMA, "EMP", SCHEMA, "EMPLOYEES");
        execute(dialect.createSynonym(emp, false).orElseThrow());
        execute(dialect.createSynonym(emp, true).orElseThrow());
        assertThat(queryName("SELECT NAME FROM EMP")).isEqualTo("Ann");

        execute(dialect.dropSynonym(SCHEMA, "EMP", false, true).orElseThrow());
        assertThat(count("SELECT COUNT(*) FROM ALL_SYNONYMS WHERE OWNER = ? AND SYNONYM_NAME = 'EMP'", SCHEMA))
                .isZero();
    }

    @Test
    void publicSynonymCreateQueryDrop() throws Exception {
        execute(dialect.createSynonym(
                new SynonymDefinition("PUBLIC", "EMP_ALL", null, SCHEMA, "EMPLOYEES", null, true), false)
                .orElseThrow());
        assertThat(count("SELECT COUNT(*) FROM ALL_SYNONYMS WHERE OWNER = ? AND SYNONYM_NAME = 'EMP_ALL'",
                "PUBLIC")).isEqualTo(1);
        assertThat(queryName("SELECT NAME FROM EMP_ALL")).isEqualTo("Ann");

        execute(dialect.dropSynonym("PUBLIC", "EMP_ALL", true, false).orElseThrow());
        assertThat(count("SELECT COUNT(*) FROM ALL_SYNONYMS WHERE OWNER = ? AND SYNONYM_NAME = 'EMP_ALL'",
                "PUBLIC")).isZero();
    }

    @Test
    void dbLinkSynonymIsAccepted() throws Exception {
        // Oracle checks neither the link nor the remote object when creating the synonym.
        execute(dialect.createSynonym(
                new SynonymDefinition(SCHEMA, "EMP_REMOTE", null, "HR", "EMPLOYEES", "HQ_LINK", false), false)
                .orElseThrow());
        assertThat(count("SELECT COUNT(*) FROM ALL_SYNONYMS WHERE OWNER = ? AND SYNONYM_NAME = 'EMP_REMOTE'"
                + " AND DB_LINK LIKE 'HQ_LINK%'", SCHEMA)).isEqualTo(1);
        execute(dialect.dropSynonym(SCHEMA, "EMP_REMOTE", false, false).orElseThrow());
    }

    private void execute(String sql) throws Exception {
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
        }
    }

    private String queryName(String sql) throws Exception {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    private int count(String sql, String owner) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
