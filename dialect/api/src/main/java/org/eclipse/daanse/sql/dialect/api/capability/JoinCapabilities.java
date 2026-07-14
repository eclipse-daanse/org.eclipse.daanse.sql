/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.dialect.api.capability;

/**
 * @param joinOn                    whether the dialect supports ANSI JOIN...ON
 *                                  syntax
 * @param asKeyword                 whether the dialect allows the AS keyword in
 *                                  FROM clause aliases
 * @param fromQuery                 whether the dialect allows subqueries in the
 *                                  FROM clause
 * @param requiresAliasForFromQuery whether subqueries in FROM require an alias
 */
public record JoinCapabilities(boolean joinOn, boolean asKeyword, boolean fromQuery,
        boolean requiresAliasForFromQuery) {

    /** @return JoinCapabilities with standard ANSI SQL features */
    public static JoinCapabilities standard() {
        return new JoinCapabilities(true, // joinOn
                true, // asKeyword
                true, // fromQuery
                true // requiresAliasForFromQuery
        );
    }

    /** @return JoinCapabilities with optional subquery alias */
    public static JoinCapabilities withOptionalAlias() {
        return new JoinCapabilities(true, // joinOn
                true, // asKeyword
                true, // fromQuery
                false // requiresAliasForFromQuery
        );
    }
}
