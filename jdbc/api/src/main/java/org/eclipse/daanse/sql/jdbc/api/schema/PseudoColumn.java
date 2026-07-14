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

import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;

public interface PseudoColumn {

    /** The pseudo column. */
    ColumnReference column();

    /** SQL/JDBC type of the column. */
    JDBCType dataType();

    /** Precision / size of the column. */
    OptionalInt columnSize();

    /** Number of fractional digits, if applicable. */
    OptionalInt decimalDigits();

    /** Radix used to express precision. */
    OptionalInt numPrecRadix();

    /** Comment on the column. */
    Optional<String> remarks();

    /** Max bytes (for character/binary). */
    OptionalInt charOctetLength();

    /** Whether the column accepts NULL values: "YES", "NO" or empty for unknown. */
    Optional<String> isNullable();

    /** Classification of the pseudo column usage. */
    String columnUsage();
}
