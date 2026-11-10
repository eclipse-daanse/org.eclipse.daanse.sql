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
import org.eclipse.daanse.sql.dialect.db.mariadb.MariaDBDialect;
import org.eclipse.daanse.sql.dialect.db.mysql.MySqlDialect;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Locks the null/non-null threshold of the metadata-free 3-arg
 * {@code renameColumn}: MySQL below 8.0, MariaDB below 10.5.2 return null.
 * MariaDB needs its own gate — its 10.x numbering would otherwise satisfy
 * MySQL's inherited "8.0+" check.
 */
class MySqlFamilyRenameColumnVersionThresholdTest {

    private static final SchemaReference SCHEMA = new SchemaReference(Optional.empty(), "appdb");
    private static final TableReference TABLE = new TableReference(Optional.of(SCHEMA), "EMPLOYEES",
            TableReference.TYPE_TABLE);

    private static Dialect mysql(int major, int minor) {
        return new MySqlDialect(
                DialectInitData.ansiDefaults().withQuoteIdentifierString("`").withVersion(major, minor));
    }

    private static Dialect mariadb(int major, int minor) {
        return new MariaDBDialect(
                DialectInitData.ansiDefaults().withQuoteIdentifierString("`").withVersion(major, minor));
    }

    record VersionCase(String label, Dialect dialect, boolean nativeRenameColumnSupported) {
        @Override
        public String toString() {
            return label;
        }
    }

    static Stream<VersionCase> cases() {
        return Stream.of(
                new VersionCase("mysql unknown version", new MySqlDialect(), true),
                new VersionCase("mysql 8.0", mysql(8, 0), true),
                new VersionCase("mysql 5.7", mysql(5, 7), false),
                new VersionCase("mariadb unknown version", new MariaDBDialect(), true),
                new VersionCase("mariadb 10.6", mariadb(10, 6), true),
                new VersionCase("mariadb 10.5", mariadb(10, 5), true),
                new VersionCase("mariadb 10.4", mariadb(10, 4), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void renameColumn_without_metadata_follows_the_native_threshold(VersionCase c) {
        DdlGenerator g = c.dialect().ddlGenerator();

        // Flag describes the metadata overload, not this one — never moves with version.
        assertThat(g.supportsRenameColumn()).as("%s: supportsRenameColumn stays true", c.label()).isTrue();

        String sql = g.renameColumn(TABLE, "OLD", "NEW");
        if (c.nativeRenameColumnSupported()) {
            assertThat(sql).as("%s: native RENAME COLUMN expected", c.label()).isNotBlank()
                    .contains("RENAME COLUMN");
        } else {
            assertThat(sql).as("%s: below the native threshold, metadata-free rename must be null", c.label())
                    .isNull();
        }
    }
}
