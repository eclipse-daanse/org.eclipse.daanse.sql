/*
* Copyright (c) 2024 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   SmartCity Jena - initial
*   Stefan Bischof (bipolis.org) - initial
*/
package org.eclipse.daanse.sql.jdbc.api.schema;

import org.eclipse.daanse.sql.model.schema.Named;

import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

public interface FunctionColumn extends Named {

    ColumnType columnType();

    JDBCType dataType();

    String typeName();

    OptionalInt precision();

    OptionalInt scale();

    OptionalInt radix();

    /** @return the nullability */
    Nullability nullable();

    Optional<String> remarks();

    OptionalInt charOctetLength();

    int ordinalPosition();

    enum ColumnType {
        UNKNOWN(DatabaseMetaData.functionColumnUnknown), IN(DatabaseMetaData.functionColumnIn),
        INOUT(DatabaseMetaData.functionColumnInOut), OUT(DatabaseMetaData.functionColumnOut),
        RETURN(DatabaseMetaData.functionReturn), RESULT(DatabaseMetaData.functionColumnResult);

        private final int value;

        ColumnType(int value) {
            this.value = value;
        }

        /**
         * Raw JDBC integer constant for this enum value (per
         * {@link java.sql.DatabaseMetaData}).
         */
        public int getValue() {
            return value;
        }

        /** Look up the enum constant matching the JDBC int code. Throws if no match. */
        public static ColumnType of(int value) {
            return Stream.of(ColumnType.values()).filter(t -> t.value == value).findFirst().orElse(UNKNOWN);
        }
    }

    enum Nullability {
        NO_NULLS(DatabaseMetaData.functionNoNulls), NULLABLE(DatabaseMetaData.functionNullable),
        UNKNOWN(DatabaseMetaData.functionNullableUnknown);

        private final int value;

        Nullability(int value) {
            this.value = value;
        }

        /**
         * Raw JDBC integer constant for this enum value (per
         * {@link java.sql.DatabaseMetaData}).
         */
        public int getValue() {
            return value;
        }

        /** Look up the enum constant matching the JDBC int code. Throws if no match. */
        public static Nullability of(int value) {
            return Stream.of(Nullability.values()).filter(n -> n.value == value).findFirst().orElse(UNKNOWN);
        }
    }
}
