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
package org.eclipse.daanse.sql.jdbc.api.meta;

import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.util.Optional;
import java.util.stream.Stream;

public interface TypeInfo {

    String typeName();

    JDBCType dataType();

    int precision();

    Optional<String> literalPrefix();

    Optional<String> literalSuffix();

    Optional<String> createParams();

    Nullable nullable();

    boolean caseSensitive();

    Searchable searchable();

    boolean unsignedAttribute();

    boolean fixedPrecScale();

    boolean autoIncrement();

    Optional<String> localTypeName();

    short minimumScale();

    short maximumScale();

    int numPrecRadix();

    enum Nullable {
        NO_NULLS(DatabaseMetaData.typeNoNulls), NULLABLE(DatabaseMetaData.typeNullable),
        UNKNOWN(DatabaseMetaData.typeNullableUnknown);

        int value;

        Nullable(int value) {
            this.value = value;
        }

        /** Look up the enum constant matching the JDBC int code. Throws if no match. */
        public static Nullable of(int value) {
            return Stream.of(Nullable.values()).filter(n -> n.value == value).findAny().orElse(null);
        }
    }

    enum Searchable {
        PRED_NONE(DatabaseMetaData.typePredNone), PRED_CHAR(DatabaseMetaData.typePredChar),
        PRED_BASIC(DatabaseMetaData.typePredBasic), SEARCHABLE(DatabaseMetaData.typeSearchable);

        int value;

        Searchable(int value) {
            this.value = value;
        }

        /** Look up the enum constant matching the JDBC int code. Throws if no match. */
        public static Searchable of(int value) {
            return Stream.of(Searchable.values()).filter(n -> n.value == value).findAny().orElse(null);
        }
    }
}
