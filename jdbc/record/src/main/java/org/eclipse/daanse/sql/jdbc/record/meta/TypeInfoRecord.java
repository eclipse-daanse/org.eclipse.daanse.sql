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
package org.eclipse.daanse.sql.jdbc.record.meta;

import java.sql.JDBCType;
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.meta.TypeInfo;

public record TypeInfoRecord(String typeName, JDBCType dataType, int precision, Optional<String> literalPrefix,
        Optional<String> literalSuffix, Optional<String> createParams, Nullable nullable, boolean caseSensitive,
        Searchable searchable, boolean unsignedAttribute, boolean fixedPrecScale, boolean autoIncrement,
        Optional<String> localTypeName, short minimumScale, short maximumScale, int numPrecRadix) implements TypeInfo {

}
