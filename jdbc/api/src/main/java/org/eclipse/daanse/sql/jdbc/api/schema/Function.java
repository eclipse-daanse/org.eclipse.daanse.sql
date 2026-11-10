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

import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public non-sealed interface Function extends SchemaObject {

    FunctionReference reference();

    FunctionType functionType();

    Optional<String> remarks();

    List<FunctionColumn> columns();

    /** @return the function body, or empty if not available */
    default Optional<String> body() {
        return Optional.empty();
    }

    /** @return the full CREATE FUNCTION definition, or empty if not available */
    default Optional<String> fullDefinition() {
        return Optional.empty();
    }

    /** @return the last-modified time, or empty if not available */
    default Optional<Instant> lastModified() {
        return Optional.empty();
    }

    enum FunctionType {
        UNKNOWN(DatabaseMetaData.functionResultUnknown), NO_TABLE(DatabaseMetaData.functionNoTable),
        RETURNS_TABLE(DatabaseMetaData.functionReturnsTable);

        private final int value;

        FunctionType(int value) {
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
        public static FunctionType of(int value) {
            return Stream.of(FunctionType.values()).filter(t -> t.value == value).findFirst().orElse(UNKNOWN);
        }
    }
}
