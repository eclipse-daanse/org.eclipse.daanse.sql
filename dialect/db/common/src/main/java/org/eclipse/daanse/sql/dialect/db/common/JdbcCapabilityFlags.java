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

/**
 * Default values for the per-engine boolean capability flags. Methods that
 * depend on another flag delegate through the supplied {@link Dialect} so
 * subclass overrides are respected.
 */
final class JdbcCapabilityFlags {

    private final Dialect dialect;

    JdbcCapabilityFlags(Dialect dialect) {
        this.dialect = dialect;
    }

    boolean requiresAliasForFromQuery() {
        return false;
    }

    boolean allowsFromAlias() {
        return true;
    }

    boolean allowsFromQuery() {
        return true;
    }

    boolean allowsCompoundCountDistinct() {
        return false;
    }

    boolean allowsCountDistinct() {
        return true;
    }

    boolean allowsMultipleCountDistinct() {
        return dialect.allowsCountDistinct();
    }

    boolean allowsMultipleDistinctSqlMeasures() {
        return dialect.allowsMultipleCountDistinct();
    }

    boolean allowsCountDistinctWithOtherAggs() {
        return dialect.allowsCountDistinct();
    }

    boolean supportsGroupByExpressions() {
        return true;
    }

    boolean allowsJoinOn() {
        return true;
    }

    boolean supportsGroupingSets() {
        return false;
    }

    boolean supportsUnlimitedValueList() {
        return false;
    }

    boolean requiresGroupByAlias() {
        return false;
    }

    boolean requiresOrderByAlias() {
        return false;
    }

    boolean requiresHavingAlias() {
        return false;
    }

    boolean allowsOrderByAlias() {
        return dialect.requiresOrderByAlias();
    }

    boolean requiresUnionOrderByOrdinal() {
        return true;
    }

    boolean requiresUnionOrderByExprInSelect() {
        return true;
    }

    boolean supportsMultiValueInExpr() {
        return false;
    }

    boolean allowsRegularExpressionInWhereClause() {
        return false;
    }

    boolean requiresDrillthroughMaxRowsInLimit() {
        return false;
    }

    boolean allowsFieldAlias() {
        return true;
    }

    boolean allowsInnerDistinct() {
        return true;
    }

    boolean supportsParallelLoading() {
        return true;
    }

    boolean supportsBatchOperations() {
        return true;
    }

    boolean allowsDialectSharing() {
        return true;
    }
}
