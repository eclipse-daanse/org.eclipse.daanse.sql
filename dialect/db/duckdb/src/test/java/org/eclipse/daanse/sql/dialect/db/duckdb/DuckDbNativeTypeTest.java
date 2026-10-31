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
package org.eclipse.daanse.sql.dialect.db.duckdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.junit.jupiter.api.Test;

/** DuckDB knows no CLOB: long text is VARCHAR, binary BLOB. */
class DuckDbNativeTypeTest {

    private final DuckDbDialect dialect = new DuckDbDialect();

    private static ColumnMetaData meta(JDBCType jdbc, OptionalInt size) {
        return new ColumnMetaDataRecord(jdbc, jdbc.getName(), size, OptionalInt.empty(), OptionalInt.empty(),
                ColumnMetaData.Nullability.NULLABLE, OptionalInt.empty(), Optional.empty(), Optional.empty(),
                ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    @Test
    void largeObjectsHaveDuckDbNames() {
        assertThat(dialect.nativeType(meta(JDBCType.NCLOB, OptionalInt.empty()))).isEqualTo("VARCHAR");
        assertThat(dialect.nativeType(meta(JDBCType.CLOB, OptionalInt.empty()))).isEqualTo("VARCHAR");
        assertThat(dialect.nativeType(meta(JDBCType.BLOB, OptionalInt.empty()))).isEqualTo("BLOB");
    }

    @Test
    void otherTypesStayAsTheGeneratorWritesThem() {
        assertThat(dialect.nativeType(meta(JDBCType.NVARCHAR, OptionalInt.of(40)))).isEqualTo("VARCHAR(40)");
        assertThat(dialect.nativeType(meta(JDBCType.INTEGER, OptionalInt.empty()))).isEqualTo("INTEGER");
    }
}
