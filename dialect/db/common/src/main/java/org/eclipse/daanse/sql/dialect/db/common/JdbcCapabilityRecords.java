/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.common;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.capability.AggregateCapabilities;
import org.eclipse.daanse.sql.dialect.api.capability.JoinCapabilities;
import org.eclipse.daanse.sql.dialect.api.capability.OrderByCapabilities;
import org.eclipse.daanse.sql.dialect.api.capability.WindowFunctionCapabilities;

/**
 * Lazy-caches the four {@code XxxCapabilities} record snapshots derived from
 * the dialect's flat boolean flags. Each record is built once on first access
 * by reading current flag values from the dialect.
 */
final class JdbcCapabilityRecords {

    private final Dialect dialect;
    private AggregateCapabilities aggregate;
    private JoinCapabilities join;
    private OrderByCapabilities orderBy;
    private WindowFunctionCapabilities windowFunction;

    JdbcCapabilityRecords(Dialect dialect) {
        this.dialect = dialect;
    }

    AggregateCapabilities aggregate() {
        if (aggregate == null) {
            aggregate = new AggregateCapabilities(dialect.allowsCountDistinct(), dialect.allowsMultipleCountDistinct(),
                    dialect.allowsCompoundCountDistinct(), dialect.allowsCountDistinctWithOtherAggs(),
                    dialect.allowsInnerDistinct(), dialect.allowsMultipleDistinctSqlMeasures(),
                    dialect.supportsGroupingSets(), dialect.supportsGroupByExpressions(),
                    dialect.allowsSelectNotInGroupBy());
        }
        return aggregate;
    }

    JoinCapabilities join() {
        if (join == null) {
            join = new JoinCapabilities(dialect.allowsJoinOn(), dialect.allowsFromAlias(), dialect.allowsFromQuery(),
                    dialect.requiresAliasForFromQuery());
        }
        return join;
    }

    OrderByCapabilities orderBy(boolean supportsNullsOrdering) {
        if (orderBy == null) {
            orderBy = new OrderByCapabilities(dialect.allowsOrderByAlias(), dialect.requiresOrderByAlias(),
                    dialect.requiresUnionOrderByOrdinal(), dialect.requiresUnionOrderByExprInSelect(),
                    dialect.requiresGroupByAlias(), dialect.requiresHavingAlias(), supportsNullsOrdering);
        }
        return orderBy;
    }

    WindowFunctionCapabilities windowFunction() {
        if (windowFunction == null) {
            var agg = dialect.aggregationGenerator();
            windowFunction = new WindowFunctionCapabilities(agg.supportsPercentileDisc(), agg.supportsPercentileCont(),
                    agg.supportsListAgg(), agg.supportsNthValue(), agg.supportsNthValueIgnoreNulls());
        }
        return windowFunction;
    }
}
