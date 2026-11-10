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

import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.capability.DdlCapabilities;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.dialect.db.clickhouse.ClickHouseDialect;
import org.eclipse.daanse.sql.dialect.db.derby.DerbyDialect;
import org.eclipse.daanse.sql.dialect.db.duckdb.DuckDbDialect;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.eclipse.daanse.sql.dialect.db.mariadb.MariaDBDialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.eclipse.daanse.sql.dialect.db.sqlite.SqliteDialect;
import org.eclipse.daanse.sql.dialect.db.testsupport.GeneratorTestSupport;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * For every dialect and rename operation, checks {@code supportsRename*()}
 * agrees with the {@link DdlGenerator} method: flag true → non-blank SQL,
 * flag false → null. Own module, not {@code test-support}, to avoid a
 * reactor cycle (see this module's pom).
 */
class RenameFlagsAgreementTest {

    private static final SchemaReference SCHEMA = new SchemaReference(Optional.empty(), "PUBLIC");
    private static final TableReference TABLE = new TableReference(Optional.of(SCHEMA), "EMPLOYEES",
            TableReference.TYPE_TABLE);
    private static final TableReference VIEW = new TableReference(Optional.of(SCHEMA), "V_EMP",
            TableReference.TYPE_VIEW);
    private static final ColumnMetaData SOME_META = GeneratorTestSupport.columnMeta(JDBCType.DECIMAL,
            OptionalInt.of(12), ColumnMetaData.Nullability.NULLABLE);

    /** Every concrete dialect, named by {@link Dialect#name()} for the test report. */
    static Stream<Named<Dialect>> dialects() {
        return Stream.<Dialect>of(new PostgreSqlDialect(), new OracleDialect(), new MicrosoftSqlServerDialect(),
                new MySqlDialect(), new MariaDBDialect(), new H2Dialect(), new SqliteDialect(), new DuckDbDialect(),
                new DerbyDialect(), new ClickHouseDialect())
                .map(d -> Named.of(d.name(), d));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void renameFlagsAgreeWithGenerators(Dialect d) {
        DdlGenerator g = d.ddlGenerator();
        DdlCapabilities caps = d.getDdlCapabilities();

        assertAgreement(d, "renameTable", caps.renameTable(), g.renameTable(TABLE, "N"));
        assertAgreement(d, "renameColumn", caps.renameColumn(), g.renameColumn(TABLE, "A", "B", SOME_META));
        assertAgreement(d, "renameIndex", caps.renameIndex(), g.renameIndex("I", "J", TABLE));
        assertAgreement(d, "renameConstraint", caps.renameConstraint(), g.renameConstraint(TABLE, "C", "D"));
        assertAgreement(d, "renameView", caps.renameView(), g.renameView(VIEW, "N"));
        assertAgreement(d, "renameTrigger", caps.renameTrigger(), g.renameTrigger("TR", TABLE, "N"));
        assertAgreement(d, "renameSequence", caps.renameSequence() && caps.sequences(),
                g.renameSequence("S", "Q", "N").orElse(null));
    }

    private static void assertAgreement(Dialect d, String operation, boolean flag, String sql) {
        if (flag) {
            assertThat(sql)
                    .as("%s on %s: flag says supported, so the generator must render something", operation, d.name())
                    .isNotBlank();
        } else {
            assertThat(sql)
                    .as("%s on %s: flag says unsupported, so the generator must return null", operation, d.name())
                    .isNull();
        }
    }
}
