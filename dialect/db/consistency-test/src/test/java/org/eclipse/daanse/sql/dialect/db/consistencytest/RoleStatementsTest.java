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
import org.eclipse.daanse.sql.dialect.db.hive.HiveDialect;
import org.eclipse.daanse.sql.dialect.db.impala.ImpalaDialect;
import org.eclipse.daanse.sql.dialect.db.mariadb.MariaDBDialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.eclipse.daanse.sql.dialect.db.sqlite.SqliteDialect;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What each dialect writes to create and drop the role {@code r}, with and without
 * {@code IF [NOT] EXISTS}. A dialect without roles writes nothing and says so by
 * {@link DdlGenerator#supportsRoles()}.
 */
class RoleStatementsTest {

    /** The statements expected of a dialect; all null where it has no roles. */
    record Expected(String create, String createIfNotExists, String drop, String dropIfExists) {

        static Expected none() {
            return new Expected(null, null, null, null);
        }

        static Expected standard(String quoted) {
            return new Expected("CREATE ROLE " + quoted, "CREATE ROLE IF NOT EXISTS " + quoted, "DROP ROLE " + quoted,
                    "DROP ROLE IF EXISTS " + quoted);
        }

        static Expected withoutIf(String quoted) {
            return new Expected("CREATE ROLE " + quoted, "CREATE ROLE " + quoted, "DROP ROLE " + quoted,
                    "DROP ROLE " + quoted);
        }
    }

    static Stream<Arguments> dialects() {
        return Stream.of(
                of("postgresql", new PostgreSqlDialect(), new Expected("CREATE ROLE \"r\"",
                        "DO $$ BEGIN CREATE ROLE \"r\"; EXCEPTION WHEN duplicate_object THEN NULL; END $$",
                        "DROP ROLE \"r\"", "DROP ROLE IF EXISTS \"r\"")),
                of("oracle, no version", new OracleDialect(), Expected.withoutIf("\"r\"")),
                of("oracle 23", new OracleDialect(version(23, 0)), Expected.standard("\"r\"")),
                of("mssql", new MicrosoftSqlServerDialect(), new Expected("CREATE ROLE \"r\"",
                        "IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'r') CREATE ROLE \"r\"",
                        "DROP ROLE \"r\"",
                        "IF EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'r') DROP ROLE \"r\"")),
                of("mysql", new MySqlDialect(), Expected.standard("`r`")),
                of("mysql 5.7", new MySqlDialect(version(5, 7)), Expected.none()),
                of("mariadb", new MariaDBDialect(), Expected.standard("`r`")),
                of("h2", new H2Dialect(), Expected.standard("\"r\"")),
                of("clickhouse", new ClickHouseDialect(), Expected.standard("\"r\"")),
                of("derby", new DerbyDialect(), Expected.withoutIf("\"r\"")),
                of("hive", new HiveDialect(), Expected.withoutIf("`r`")),
                of("impala", new ImpalaDialect(), Expected.withoutIf("`r`")),
                of("ansi", new AnsiDialect(), Expected.standard("\"r\"")),
                of("duckdb", new DuckDbDialect(), Expected.none()),
                of("sqlite", new SqliteDialect(), Expected.none()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void createAndDropARole(Dialect dialect, Expected expected) {
        DdlGenerator ddl = dialect.ddlGenerator();

        assertThat(ddl.supportsRoles()).as("supportsRoles").isEqualTo(expected.create() != null);
        assertThat(ddl.createRole("r", false)).isEqualTo(Optional.ofNullable(expected.create()));
        assertThat(ddl.createRole("r", true)).isEqualTo(Optional.ofNullable(expected.createIfNotExists()));
        assertThat(ddl.dropRole("r", false)).isEqualTo(Optional.ofNullable(expected.drop()));
        assertThat(ddl.dropRole("r", true)).isEqualTo(Optional.ofNullable(expected.dropIfExists()));
    }

    private static Arguments of(String name, Dialect dialect, Expected expected) {
        return Arguments.of(Named.of(name, dialect), expected);
    }

    private static DialectInitData version(int major, int minor) {
        return DialectInitData.ansiDefaults().withVersion(major, minor);
    }
}
