/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.jdbc.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A column default as MySQL 8 keeps it in {@code information_schema.COLUMNS} ({@code COLUMN_DEFAULT},
 * {@code DATA_TYPE}, {@code EXTRA}), written as MySQL writes it in SQL.
 */
class MySqlColumnDefaultTest {

    @Test
    void aTextOrDateLiteralIsQuoted() {
        assertThat(MySqlMetadataProvider.columnDefault("NEW", "varchar", "")).isEqualTo("'NEW'");
        assertThat(MySqlMetadataProvider.columnDefault("it's", "varchar", "")).isEqualTo("'it''s'");
        assertThat(MySqlMetadataProvider.columnDefault("a\\b", "varchar", "")).isEqualTo("'a\\\\b'");
        assertThat(MySqlMetadataProvider.columnDefault("", "varchar", "")).isEqualTo("''");
        assertThat(MySqlMetadataProvider.columnDefault("2020-01-02", "date", "")).isEqualTo("'2020-01-02'");
        assertThat(MySqlMetadataProvider.columnDefault("red", "enum", "")).isEqualTo("'red'");
    }

    @Test
    void aNumberABitAndAHexadecimalLiteralStayAsTheyAre() {
        assertThat(MySqlMetadataProvider.columnDefault("5", "int", "")).isEqualTo("5");
        assertThat(MySqlMetadataProvider.columnDefault("1.50", "decimal", "")).isEqualTo("1.50");
        assertThat(MySqlMetadataProvider.columnDefault("b'1'", "bit", "")).isEqualTo("b'1'");
        assertThat(MySqlMetadataProvider.columnDefault("0x6162", "varbinary", "")).isEqualTo("0x6162");
    }

    @Test
    void anExpressionIsGivenAsMySqlKeepsItWithItsEscapesUndone() {
        assertThat(MySqlMetadataProvider.columnDefault("CURRENT_TIMESTAMP", "timestamp", "DEFAULT_GENERATED"))
                .isEqualTo("CURRENT_TIMESTAMP");
        assertThat(MySqlMetadataProvider.columnDefault("CURRENT_TIMESTAMP", "timestamp",
                "DEFAULT_GENERATED on update CURRENT_TIMESTAMP")).isEqualTo("CURRENT_TIMESTAMP");
        assertThat(MySqlMetadataProvider.columnDefault("(1 + 1)", "int", "DEFAULT_GENERATED")).isEqualTo("(1 + 1)");
        assertThat(MySqlMetadataProvider.columnDefault("lower(_utf8mb4\\'ABC\\')", "varchar", "DEFAULT_GENERATED"))
                .isEqualTo("lower(_utf8mb4'ABC')");
    }

    @Test
    void noDefaultIsNone() {
        assertThat(MySqlMetadataProvider.columnDefault(null, "varchar", "")).isNull();
    }
}
