/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * ResultSet assertion helpers shared by per-engine generator round-trip tests.
 * Each helper opens its own Statement/ResultSet so the calling test can pass
 * the connection directly.
 */
public final class RoundTripAssertions {

    private RoundTripAssertions() {
        // static helpers only
    }

    /**
     * Run {@code selectIdSql}, count rows, and assert the first {@code id} value.
     *
     * @param conn            open connection
     * @param selectIdSql     query whose first column is an integer id
     * @param expectedCount   row count expected
     * @param expectedFirstId integer value expected in the first row
     * @throws SQLException on database access error
     */
    public static void assertSelectIdRowCountAndFirst(Connection conn, String selectIdSql, int expectedCount,
            int expectedFirstId) throws SQLException {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(selectIdSql)) {
            int count = 0;
            int firstId = -1;
            while (rs.next()) {
                if (count == 0)
                    firstId = rs.getInt(1);
                count++;
            }
            assertThat(count).as("row count").isEqualTo(expectedCount);
            assertThat(firstId).as("first id").isEqualTo(expectedFirstId);
        }
    }

    /**
     * Execute {@code dml} and assert the returned update count.
     *
     * @param conn             open connection
     * @param dml              INSERT/UPDATE/DELETE/MERGE statement
     * @param expectedAffected expected affected row count from
     *                         {@link Statement#executeUpdate(String)}
     * @param description      AssertJ description for failure reporting
     * @throws SQLException on database access error
     */
    public static void assertExecuteUpdateAffected(Connection conn, String dml, int expectedAffected,
            String description) throws SQLException {
        try (Statement s = conn.createStatement()) {
            assertThat(s.executeUpdate(dml)).as(description).isEqualTo(expectedAffected);
        }
    }

    /**
     * Run {@code selectSql} and assert the first column of the first row equals
     * {@code expected}.
     *
     * @param conn      open connection
     * @param selectSql query whose first column is a String
     * @param expected  expected String value
     * @throws SQLException on database access error
     */
    public static void assertFirstStringEquals(Connection conn, String selectSql, String expected) throws SQLException {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(selectSql)) {
            assertThat(rs.next()).as("at least one row").isTrue();
            assertThat(rs.getString(1)).isEqualTo(expected);
        }
    }

    /**
     * Run {@code selectSql} and assert the first column of the first row equals
     * {@code expected}.
     *
     * @param conn      open connection
     * @param selectSql query whose first column is an int
     * @param expected  expected int value
     * @throws SQLException on database access error
     */
    public static void assertFirstIntEquals(Connection conn, String selectSql, int expected) throws SQLException {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(selectSql)) {
            assertThat(rs.next()).as("at least one row").isTrue();
            assertThat(rs.getInt(1)).isEqualTo(expected);
        }
    }
}
