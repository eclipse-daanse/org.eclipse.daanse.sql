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

import org.eclipse.daanse.sql.model.schema.ColumnReference;

import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.util.OptionalInt;
import java.util.stream.Stream;

public interface VersionColumn {

    /** The column. */
    ColumnReference column();

    /** SQL/JDBC type of the column. */
    JDBCType dataType();

    /** Database-specific type name. */
    String typeName();

    /** Precision / size of the column. */
    OptionalInt columnSize();

    /** Number of fractional digits, if applicable. */
    OptionalInt decimalDigits();

    /** Whether the column is a pseudo column. */
    PseudoColumnKind pseudoColumn();

    /** Pseudo-column categorization. */
    enum PseudoColumnKind {
        UNKNOWN(DatabaseMetaData.versionColumnUnknown), NOT_PSEUDO(DatabaseMetaData.versionColumnNotPseudo),
        PSEUDO(DatabaseMetaData.versionColumnPseudo);

        private final int value;

        PseudoColumnKind(int value) {
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
        public static PseudoColumnKind of(int value) {
            return Stream.of(values()).filter(k -> k.value == value).findFirst().orElse(UNKNOWN);
        }
    }
}
