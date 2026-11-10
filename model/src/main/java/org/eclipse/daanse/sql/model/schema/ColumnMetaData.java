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
package org.eclipse.daanse.sql.model.schema;

import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

public interface ColumnMetaData {

    JDBCType dataType();

    String typeName();

    OptionalInt columnSize();

    OptionalInt decimalDigits();

    OptionalInt numPrecRadix();

    Nullability nullability();

    OptionalInt charOctetLength();

    Optional<String> remarks();

    Optional<String> columnDefault();

    AutoIncrement autoIncrement();

    GeneratedColumn generatedColumn();

    enum Nullability {
        NO_NULLS(DatabaseMetaData.columnNoNulls), NULLABLE(DatabaseMetaData.columnNullable),
        UNKNOWN(DatabaseMetaData.columnNullableUnknown);

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

        /**
         * Look up the enum constant matching the JDBC string code. Throws if no match.
         */
        public static Nullability ofString(String value) {
            if ("YES".equalsIgnoreCase(value)) {
                return NULLABLE;
            } else if ("NO".equalsIgnoreCase(value)) {
                return NO_NULLS;
            }
            return UNKNOWN;
        }
    }

    enum AutoIncrement {
        YES, NO, UNKNOWN;

        /**
         * Look up the enum constant matching the JDBC string code. Throws if no match.
         */
        public static AutoIncrement ofString(String value) {
            if ("YES".equalsIgnoreCase(value)) {
                return YES;
            } else if ("NO".equalsIgnoreCase(value)) {
                return NO;
            }
            return UNKNOWN;
        }
    }

    enum GeneratedColumn {
        YES, NO, UNKNOWN;

        /**
         * Look up the enum constant matching the JDBC string code. Throws if no match.
         */
        public static GeneratedColumn ofString(String value) {
            if ("YES".equalsIgnoreCase(value)) {
                return YES;
            } else if ("NO".equalsIgnoreCase(value)) {
                return NO;
            }
            return UNKNOWN;
        }
    }
}
