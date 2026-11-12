/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.jdbc.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureColumn;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Read through the JDBC driver, each of two routines of one name has its own parameters, those the
 * driver names with its specific name. H2 makes an alias of {@code Integer.toString} two
 * procedures, {@code toString(int)} and {@code toString(int, int)}.
 */
class DatabaseServiceReadsOverloadsH2Test {

    private static Connection connection;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:readsOverloads;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE ALIAS TOSTR FOR 'java.lang.Integer.toString'");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
        connection.close();
    }

    @Test
    void eachOverloadHasItsOwnParameters() throws Exception {
        List<Procedure> overloads = new DatabaseServiceImpl().getProcedures(connection, MetadataProvider.EMPTY, null,
                "PUBLIC", "TOSTR");

        assertThat(overloads).hasSize(2);
        assertThat(overloads).extracting(p -> p.columns().stream().map(ProcedureColumn::name).toList())
                .containsExactlyInAnyOrder(List.of("RESULT", "P1"), List.of("RESULT", "P1", "P2"));
    }
}
