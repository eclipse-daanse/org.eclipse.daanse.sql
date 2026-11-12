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
 * What each dialect writes to comment on the view {@code s.v} and on its column {@code c}. A dialect
 * whose database keeps no comment on a view writes nothing for either and says so by
 * {@link DdlGenerator#supportsCommentOnView()}.
 */
class ViewCommentStatementsTest {

    private static final TableReference VIEW = new TableReference(
            Optional.of(new SchemaReference(Optional.empty(), "s")), "v", TableReference.TYPE_VIEW);

    /** The statements expected of a dialect; both null where it keeps no comment on a view. */
    record Expected(String onView, String onColumn) {

        static Expected none() {
            return new Expected(null, null);
        }
    }

    static Stream<Arguments> dialects() {
        Expected standard = new Expected("COMMENT ON VIEW \"s\".\"v\" IS 'it''s'",
                "COMMENT ON COLUMN \"s\".\"v\".\"c\" IS 'it''s'");
        return Stream.of(of("postgresql", new PostgreSqlDialect(), standard),
                of("h2", new H2Dialect(), standard),
                of("duckdb", new DuckDbDialect(), standard),
                of("ansi", new AnsiDialect(), standard),
                of("oracle", new OracleDialect(), new Expected("COMMENT ON TABLE \"s\".\"v\" IS 'it''s'",
                        "COMMENT ON COLUMN \"s\".\"v\".\"c\" IS 'it''s'")),
                of("mssql", new MicrosoftSqlServerDialect(), new Expected(
                        "IF EXISTS (SELECT 1 FROM sys.extended_properties WHERE class = 1 AND major_id = OBJECT_ID(N'\"s\".\"v\"') AND minor_id = 0 AND name = N'MS_Description') EXEC sys.sp_updateextendedproperty @name = N'MS_Description', @value = N'it''s', @level0type = N'SCHEMA', @level0name = N's', @level1type = N'VIEW', @level1name = N'v' ELSE EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'it''s', @level0type = N'SCHEMA', @level0name = N's', @level1type = N'VIEW', @level1name = N'v'",
                        "IF EXISTS (SELECT 1 FROM sys.extended_properties WHERE class = 1 AND major_id = OBJECT_ID(N'\"s\".\"v\"') AND minor_id = COLUMNPROPERTY(OBJECT_ID(N'\"s\".\"v\"'), N'c', 'ColumnId') AND name = N'MS_Description') EXEC sys.sp_updateextendedproperty @name = N'MS_Description', @value = N'it''s', @level0type = N'SCHEMA', @level0name = N's', @level1type = N'VIEW', @level1name = N'v', @level2type = N'COLUMN', @level2name = N'c' ELSE EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'it''s', @level0type = N'SCHEMA', @level0name = N's', @level1type = N'VIEW', @level1name = N'v', @level2type = N'COLUMN', @level2name = N'c'")),
                of("mysql", new MySqlDialect(), Expected.none()),
                of("mariadb", new MariaDBDialect(), Expected.none()),
                of("clickhouse", new ClickHouseDialect(), Expected.none()),
                of("hive", new HiveDialect(), Expected.none()),
                of("impala", new ImpalaDialect(), Expected.none()),
                of("sqlite", new SqliteDialect(), Expected.none()),
                of("derby", new DerbyDialect(), Expected.none()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void commentsOnAViewAndItsColumn(Dialect dialect, Expected expected) {
        DdlGenerator ddl = dialect.ddlGenerator();

        assertThat(ddl.supportsCommentOnView()).as("supportsCommentOnView").isEqualTo(expected.onView() != null);
        assertThat(ddl.commentOnTable(VIEW, "it's")).isEqualTo(Optional.ofNullable(expected.onView()));
        assertThat(ddl.commentOnColumn(VIEW, "c", "it's", null)).isEqualTo(Optional.ofNullable(expected.onColumn()));
    }

    private static Arguments of(String name, Dialect dialect, Expected expected) {
        return Arguments.of(Named.of(name, dialect), expected);
    }
}
