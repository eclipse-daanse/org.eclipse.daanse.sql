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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.ParameterMode;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineDefinition;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineParameter;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.RoutineSignature;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What each dialect writes to create and drop the function {@code s.f (a, b)} and the procedure
 * {@code s.p (n, INOUT total)}, with a body in a language it takes. A dialect that writes no
 * routines writes nothing and takes no language ({@link DdlGenerator#routineLanguages()}).
 */
class RoutineStatementsTest {

    /** What a dialect takes and writes; the statements are null where it writes no routines. */
    record Expected(Set<String> languages, String language, String types, String body, String procedureBody,
            String createFunction, String createOrReplaceFunction, String createProcedure, String dropFunction,
            String dropProcedure) {

        static Expected none() {
            return new Expected(Set.of(), "sql", "INT", "RETURN a + b", "SET total = total + n", null, null, null,
                    null, null);
        }
    }

    static Stream<Arguments> dialects() {
        return Stream.of(
                of("postgresql", new PostgreSqlDialect(), new Expected(Set.of("sql", "plpgsql"), "sql", "integer",
                        "SELECT a + b", "BEGIN total := total + n; END",
                        "CREATE FUNCTION \"s\".\"f\"(\"a\" integer, \"b\" integer) RETURNS integer LANGUAGE sql"
                                + " AS $$SELECT a + b$$",
                        "CREATE OR REPLACE FUNCTION \"s\".\"f\"(\"a\" integer, \"b\" integer) RETURNS integer"
                                + " LANGUAGE sql AS $$SELECT a + b$$",
                        "CREATE PROCEDURE \"s\".\"p\"(\"n\" integer, INOUT \"total\" integer) LANGUAGE sql"
                                + " AS $$BEGIN total := total + n; END$$",
                        "DROP FUNCTION IF EXISTS \"s\".\"f\"(integer, integer)", "DROP PROCEDURE \"s\".\"p\"")),
                of("oracle, no version", new OracleDialect(), new Expected(Set.of("plsql"), "plsql", "NUMBER",
                        "BEGIN RETURN a + b; END;", "BEGIN total := total + n; END;",
                        "CREATE FUNCTION \"s\".\"f\"(\"a\" IN NUMBER, \"b\" IN NUMBER) RETURN NUMBER AS"
                                + " BEGIN RETURN a + b; END;",
                        "CREATE OR REPLACE FUNCTION \"s\".\"f\"(\"a\" IN NUMBER, \"b\" IN NUMBER) RETURN NUMBER AS"
                                + " BEGIN RETURN a + b; END;",
                        "CREATE PROCEDURE \"s\".\"p\"(\"n\" IN NUMBER, \"total\" IN OUT NUMBER) AS"
                                + " BEGIN total := total + n; END;",
                        "DROP FUNCTION \"s\".\"f\"", "DROP PROCEDURE \"s\".\"p\"")),
                of("oracle 23", new OracleDialect(version(23, 0)), new Expected(Set.of("plsql"), "plsql", "NUMBER",
                        "BEGIN RETURN a + b; END;", "BEGIN total := total + n; END;",
                        "CREATE FUNCTION \"s\".\"f\"(\"a\" IN NUMBER, \"b\" IN NUMBER) RETURN NUMBER AS"
                                + " BEGIN RETURN a + b; END;",
                        "CREATE OR REPLACE FUNCTION \"s\".\"f\"(\"a\" IN NUMBER, \"b\" IN NUMBER) RETURN NUMBER AS"
                                + " BEGIN RETURN a + b; END;",
                        "CREATE PROCEDURE \"s\".\"p\"(\"n\" IN NUMBER, \"total\" IN OUT NUMBER) AS"
                                + " BEGIN total := total + n; END;",
                        "DROP FUNCTION IF EXISTS \"s\".\"f\"", "DROP PROCEDURE \"s\".\"p\"")),
                of("mssql", new MicrosoftSqlServerDialect(), new Expected(Set.of("tsql"), "tsql", "INT",
                        "BEGIN RETURN @a + @b END", "BEGIN SET @total = @total + @n END",
                        "CREATE FUNCTION \"s\".\"f\"(@a INT, @b INT) RETURNS INT AS BEGIN RETURN @a + @b END",
                        "CREATE OR ALTER FUNCTION \"s\".\"f\"(@a INT, @b INT) RETURNS INT AS BEGIN RETURN @a + @b END",
                        "CREATE PROCEDURE \"s\".\"p\" @n INT, @total INT OUTPUT AS BEGIN SET @total = @total + @n END",
                        "DROP FUNCTION IF EXISTS \"s\".\"f\"", "DROP PROCEDURE \"s\".\"p\"")),
                of("mysql", new MySqlDialect(), new Expected(Set.of("sql"), "sql", "INT", "DETERMINISTIC RETURN a + b",
                        "SET total = total + n",
                        "CREATE FUNCTION `s`.`f`(`a` INT, `b` INT) RETURNS INT DETERMINISTIC RETURN a + b",
                        "CREATE FUNCTION `s`.`f`(`a` INT, `b` INT) RETURNS INT DETERMINISTIC RETURN a + b",
                        "CREATE PROCEDURE `s`.`p`(IN `n` INT, INOUT `total` INT) SET total = total + n",
                        "DROP FUNCTION IF EXISTS `s`.`f`", "DROP PROCEDURE `s`.`p`")),
                of("mariadb", new MariaDBDialect(), new Expected(Set.of("sql"), "sql", "INT",
                        "DETERMINISTIC RETURN a + b", "SET total = total + n",
                        "CREATE FUNCTION `s`.`f`(`a` INT, `b` INT) RETURNS INT DETERMINISTIC RETURN a + b",
                        "CREATE OR REPLACE FUNCTION `s`.`f`(`a` INT, `b` INT) RETURNS INT DETERMINISTIC RETURN a + b",
                        "CREATE PROCEDURE `s`.`p`(IN `n` INT, INOUT `total` INT) SET total = total + n",
                        "DROP FUNCTION IF EXISTS `s`.`f`", "DROP PROCEDURE `s`.`p`")),
                of("h2", new H2Dialect(), Expected.none()),
                of("duckdb", new DuckDbDialect(), Expected.none()),
                of("sqlite", new SqliteDialect(), Expected.none()),
                of("derby", new DerbyDialect(), Expected.none()),
                of("clickhouse", new ClickHouseDialect(), Expected.none()),
                of("hive", new HiveDialect(), Expected.none()),
                of("impala", new ImpalaDialect(), Expected.none()),
                of("ansi", new AnsiDialect(), Expected.none()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void createAndDropAFunctionAndAProcedure(Dialect dialect, Expected expected) {
        DdlGenerator ddl = dialect.ddlGenerator();
        RoutineDefinition function = new RoutineDefinition("s", "f",
                List.of(RoutineParameter.in("a", expected.types()), RoutineParameter.in("b", expected.types())),
                expected.types(), expected.language(), expected.body());
        RoutineDefinition procedure = new RoutineDefinition("s", "p",
                List.of(RoutineParameter.in("n", expected.types()),
                        new RoutineParameter("total", ParameterMode.INOUT, expected.types())),
                null, expected.language(), expected.procedureBody());

        assertThat(ddl.routineLanguages()).isEqualTo(expected.languages());
        assertThat(ddl.createFunction(function, false)).isEqualTo(Optional.ofNullable(expected.createFunction()));
        assertThat(ddl.createFunction(function, true))
                .isEqualTo(Optional.ofNullable(expected.createOrReplaceFunction()));
        assertThat(ddl.createProcedure(procedure, false)).isEqualTo(Optional.ofNullable(expected.createProcedure()));
        assertThat(ddl.dropFunction(new RoutineSignature("s", "f", List.of(expected.types(), expected.types())), true))
                .isEqualTo(Optional.ofNullable(expected.dropFunction()));
        assertThat(ddl.dropProcedure(new RoutineSignature("s", "p", null), false))
                .isEqualTo(Optional.ofNullable(expected.dropProcedure()));
        assertThat(ddl.createFunction(new RoutineDefinition("s", "f", List.of(), "INT", "cobol", "x"), false))
                .as("a body in a language the dialect does not take").isEmpty();
    }

    /** SQL Server's function has no OUT parameter, and MySQL's none but IN. */
    @Test
    void aFunctionWithAnOutParameterWhereTheDatabaseHasNone() {
        RoutineDefinition tsql = new RoutineDefinition("s", "f",
                List.of(new RoutineParameter("r", ParameterMode.OUT, "INT")), "INT", "tsql", "BEGIN RETURN 1 END");
        RoutineDefinition sql = new RoutineDefinition("s", "f",
                List.of(new RoutineParameter("r", ParameterMode.OUT, "INT")), "INT", "sql", "RETURN 1");

        assertThat(new MicrosoftSqlServerDialect().ddlGenerator().createFunction(tsql, false)).isEmpty();
        assertThat(new MySqlDialect().ddlGenerator().createFunction(sql, false)).isEmpty();
        assertThat(new MariaDBDialect().ddlGenerator().createFunction(sql, false)).isEmpty();
    }

    private static Arguments of(String name, Dialect dialect, Expected expected) {
        return Arguments.of(Named.of(name, dialect), expected);
    }

    private static DialectInitData version(int major, int minor) {
        return DialectInitData.ansiDefaults().withVersion(major, minor);
    }
}
