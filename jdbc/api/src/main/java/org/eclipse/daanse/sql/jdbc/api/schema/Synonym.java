/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
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

/**
 * An alias for another schema object (Oracle synonym, SQL Server synonym, DB2 alias).
 * <p>
 * The target is reported as stored in the catalog; it is not resolved further. A
 * synonym pointing to another synonym has {@link #targetObjectType()} {@code SYNONYM},
 * and consumers follow the chain themselves.
 */
public non-sealed interface Synonym extends SchemaObject, Named {

    SynonymReference reference();

    @Override
    default String name() {
        return reference().name();
    }

    /** Convenience: the schema of this synonym ({@code PUBLIC} for Oracle public synonyms). */
    default Optional<SchemaReference> schema() {
        return reference().schema();
    }

    /** @return the schema of the target object, empty when the catalog does not record one */
    Optional<SchemaReference> targetSchema();

    /** @return the name of the target object */
    String targetName();

    /** @return the database link (Oracle) or linked server (SQL Server) the target lives behind */
    Optional<String> dbLink();

    /** @return true for a synonym visible to every user (Oracle {@code PUBLIC} synonym) */
    boolean isPublic();

    /**
     * @return the type of the target object as named by the database ({@code TABLE}, {@code VIEW},
     *         {@code FUNCTION}, {@code SYNONYM}, ...); empty when the target is remote, missing
     *         or not visible to the reading user
     */
    Optional<String> targetObjectType();
}
