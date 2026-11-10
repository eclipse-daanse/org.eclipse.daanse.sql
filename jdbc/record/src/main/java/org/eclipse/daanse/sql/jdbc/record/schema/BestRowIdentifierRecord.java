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
import java.util.OptionalInt;

import org.eclipse.daanse.sql.jdbc.api.schema.BestRowIdentifier;
import org.eclipse.daanse.sql.model.schema.ColumnReference;

public record BestRowIdentifierRecord(
        ColumnReference column,
        Scope scope,
        JDBCType dataType,
        String typeName,
        OptionalInt columnSize,
        OptionalInt decimalDigits,
        PseudoColumnKind pseudoColumn) implements BestRowIdentifier {
}
