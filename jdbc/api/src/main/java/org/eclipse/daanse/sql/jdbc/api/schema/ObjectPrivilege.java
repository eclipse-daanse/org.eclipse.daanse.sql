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

import java.util.Optional;

/**
 * A privilege granted on a non-table securable object — schema, database,
 * function, procedure, package, sequence, directory, ... Table and column
 * privileges stay in {@link TablePrivilege}/{@link ColumnPrivilege}.
 */
public interface ObjectPrivilege {

    /** Raw object kind as the dialect reports it: SCHEMA, DATABASE, FUNCTION, SEQUENCE, ... */
    String objectKind();

    /** @return the catalog/database qualifier, or empty */
    Optional<String> catalogName();

    /** @return the schema qualifier, or empty (schema- and database-level rows) */
    Optional<String> schemaName();

    /** The object's name. */
    String objectName();

    /**
     * The name that tells the object from others of its name, as
     * {@link FunctionReference#specificName()} and {@link ProcedureReference#specificName()} give it:
     * where a schema holds several routines of one name (overloads), a privilege on a routine is on
     * one of them, and this is that one's specific name. Where a name is unique, it is the
     * {@link #objectName()}.
     */
    default String specificName() {
        return objectName();
    }

    /** Principal that granted the privilege (or empty if unknown). */
    Optional<String> grantor();

    /** Principal that received the privilege. */
    String grantee();

    /** Privilege kind: USAGE, CREATE, EXECUTE, CONNECT, ... */
    String privilege();

    /** "YES", "NO", or empty if unknown. */
    Optional<String> isGrantable();
}
