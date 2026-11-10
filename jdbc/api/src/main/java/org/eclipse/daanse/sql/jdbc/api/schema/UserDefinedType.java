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
import org.eclipse.daanse.sql.model.schema.SchemaReference;

import java.sql.JDBCType;
import java.util.Optional;

public non-sealed interface UserDefinedType extends SchemaObject, Named {

    UserDefinedTypeReference reference();

    @Override
    default String name() {
        return reference().name();
    }

    /** Convenience: the schema of this user-defined type. */
    default Optional<SchemaReference> schema() {
        return reference().schema();
    }

    String className();

    JDBCType baseType();

    /** @return the remarks, or empty */
    Optional<String> remarks();
}
