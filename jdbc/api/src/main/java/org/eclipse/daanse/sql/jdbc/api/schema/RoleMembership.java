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
 * A role membership edge — {@code GRANT role TO grantee} — from the dialect's
 * membership catalog; no JDBC equivalent exists.
 */
public interface RoleMembership {

    /** Principal (user or role) that received the role. */
    String grantee();

    /** The granted role. */
    String role();

    /** Principal that granted the membership (or empty if unknown). */
    Optional<String> grantor();

    /** WITH ADMIN OPTION: "YES", "NO", or empty if unknown. */
    Optional<String> adminOption();
}
