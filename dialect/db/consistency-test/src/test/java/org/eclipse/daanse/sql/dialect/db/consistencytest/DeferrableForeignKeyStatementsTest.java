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
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.Deferrability;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What each dialect writes for the foreign key {@code fk} from {@code s.c (p_id)} to {@code s.p (id)}
 * by when it is checked. A key checked at once is the plain clause of every dialect; a deferrable
 * one is the plain clause and its deferrability, or nothing where the database checks every key at
 * once, as {@link DdlGenerator#supportsDeferrableForeignKeys()} says.
 */
class DeferrableForeignKeyStatementsTest {

    private static final TableReference CHILD = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "s")), "c");

    private static final TableReference PARENT = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "s")), "p");

    static Stream<Arguments> dialects() {
        return Stream.of(of("postgresql", new PostgreSqlDialect(), true), of("oracle", new OracleDialect(), true),
                of("sqlite", new SqliteDialect(), true), of("derby", new DerbyDialect(), true),
                of("mssql", new MicrosoftSqlServerDialect(), false), of("mysql", new MySqlDialect(), false),
                of("mariadb", new MariaDBDialect(), false), of("h2", new H2Dialect(), false),
                of("duckdb", new DuckDbDialect(), false), of("clickhouse", new ClickHouseDialect(), false),
                of("hive", new HiveDialect(), false), of("impala", new ImpalaDialect(), false),
                of("ansi", new AnsiDialect(), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void aForeignKeyByWhenItIsChecked(Dialect dialect, boolean deferrable) {
        DdlGenerator ddl = dialect.ddlGenerator();
        String plain = ddl.foreignKeyClause("fk", List.of("p_id"), PARENT, List.of("id"), null, null);

        assertThat(ddl.supportsDeferrableForeignKeys()).as("supportsDeferrableForeignKeys").isEqualTo(deferrable);
        assertThat(clause(ddl, Deferrability.NOT_DEFERRABLE)).contains(plain);
        assertThat(clause(ddl, Deferrability.INITIALLY_DEFERRED))
                .isEqualTo(deferrable ? Optional.of(plain + " DEFERRABLE INITIALLY DEFERRED") : Optional.empty());
        assertThat(clause(ddl, Deferrability.INITIALLY_IMMEDIATE))
                .isEqualTo(deferrable ? Optional.of(plain + " DEFERRABLE INITIALLY IMMEDIATE") : Optional.empty());
        assertThat(ddl.addForeignKeyConstraint(CHILD, "fk", List.of("p_id"), PARENT, List.of("id"), null, null,
                Deferrability.INITIALLY_DEFERRED))
                .isEqualTo(deferrable && ddl.supportsAddForeignKeyConstraint()
                        ? Optional.of(ddl.addForeignKeyConstraint(CHILD, "fk", List.of("p_id"), PARENT, List.of("id"),
                                null, null) + " DEFERRABLE INITIALLY DEFERRED")
                        : Optional.empty());
    }

    @Test
    void postgresqlWritesTheKeyAndWhenItIsChecked() {
        assertThat(new PostgreSqlDialect().ddlGenerator().addForeignKeyConstraint(CHILD, "fk", List.of("p_id"), PARENT,
                List.of("id"), "CASCADE", null, Deferrability.INITIALLY_DEFERRED))
                .contains("ALTER TABLE \"s\".\"c\" ADD CONSTRAINT \"fk\" FOREIGN KEY (\"p_id\") REFERENCES \"s\".\"p\""
                        + " (\"id\") ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED");
    }

    private static Optional<String> clause(DdlGenerator ddl, Deferrability deferrability) {
        return ddl.foreignKeyClause("fk", List.of("p_id"), PARENT, List.of("id"), null, null, deferrability);
    }

    private static Arguments of(String name, Dialect dialect, boolean deferrable) {
        return Arguments.of(Named.of(name, dialect), deferrable);
    }
}
