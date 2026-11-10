/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.dialect.db.mssqlserver.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.junit.jupiter.api.Test;

/**
 * National text keeps its national type on SQL Server, whose VARCHAR(n) counts bytes; long text and binary take their
 * MAX form, which a large object without a length has no other name for.
 */
class MssqlNativeTypeTest {

    private final MicrosoftSqlServerDialect dialect = new MicrosoftSqlServerDialect();

    private static ColumnMetaData meta(JDBCType jdbc, OptionalInt size) {
        return new ColumnMetaDataRecord(jdbc, jdbc.getName(), size, OptionalInt.empty(), OptionalInt.empty(),
                ColumnMetaData.Nullability.NULLABLE, OptionalInt.empty(), Optional.empty(), Optional.empty(),
                ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    @Test
    void nationalTextStaysNational() {
        assertThat(dialect.nativeType(meta(JDBCType.NVARCHAR, OptionalInt.of(400)))).isEqualTo("NVARCHAR(400)");
        assertThat(dialect.nativeType(meta(JDBCType.NCHAR, OptionalInt.of(3)))).isEqualTo("NCHAR(3)");
        assertThat(dialect.nativeType(meta(JDBCType.NVARCHAR, OptionalInt.of(5000)))).isEqualTo("NVARCHAR(MAX)");
    }

    @Test
    void longTextAndBinaryAreMax() {
        assertThat(dialect.nativeType(meta(JDBCType.NCLOB, OptionalInt.empty()))).isEqualTo("NVARCHAR(MAX)");
        assertThat(dialect.nativeType(meta(JDBCType.CLOB, OptionalInt.empty()))).isEqualTo("VARCHAR(MAX)");
        assertThat(dialect.nativeType(meta(JDBCType.BLOB, OptionalInt.empty()))).isEqualTo("VARBINARY(MAX)");
        assertThat(dialect.nativeType(meta(JDBCType.VARBINARY, OptionalInt.empty()))).isEqualTo("VARBINARY(MAX)");
        assertThat(dialect.nativeType(meta(JDBCType.VARBINARY, OptionalInt.of(16)))).isEqualTo("VARBINARY(16)");
        assertThat(dialect.nativeType(meta(JDBCType.VARCHAR, OptionalInt.of(9000)))).isEqualTo("VARCHAR(MAX)");
    }

    @Test
    void otherTypesStayAsTheGeneratorWritesThem() {
        assertThat(dialect.nativeType(meta(JDBCType.VARCHAR, OptionalInt.of(40)))).isEqualTo("VARCHAR(40)");
        assertThat(dialect.nativeType(meta(JDBCType.INTEGER, OptionalInt.empty()))).isEqualTo("INTEGER");
    }
}
