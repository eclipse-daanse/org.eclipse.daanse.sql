/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.consistencytest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.dialect.db.clickhouse.ClickHouseDialect;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.dialect.db.derby.DerbyDialect;
import org.eclipse.daanse.sql.dialect.db.duckdb.DuckDbDialect;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.eclipse.daanse.sql.dialect.db.mariadb.MariaDBDialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.eclipse.daanse.sql.dialect.db.sqlite.SqliteDialect;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What each dialect writes to create and drop the user {@code u}: without credentials, and so that
 * it cannot log in before an administrator gives it a way to. A dialect without users writes
 * nothing and says so by {@link DdlGenerator#supportsUsers()}.
 */
class UserStatementsTest {

    /** The statements expected of a dialect; all null where it has no users. */
    record Expected(String create, String createIfNotExists, String drop, String dropIfExists) {

        static Expected none() {
            return new Expected(null, null, null, null);
        }
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> dialects() {
        return Stream.of(
                of("postgresql", new PostgreSqlDialect(), new Expected("CREATE USER \"u\"",
                        "DO $$ BEGIN CREATE USER \"u\"; EXCEPTION WHEN duplicate_object THEN NULL; END $$",
                        "DROP USER \"u\"", "DROP USER IF EXISTS \"u\"")),
                of("oracle, no version", new OracleDialect(), new Expected("CREATE USER \"u\" NO AUTHENTICATION",
                        "CREATE USER \"u\" NO AUTHENTICATION", "DROP USER \"u\"", "DROP USER \"u\"")),
                of("oracle 23", new OracleDialect(version(23, 0)),
                        new Expected("CREATE USER \"u\" NO AUTHENTICATION",
                                "CREATE USER IF NOT EXISTS \"u\" NO AUTHENTICATION", "DROP USER \"u\"",
                                "DROP USER IF EXISTS \"u\"")),
                of("oracle 12", new OracleDialect(version(12, 2)), Expected.none()),
                of("mssql", new MicrosoftSqlServerDialect(), new Expected("CREATE USER \"u\" WITHOUT LOGIN",
                        "IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'u')"
                                + " CREATE USER \"u\" WITHOUT LOGIN",
                        "DROP USER \"u\"",
                        "IF EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'u') DROP USER \"u\"")),
                of("mysql", new MySqlDialect(), new Expected("CREATE USER `u` ACCOUNT LOCK",
                        "CREATE USER IF NOT EXISTS `u` ACCOUNT LOCK", "DROP USER `u`", "DROP USER IF EXISTS `u`")),
                of("mysql 5.6", new MySqlDialect(version(5, 6)), Expected.none()),
                of("mariadb", new MariaDBDialect(), new Expected("CREATE USER `u` ACCOUNT LOCK",
                        "CREATE USER IF NOT EXISTS `u` ACCOUNT LOCK", "DROP USER `u`", "DROP USER IF EXISTS `u`")),
                of("mariadb 10.3", new MariaDBDialect(version(10, 3)), Expected.none()),
                of("h2", new H2Dialect(), new Expected("CREATE USER \"u\" SALT X'' HASH X''",
                        "CREATE USER IF NOT EXISTS \"u\" SALT X'' HASH X''", "DROP USER \"u\"",
                        "DROP USER IF EXISTS \"u\"")),
                of("clickhouse", new ClickHouseDialect(), new Expected("CREATE USER \"u\" HOST NONE",
                        "CREATE USER IF NOT EXISTS \"u\" HOST NONE", "DROP USER \"u\"", "DROP USER IF EXISTS \"u\"")),
                of("duckdb", new DuckDbDialect(), Expected.none()),
                of("sqlite", new SqliteDialect(), Expected.none()),
                of("derby", new DerbyDialect(), Expected.none()),
                of("ansi", new AnsiDialect(DialectInitData.ansiDefaults()), Expected.none()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void createAndDropAUser(Dialect dialect, Expected expected) {
        DdlGenerator ddl = dialect.ddlGenerator();

        assertThat(ddl.supportsUsers()).as("supportsUsers").isEqualTo(expected.create() != null);
        assertThat(ddl.createUser("u", false)).isEqualTo(Optional.ofNullable(expected.create()));
        assertThat(ddl.createUser("u", true)).isEqualTo(Optional.ofNullable(expected.createIfNotExists()));
        assertThat(ddl.dropUser("u", false)).isEqualTo(Optional.ofNullable(expected.drop()));
        assertThat(ddl.dropUser("u", true)).isEqualTo(Optional.ofNullable(expected.dropIfExists()));
    }

    private static org.junit.jupiter.params.provider.Arguments of(String name, Dialect dialect, Expected expected) {
        return org.junit.jupiter.params.provider.Arguments.of(Named.of(name, dialect), expected);
    }

    private static DialectInitData version(int major, int minor) {
        return DialectInitData.ansiDefaults().withVersion(major, minor);
    }
}
