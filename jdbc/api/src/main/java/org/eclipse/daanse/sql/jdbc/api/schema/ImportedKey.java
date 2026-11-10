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
import org.eclipse.daanse.sql.model.schema.ColumnReference;

import java.sql.DatabaseMetaData;
import java.util.Optional;
import java.util.stream.Stream;

public non-sealed interface ImportedKey extends Named, Constraint {

    /** @return the primary key column reference */
    ColumnReference primaryKeyColumn();

    /** @return the foreign key column reference */
    ColumnReference foreignKeyColumn();

    /** @return sequence number within the foreign key */
    int keySequence();

    ReferentialAction updateRule();

    ReferentialAction deleteRule();

    Optional<String> primaryKeyName();

    Deferrability deferrability();

    enum ReferentialAction {
        NO_ACTION(DatabaseMetaData.importedKeyNoAction), CASCADE(DatabaseMetaData.importedKeyCascade),
        SET_NULL(DatabaseMetaData.importedKeySetNull), SET_DEFAULT(DatabaseMetaData.importedKeySetDefault),
        RESTRICT(DatabaseMetaData.importedKeyRestrict);

        private final int value;

        ReferentialAction(int value) {
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
        public static ReferentialAction of(int value) {
            return Stream.of(ReferentialAction.values()).filter(r -> r.value == value).findFirst().orElse(NO_ACTION);
        }
    }

    enum Deferrability {
        INITIALLY_DEFERRED(DatabaseMetaData.importedKeyInitiallyDeferred),
        INITIALLY_IMMEDIATE(DatabaseMetaData.importedKeyInitiallyImmediate),
        NOT_DEFERRABLE(DatabaseMetaData.importedKeyNotDeferrable);

        private final int value;

        Deferrability(int value) {
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
        public static Deferrability of(int value) {
            return Stream.of(Deferrability.values()).filter(d -> d.value == value).findFirst().orElse(NOT_DEFERRABLE);
        }
    }
}
