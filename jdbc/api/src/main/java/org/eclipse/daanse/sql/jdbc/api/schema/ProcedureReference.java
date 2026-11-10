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

import java.util.Optional;

public record ProcedureReference(Optional<SchemaReference> schema, String name, String specificName) implements Named {

    public ProcedureReference(Optional<SchemaReference> schema, String name) {
        this(schema, name, name);
    }
}
