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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.schema.DatabasePrincipal;
import org.eclipse.daanse.sql.jdbc.api.schema.RoleMembership;
import org.eclipse.daanse.sql.model.schema.TableReference;

/**
 * A user and a role on a database, as a dialect writes them: the user is created next to the role,
 * given the role and a privilege on a table, read back as a principal and a member of the role where
 * the metadata provider reads principals, and dropped again. Where the dialect can say
 * {@code IF NOT EXISTS} or {@code IF EXISTS}, creating the user twice or dropping it twice is no
 * error; a dialect says so by writing another statement for it.
 */
public final class UserRoundTrip {

    private UserRoundTrip() {
        // static helpers only
    }

    /**
     * @param conn     a connection that may create users and roles and grant on {@code table}
     * @param ddl      the dialect's DDL generator
     * @param provider the metadata provider that reads the principals and memberships back
     * @param userKind the {@link DatabasePrincipal#kind()} the provider gives a user: KIND_USER where
     *                 it tells users from roles, KIND_UNKNOWN where it cannot
     * @param table    a table the user is granted SELECT on
     * @param user     the user's name; principals are compared without regard to case, as some
     *                 databases fold them
     * @param role     the role's name
     */
    public static void assertUserRoundTrip(Connection conn, DdlGenerator ddl, MetadataProvider provider,
            String userKind, TableReference table, String user, String role) throws SQLException {
        assertThat(ddl.supportsUsers()).as("supportsUsers").isTrue();
        String create = ddl.createUser(user, false).orElseThrow();
        String createIfNotExists = ddl.createUser(user, true).orElseThrow();
        String drop = ddl.dropUser(user, false).orElseThrow();
        String dropIfExists = ddl.dropUser(user, true).orElseThrow();

        execute(conn, ddl.createRole(role));
        execute(conn, create);
        if (!createIfNotExists.equals(create)) {
            execute(conn, createIfNotExists);
        }
        execute(conn, ddl.grantRole(role, user, false));
        execute(conn, ddl.grantTablePrivilege("SELECT", table, user, false));

        Optional<List<DatabasePrincipal>> principals = provider.getAllPrincipals(conn);
        principals.ifPresent(all -> {
            assertThat(all).filteredOn(p -> p.name().equalsIgnoreCase(user)).as("the user").singleElement()
                    .satisfies(p -> assertThat(p.kind()).isEqualTo(userKind));
            if (DatabasePrincipal.KIND_USER.equals(userKind)) {
                assertThat(all).filteredOn(p -> p.name().equalsIgnoreCase(role)).as("the role").singleElement()
                        .satisfies(p -> assertThat(p.kind()).isEqualTo(DatabasePrincipal.KIND_ROLE));
            }
        });
        Optional<List<RoleMembership>> memberships = provider.getAllRoleMemberships(conn);
        memberships.ifPresent(all -> assertThat(all).as("the user's membership in the role")
                .anyMatch(m -> m.grantee().equalsIgnoreCase(user) && m.role().equalsIgnoreCase(role)));

        execute(conn, ddl.revokeTablePrivilege("SELECT", table, user));
        execute(conn, ddl.revokeRole(role, user));
        execute(conn, drop);
        if (!dropIfExists.equals(drop)) {
            execute(conn, dropIfExists);
        }
        execute(conn, ddl.dropRole(role));

        provider.getAllPrincipals(conn).ifPresent(all -> assertThat(all).as("the principals after the drop")
                .noneMatch(p -> p.name().equalsIgnoreCase(user) || p.name().equalsIgnoreCase(role)));
    }

    /**
     * Creates the role {@code role}, and again where the dialect can say {@code IF NOT EXISTS}, reads
     * it back as a principal of {@code roleKind} where the provider reads principals, drops it, and
     * again where the dialect can say {@code IF EXISTS}.
     */
    public static void assertRoleRoundTrip(Connection conn, DdlGenerator ddl, MetadataProvider provider,
            String roleKind, String role) throws SQLException {
        assertThat(ddl.supportsRoles()).as("supportsRoles").isTrue();
        String create = ddl.createRole(role, false).orElseThrow();
        String createIfNotExists = ddl.createRole(role, true).orElseThrow();
        String drop = ddl.dropRole(role, false).orElseThrow();
        String dropIfExists = ddl.dropRole(role, true).orElseThrow();

        execute(conn, create);
        if (!createIfNotExists.equals(create)) {
            execute(conn, createIfNotExists);
        }
        provider.getAllPrincipals(conn).ifPresent(all -> assertThat(all)
                .filteredOn(p -> p.name().equalsIgnoreCase(role)).as("the role").singleElement()
                .satisfies(p -> assertThat(p.kind()).isEqualTo(roleKind)));
        execute(conn, drop);
        if (!dropIfExists.equals(drop)) {
            execute(conn, dropIfExists);
        }
        provider.getAllPrincipals(conn).ifPresent(all -> assertThat(all).as("the principals after the drop")
                .noneMatch(p -> p.name().equalsIgnoreCase(role)));
    }

    private static void execute(Connection conn, String sql) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new SQLException(e.getMessage() + " -- " + sql, e.getSQLState(), e.getErrorCode(), e);
        }
    }
}
