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
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What each dialect writes to create and drop the materialized view {@code s.mv}. A dialect whose
 * database keeps no query result as a view writes nothing and says so by
 * {@link DdlGenerator#supportsMaterializedViews()}.
 */
class MaterializedViewStatementsTest {

    private static final TableReference VIEW = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "s")), "mv", TableReference.TYPE_VIEW);

    private static final String QUERY = "SELECT a FROM t";

    /** The statements expected of a dialect; all null where it has no materialized views. */
    record Expected(String create, String createIfNotExists, String drop, String dropIfExists) {

        static Expected none() {
            return new Expected(null, null, null, null);
        }
    }

    static Stream<Arguments> dialects() {
        return Stream.of(
                of("postgresql", new PostgreSqlDialect(), new Expected(
                        "CREATE MATERIALIZED VIEW \"s\".\"mv\" AS " + QUERY,
                        "CREATE MATERIALIZED VIEW IF NOT EXISTS \"s\".\"mv\" AS " + QUERY,
                        "DROP MATERIALIZED VIEW \"s\".\"mv\"", "DROP MATERIALIZED VIEW IF EXISTS \"s\".\"mv\"")),
                of("oracle, no version", new OracleDialect(), new Expected(
                        "CREATE MATERIALIZED VIEW \"s\".\"mv\" AS " + QUERY,
                        "CREATE MATERIALIZED VIEW \"s\".\"mv\" AS " + QUERY,
                        "DROP MATERIALIZED VIEW \"s\".\"mv\"", "DROP MATERIALIZED VIEW \"s\".\"mv\"")),
                of("oracle 23", new OracleDialect(version(23, 0)), new Expected(
                        "CREATE MATERIALIZED VIEW \"s\".\"mv\" AS " + QUERY,
                        "CREATE MATERIALIZED VIEW IF NOT EXISTS \"s\".\"mv\" AS " + QUERY,
                        "DROP MATERIALIZED VIEW \"s\".\"mv\"", "DROP MATERIALIZED VIEW IF EXISTS \"s\".\"mv\"")),
                of("mssql", new MicrosoftSqlServerDialect(), Expected.none()),
                of("mysql", new MySqlDialect(), Expected.none()),
                of("mariadb", new MariaDBDialect(), Expected.none()),
                of("h2", new H2Dialect(), Expected.none()),
                of("clickhouse", new ClickHouseDialect(), Expected.none()),
                of("duckdb", new DuckDbDialect(), Expected.none()),
                of("sqlite", new SqliteDialect(), Expected.none()),
                of("derby", new DerbyDialect(), Expected.none()),
                of("hive", new HiveDialect(), Expected.none()),
                of("impala", new ImpalaDialect(), Expected.none()),
                of("ansi", new AnsiDialect(), Expected.none()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void createAndDropAMaterializedView(Dialect dialect, Expected expected) {
        DdlGenerator ddl = dialect.ddlGenerator();

        assertThat(ddl.supportsMaterializedViews()).as("supportsMaterializedViews")
                .isEqualTo(expected.create() != null);
        assertThat(ddl.createMaterializedView(VIEW, QUERY, false)).isEqualTo(Optional.ofNullable(expected.create()));
        assertThat(ddl.createMaterializedView(VIEW, QUERY, true))
                .isEqualTo(Optional.ofNullable(expected.createIfNotExists()));
        assertThat(ddl.dropMaterializedView(VIEW, false)).isEqualTo(Optional.ofNullable(expected.drop()));
        assertThat(ddl.dropMaterializedView(VIEW, true)).isEqualTo(Optional.ofNullable(expected.dropIfExists()));
    }

    private static Arguments of(String name, Dialect dialect, Expected expected) {
        return Arguments.of(Named.of(name, dialect), expected);
    }

    private static DialectInitData version(int major, int minor) {
        return DialectInitData.ansiDefaults().withVersion(major, minor);
    }
}
