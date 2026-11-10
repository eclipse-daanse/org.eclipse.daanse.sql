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
package org.eclipse.daanse.sql.dialect.db.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.junit.jupiter.api.Test;

/** PostgreSQL has no CLOB: long text is TEXT; a BLOB is an OID as pgjdbc binds it, other binary BYTEA. */
class PostgreSqlNativeTypeTest {

    private final PostgreSqlDialect dialect = new PostgreSqlDialect();

    private static ColumnMetaData meta(JDBCType jdbc, OptionalInt size) {
        return new ColumnMetaDataRecord(jdbc, jdbc.getName(), size, OptionalInt.empty(), OptionalInt.empty(),
                ColumnMetaData.Nullability.NULLABLE, OptionalInt.empty(), Optional.empty(), Optional.empty(),
                ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    @Test
    void largeObjectsHavePostgreSqlNames() {
        assertThat(dialect.nativeType(meta(JDBCType.NCLOB, OptionalInt.empty()))).isEqualTo("TEXT");
        assertThat(dialect.nativeType(meta(JDBCType.CLOB, OptionalInt.empty()))).isEqualTo("TEXT");
        assertThat(dialect.nativeType(meta(JDBCType.BLOB, OptionalInt.empty()))).isEqualTo("OID");
        assertThat(dialect.nativeType(meta(JDBCType.VARBINARY, OptionalInt.empty()))).isEqualTo("BYTEA");
    }

    @Test
    void otherTypesStayAsTheGeneratorWritesThem() {
        assertThat(dialect.nativeType(meta(JDBCType.NVARCHAR, OptionalInt.of(40)))).isEqualTo("VARCHAR(40)");
        assertThat(dialect.nativeType(meta(JDBCType.INTEGER, OptionalInt.empty()))).isEqualTo("INTEGER");
    }
}
