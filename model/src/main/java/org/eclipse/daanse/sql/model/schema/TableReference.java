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
package org.eclipse.daanse.sql.model.schema;

import java.util.Optional;

public record TableReference(Optional<SchemaReference> schema, String name, String type) implements Named {

    public static final String TYPE_TABLE = "TABLE";
    public static final String TYPE_VIEW = "VIEW";
    public static final String TYPE_SYSTEM_TABLE = "SYSTEM TABLE";
    public static final String TYPE_GLOBAL_TEMPORARY = "GLOBAL TEMPORARY";
    public static final String TYPE_LOCAL_TEMPORARY = "LOCAL TEMPORARY";
    public static final String TYPE_ALIAS = "ALIAS";
    public static final String TYPE_SYNONYM = "SYNONYM";

    /** Convenience: an unqualified table (no schema), {@link #TYPE_TABLE}. */
    public TableReference(String name) {
        this(Optional.empty(), name, TYPE_TABLE);
    }

    /** Convenience: an unqualified table (no schema) of the given JDBC type. */
    public TableReference(String name, String type) {
        this(Optional.empty(), name, type);
    }

    /** Convenience: a schema-qualified {@link #TYPE_TABLE}. */
    public TableReference(Optional<SchemaReference> schema, String name) {
        this(schema, name, TYPE_TABLE);
    }
}
