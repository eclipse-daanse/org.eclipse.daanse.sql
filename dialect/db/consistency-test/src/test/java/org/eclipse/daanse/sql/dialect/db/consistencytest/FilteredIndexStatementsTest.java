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
import java.util.stream.Stream;

import org.eclipse.daanse.sql.dialect.api.Dialect;
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
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What each dialect writes for the unique index {@code ix} on {@code s.t (a)} over the rows where
 * {@code a > 0}. A dialect whose database has no partial indexes writes nothing for it and says so
 * by {@link DdlGenerator#supportsFilteredIndexes()}; without a condition every dialect writes its
 * plain index.
 */
class FilteredIndexStatementsTest {

    private static final TableReference TABLE = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "s")), "t");

    private static final String CONDITION = "a > 0";

    static Stream<Arguments> dialects() {
        String partial = "CREATE UNIQUE INDEX \"ix\" ON \"s\".\"t\" (\"a\") WHERE a > 0";
        return Stream.of(of("postgresql", new PostgreSqlDialect(), partial),
                of("mssql", new MicrosoftSqlServerDialect(), partial),
                of("sqlite", new SqliteDialect(), partial),
                of("oracle", new OracleDialect(), null),
                of("mysql", new MySqlDialect(), null),
                of("mariadb", new MariaDBDialect(), null),
                of("h2", new H2Dialect(), null),
                of("clickhouse", new ClickHouseDialect(), null),
                of("duckdb", new DuckDbDialect(), null),
                of("derby", new DerbyDialect(), null),
                of("hive", new HiveDialect(), null),
                of("impala", new ImpalaDialect(), null),
                of("ansi", new AnsiDialect(), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void anIndexOverTheRowsOfACondition(Dialect dialect, String expected) {
        DdlGenerator ddl = dialect.ddlGenerator();

        assertThat(ddl.supportsFilteredIndexes()).as("supportsFilteredIndexes").isEqualTo(expected != null);
        assertThat(ddl.createIndex("ix", TABLE, List.of("a"), true, CONDITION, false))
                .isEqualTo(Optional.ofNullable(expected));
        assertThat(ddl.createIndex("ix", TABLE, List.of("a"), true, null, false))
                .contains(ddl.createIndex("ix", TABLE, List.of("a"), true, false));
    }

    private static Arguments of(String name, Dialect dialect, String expected) {
        return Arguments.of(Named.of(name, dialect), expected);
    }
}
