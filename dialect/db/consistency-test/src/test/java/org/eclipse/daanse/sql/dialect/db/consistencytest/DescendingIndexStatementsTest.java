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
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.IndexColumn;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.IndexDefinition;
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
 * What each dialect writes for the index {@code ix} on {@code s.t (a DESC, b)}. A dialect whose
 * database keeps no order in an index writes nothing for it and says so by
 * {@link DdlGenerator#supportsDescendingIndexColumns()}; an index of ascending columns is the plain
 * index of every dialect.
 */
class DescendingIndexStatementsTest {

    private static final TableReference TABLE = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "s")), "t");

    private static final IndexDefinition DESCENDING = new IndexDefinition("ix", TABLE,
            List.of(IndexColumn.descending("a"), IndexColumn.ascending("b")), false, null);

    private static final IndexDefinition ASCENDING = new IndexDefinition("ix", TABLE,
            List.of(IndexColumn.ascending("a"), IndexColumn.ascending("b")), false, null);

    static Stream<Arguments> dialects() {
        String quoted = "CREATE INDEX \"ix\" ON \"s\".\"t\" (\"a\" DESC, \"b\")";
        String backticked = "CREATE INDEX `ix` ON `s`.`t` (`a` DESC, `b`)";
        return Stream.of(of("postgresql", new PostgreSqlDialect(), quoted),
                of("oracle", new OracleDialect(), quoted),
                of("mssql", new MicrosoftSqlServerDialect(), quoted),
                of("mysql", new MySqlDialect(), backticked),
                of("mysql 5.7", new MySqlDialect(version(5, 7)), null),
                of("mariadb", new MariaDBDialect(), backticked),
                of("mariadb 10.6", new MariaDBDialect(version(10, 6)), null),
                of("h2", new H2Dialect(), quoted),
                of("sqlite", new SqliteDialect(), quoted),
                of("derby", new DerbyDialect(), quoted),
                of("duckdb", new DuckDbDialect(), null),
                of("clickhouse", new ClickHouseDialect(), null),
                of("hive", new HiveDialect(), null),
                of("impala", new ImpalaDialect(), null),
                of("ansi", new AnsiDialect(), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void anIndexWithADescendingColumn(Dialect dialect, String expected) {
        DdlGenerator ddl = dialect.ddlGenerator();

        assertThat(ddl.supportsDescendingIndexColumns()).as("supportsDescendingIndexColumns")
                .isEqualTo(expected != null);
        assertThat(ddl.createIndex(DESCENDING, false)).isEqualTo(Optional.ofNullable(expected));
        assertThat(ddl.createIndex(ASCENDING, false))
                .contains(ddl.createIndex("ix", TABLE, List.of("a", "b"), false, false));
    }

    private static Arguments of(String name, Dialect dialect, String expected) {
        return Arguments.of(Named.of(name, dialect), expected);
    }

    private static DialectInitData version(int major, int minor) {
        return DialectInitData.ansiDefaults().withVersion(major, minor);
    }
}
