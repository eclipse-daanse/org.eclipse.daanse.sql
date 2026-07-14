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

public interface BestRowIdentifier {

    /** The column. */
    ColumnReference column();

    /** Actual scope of the identifier's validity. */
    Scope scope();

    /** SQL/JDBC type of the column. */
    JDBCType dataType();

    /** Database-specific type name. */
    String typeName();

    /** Precision / size of the column. */
    OptionalInt columnSize();

    /** The number of fractional digits, if applicable. */
    OptionalInt decimalDigits();

    /** Whether the column is a pseudo column (e.g. Oracle ROWID). */
    PseudoColumnKind pseudoColumn();

    /** Scope of the identifier (how long it remains valid). */
    enum Scope {
        TEMPORARY(DatabaseMetaData.bestRowTemporary), TRANSACTION(DatabaseMetaData.bestRowTransaction),
        SESSION(DatabaseMetaData.bestRowSession);

        private final int value;

        Scope(int value) {
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
        public static Scope of(int value) {
            return Stream.of(values()).filter(s -> s.value == value).findFirst().orElse(SESSION);
        }
    }

    /** Pseudo-column categorization. */
    enum PseudoColumnKind {
        UNKNOWN(DatabaseMetaData.bestRowUnknown), NOT_PSEUDO(DatabaseMetaData.bestRowNotPseudo),
        PSEUDO(DatabaseMetaData.bestRowPseudo);

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
