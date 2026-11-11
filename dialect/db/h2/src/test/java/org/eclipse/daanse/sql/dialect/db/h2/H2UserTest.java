/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.h2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.db.testsupport.UserRoundTrip;
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.schema.DatabasePrincipal;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A user H2 creates without a password: it exists and takes a role and a privilege, cannot log
 * in with any password, and is dropped again.
 */
class H2UserTest {

    private static final String URL = "jdbc:h2:mem:h2users;DB_CLOSE_DELAY=-1";

    private Connection conn;
    private H2Dialect dialect;
    private TableReference table;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection(URL, "sa", "");
        dialect = new H2Dialect(DialectInitData.fromConnection(conn));
        table = new TableReference(Optional.of(new SchemaReference(Optional.empty(), "PUBLIC")), "T");
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE PUBLIC.T (ID INT)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("DROP ALL OBJECTS");
        }
        conn.close();
    }

    /** H2's provider reads no principals; the round trip checks the statements run. */
    @Test
    void user_is_created_given_a_role_and_dropped() throws SQLException {
        UserRoundTrip.assertUserRoundTrip(conn, dialect.ddlGenerator(), MetadataProvider.EMPTY,
                DatabasePrincipal.KIND_USER, table, "rt_user", "rt_role");
    }

    @Test
    void user_exists_and_cannot_log_in() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute(dialect.createUser("rt_user", false).orElseThrow());
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.USERS WHERE UPPER(USER_NAME) = 'RT_USER'")) {
                rs.next();
                assertThat(rs.getInt(1)).as("the user").isEqualTo(1);
            }
            for (String password : new String[] { "", "rt_user", "sa" }) {
                assertThatThrownBy(() -> DriverManager.getConnection(URL, "rt_user", password).close())
                        .as("logging in with '%s'", password).isInstanceOf(SQLException.class);
            }
            s.execute(dialect.dropUser("rt_user", false).orElseThrow());
        }
    }
}
