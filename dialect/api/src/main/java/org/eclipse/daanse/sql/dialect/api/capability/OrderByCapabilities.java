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
 * @param allowsOrderByAlias               whether ORDER BY can reference SELECT
 *                                         aliases
 * @param requiresOrderByAlias             whether ORDER BY must use SELECT
 *                                         aliases
 * @param requiresUnionOrderByOrdinal      whether UNION ORDER BY requires
 *                                         ordinal position (e.g., ORDER BY 1)
 * @param requiresUnionOrderByExprInSelect whether UNION ORDER BY expression
 *                                         must be in SELECT clause
 * @param requiresGroupByAlias             whether GROUP BY must use SELECT
 *                                         aliases
 * @param requiresHavingAlias              whether HAVING must use SELECT
 *                                         aliases
 * @param supportsNullsLast                whether NULLS FIRST/LAST syntax is
 *                                         supported
 */
public record OrderByCapabilities(boolean allowsOrderByAlias, boolean requiresOrderByAlias,
        boolean requiresUnionOrderByOrdinal, boolean requiresUnionOrderByExprInSelect, boolean requiresGroupByAlias,
        boolean requiresHavingAlias, boolean supportsNullsLast) {

    /** @return OrderByCapabilities with standard features */
    public static OrderByCapabilities standard() {
        return new OrderByCapabilities(true, // allowsOrderByAlias
                false, // requiresOrderByAlias
                false, // requiresUnionOrderByOrdinal
                false, // requiresUnionOrderByExprInSelect
                false, // requiresGroupByAlias
                false, // requiresHavingAlias
                true // supportsNullsLast
        );
    }

    /** @return OrderByCapabilities with minimal features */
    public static OrderByCapabilities minimal() {
        return new OrderByCapabilities(false, // allowsOrderByAlias
                false, // requiresOrderByAlias
                false, // requiresUnionOrderByOrdinal
                false, // requiresUnionOrderByExprInSelect
                false, // requiresGroupByAlias
                false, // requiresHavingAlias
                false // supportsNullsLast
        );
    }
}
