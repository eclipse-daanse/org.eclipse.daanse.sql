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
 * @param countDistinct               whether the dialect supports
 *                                    COUNT(DISTINCT column)
 * @param multipleCountDistinct       whether the dialect supports multiple
 *                                    COUNT(DISTINCT) in the same query
 * @param compoundCountDistinct       whether the dialect supports
 *                                    COUNT(DISTINCT col1, col2)
 * @param countDistinctWithOtherAggs  whether COUNT(DISTINCT) can be used with
 *                                    other aggregates in the same query
 * @param innerDistinct               whether the dialect allows DISTINCT in
 *                                    inner/subqueries
 * @param multipleDistinctSqlMeasures whether the dialect supports multiple
 *                                    distinct SQL measures
 * @param groupingSets                whether the dialect supports GROUPING SETS
 * @param groupByExpressions          whether the dialect supports expressions
 *                                    in GROUP BY
 * @param selectNotInGroupBy          whether the dialect allows SELECT columns
 *                                    not in GROUP BY (MySQL-style)
 */
public record AggregateCapabilities(boolean countDistinct, boolean multipleCountDistinct, boolean compoundCountDistinct,
        boolean countDistinctWithOtherAggs, boolean innerDistinct, boolean multipleDistinctSqlMeasures,
        boolean groupingSets, boolean groupByExpressions, boolean selectNotInGroupBy) {

    /** @return AggregateCapabilities with all features enabled */
    public static AggregateCapabilities full() {
        return new AggregateCapabilities(true, // countDistinct
                true, // multipleCountDistinct
                true, // compoundCountDistinct
                true, // countDistinctWithOtherAggs
                true, // innerDistinct
                true, // multipleDistinctSqlMeasures
                true, // groupingSets
                true, // groupByExpressions
                false // selectNotInGroupBy (usually false for standard SQL)
        );
    }

    /** @return AggregateCapabilities with minimal features */
    public static AggregateCapabilities minimal() {
        return new AggregateCapabilities(true, // countDistinct
                false, // multipleCountDistinct
                false, // compoundCountDistinct
                false, // countDistinctWithOtherAggs
                false, // innerDistinct
                false, // multipleDistinctSqlMeasures
                false, // groupingSets
                false, // groupByExpressions
                false // selectNotInGroupBy
        );
    }

    /** @return AggregateCapabilities with all features disabled */
    public static AggregateCapabilities none() {
        return new AggregateCapabilities(false, false, false, false, false, false, false, false, false);
    }
}
