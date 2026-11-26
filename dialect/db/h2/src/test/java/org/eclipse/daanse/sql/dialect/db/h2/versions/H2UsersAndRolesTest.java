/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.h2.versions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * H2 has users and roles on every version from 1.4.200 on, {@code IF [NOT] EXISTS} on their creation and drop, and no
 * {@code WITH ADMIN OPTION} on a role's grant (a syntax error). The dialect is built from a live connection of each
 * version: the user it creates, with an empty salt and hash no password hashes to, logs in with no password, takes a
 * role that holds a privilege, reads what the role gives it once an administrator gives it a password, no longer once
 * the role is revoked, and takes a privilege of its own; the admin option of a grant is left out; a second create and
 * a drop of a missing one fail, IF [NOT] EXISTS spares the error.
 */
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
class H2UsersAndRolesTest {

    private static final TableReference T = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "PUBLIC")), "T");

    @ParameterizedTest(name = "h2 {0}")
    @ValueSource(strings = { "1.4.200", "2.0.206", "2.1.214", "2.2.224", "2.3.232" })
    void usersAndRoles(String version) throws Exception {
        try (H2Versions.Session h2 = H2Versions.open(version)) {
            h2.execute("CREATE TABLE PUBLIC.T(ID INT)");
            h2.execute("INSERT INTO PUBLIC.T VALUES (1)");
            DdlGenerator ddl = h2.dialect().ddlGenerator();
            assertThat(ddl.supportsUsers()).isTrue();
            assertThat(ddl.supportsRoles()).isTrue();

            String createUser = ddl.createUser("RT_USER", false).orElseThrow();
            h2.execute(createUser);
            assertRefused(h2, createUser, "a second user of the name");
            h2.execute(ddl.createUser("RT_USER", true).orElseThrow());
            for (String password : new String[] { "", "RT_USER", "sa" }) {
                assertThatThrownBy(() -> h2.login("RT_USER", password).close())
                        .as("no login with '" + password + "'").isInstanceOf(SQLException.class);
            }

            String createRole = ddl.createRole("RT_ROLE", false).orElseThrow();
            h2.execute(createRole);
            assertRefused(h2, createRole, "a second role of the name");
            h2.execute(ddl.createRole("RT_ROLE", true).orElseThrow());
            h2.execute(ddl.grantTablePrivilege("SELECT", T, "RT_ROLE", false));

            String grant = ddl.grantRole("RT_ROLE", "RT_USER", true);
            assertThat(grant).as("no admin option, which H2 has not").doesNotContain("ADMIN");
            assertRefused(h2, grant + " WITH ADMIN OPTION", "WITH ADMIN OPTION");
            h2.execute(grant);
            h2.execute("ALTER USER \"RT_USER\" SET PASSWORD 'pw'");
            assertThat(count(h2)).as("the user reads with the role's privilege").isEqualTo(1);

            h2.execute(ddl.revokeRole("RT_ROLE", "RT_USER"));
            assertThatThrownBy(() -> count(h2)).as("no longer once revoked").isInstanceOf(SQLException.class);
            h2.execute(ddl.grantTablePrivilege("SELECT", T, "RT_USER", false));
            assertThat(count(h2)).as("a privilege of its own").isEqualTo(1);
            h2.execute(ddl.revokeTablePrivilege("SELECT", T, "RT_USER"));
            assertThatThrownBy(() -> count(h2)).as("revoked").isInstanceOf(SQLException.class);

            String dropRole = ddl.dropRole("RT_ROLE", false).orElseThrow();
            h2.execute(dropRole);
            assertRefused(h2, dropRole, "a missing role");
            h2.execute(ddl.dropRole("RT_ROLE", true).orElseThrow());
            String dropUser = ddl.dropUser("RT_USER", false).orElseThrow();
            h2.execute(dropUser);
            assertRefused(h2, dropUser, "a missing user");
            h2.execute(ddl.dropUser("RT_USER", true).orElseThrow());
            assertThatThrownBy(() -> h2.login("RT_USER", "pw").close()).as("dropped")
                    .isInstanceOf(SQLException.class);
        }
    }

    /** The rows of T as RT_USER reads them, logged in anew. */
    private static int count(H2Versions.Session h2) throws SQLException {
        try (Connection c = h2.login("RT_USER", "pw"); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM PUBLIC.T")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void assertRefused(H2Versions.Session h2, String sql, String what) {
        assertThatThrownBy(() -> h2.execute(sql)).as(what + ": " + sql).isInstanceOf(SQLException.class);
    }
}
