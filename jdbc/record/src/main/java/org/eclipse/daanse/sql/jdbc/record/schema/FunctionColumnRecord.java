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
package org.eclipse.daanse.sql.jdbc.record.schema;

import java.sql.JDBCType;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.jdbc.api.schema.FunctionColumn;

public record FunctionColumnRecord(
        String name,
        ColumnType columnType,
        JDBCType dataType,
        String typeName,
        OptionalInt precision,
        OptionalInt scale,
        OptionalInt radix,
        Nullability nullable,
        Optional<String> remarks,
        OptionalInt charOctetLength,
        int ordinalPosition) implements FunctionColumn {

}
