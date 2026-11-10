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

/**
 * A database principal — user or role — from the dialect's principal catalog;
 * no JDBC equivalent exists. Built-ins are not filtered.
 */
public interface DatabasePrincipal {

    /** Canonical {@link #kind()} for login-capable principals. */
    String KIND_USER = "USER";

    /** Canonical {@link #kind()} for grantable/joinable principals (roles, groups). */
    String KIND_ROLE = "ROLE";

    /**
     * Canonical {@link #kind()} when the dialect does not separate users from
     * roles structurally (MySQL: "little to distinguish them") — no basis for
     * provisioning decisions.
     */
    String KIND_UNKNOWN = "UNKNOWN";

    /** The principal's name. */
    String name();

    /** Canonical kind: {@link #KIND_USER}, {@link #KIND_ROLE} or {@link #KIND_UNKNOWN}. */
    String kind();
}
