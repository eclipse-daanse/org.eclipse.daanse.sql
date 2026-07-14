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

import java.util.Optional;

public interface ColumnPrivilege {

    /** The column. */
    ColumnReference column();

    /** Principal that granted the privilege (or empty if unknown). */
    Optional<String> grantor();

    /** Principal that received the privilege. */
    String grantee();

    /** Privilege kind: SELECT, INSERT, UPDATE, REFERENCES, ... */
    String privilege();

    /** "YES", "NO", or empty if unknown. */
    Optional<String> isGrantable();
}
