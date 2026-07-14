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

public interface DialectCapabilitiesProvider {

    default AggregateCapabilities getAggregateCapabilities() {
        return new AggregateCapabilities(allowsCountDistinct(), allowsMultipleCountDistinct(),
                allowsCompoundCountDistinct(), allowsCountDistinctWithOtherAggs(), allowsInnerDistinct(),
                allowsMultipleDistinctSqlMeasures(), supportsGroupingSets(), supportsGroupByExpressions(),
                allowsSelectNotInGroupBy());
    }

    default boolean allowsSelectNotInGroupBy() {
        return false;
    }

    default JoinCapabilities getJoinCapabilities() {
        return new JoinCapabilities(allowsJoinOn(), allowsFromAlias(), allowsFromQuery(), requiresAliasForFromQuery());
    }

    default OrderByCapabilities getOrderByCapabilities() {
        return new OrderByCapabilities(allowsOrderByAlias(), requiresOrderByAlias(), requiresUnionOrderByOrdinal(),
                requiresUnionOrderByExprInSelect(), requiresGroupByAlias(), requiresHavingAlias(), supportsNullsLast());
    }

    WindowFunctionCapabilities getWindowFunctionCapabilities();

    default DdlCapabilities getDdlCapabilities() {
        return new DdlCapabilities(supportsDdl(), supportsDropTableCascade(), supportsSequences(),
                dropIndexRequiresTable(), supportsCreateTableIfNotExists(), supportsCreateIndexIfNotExists(),
                supportsDropIndexIfExists(), supportsCreateOrReplaceView(), supportsCreateOrReplaceTrigger(),
                supportsDropViewIfExists(), supportsDropConstraintIfExists(), supportsDropTableIfExists(),
                supportsDropSchemaIfExists(), requiresDropSchemaRestrict(), getMaxColumnNameLength());
    }

    int getMaxColumnNameLength();

    /** @return true if DDL is allowed */
    boolean supportsDdl();

    /** @return true if {@code DROP TABLE … CASCADE} is recognised */
    default boolean supportsDropTableCascade() {
        return true;
    }

    /** @return true if SQL sequences are supported */
    default boolean supportsSequences() {
        return true;
    }

    /**
     * @return true if {@code DROP INDEX} must be qualified with the owning table;
     *         false (default) otherwise
     */
    default boolean dropIndexRequiresTable() {
        return false;
    }

    /**
     * @return true if the dialect honours the {@code IF NOT EXISTS} clause on
     *         {@code CREATE TABLE}
     */
    default boolean supportsCreateTableIfNotExists() {
        return true;
    }

    /**
     * @return true if the dialect supports plain b-tree {@code CREATE INDEX} DDL at all.
     *         ClickHouse for example rejects {@code CREATE INDEX} without a data-skipping
     *         {@code TYPE} clause (INCORRECT_QUERY 80) — tools generating index DDL must
     *         skip or specialize there.
     */
    default boolean supportsIndexDdl() {
        return true;
    }

    /**
     * @return true if the dialect honours {@code IF NOT EXISTS} on
     *         {@code CREATE INDEX}
     */
    default boolean supportsCreateIndexIfNotExists() {
        return true;
    }

    /**
     * @return true if the dialect honours {@code IF EXISTS} on {@code DROP INDEX}
     */
    default boolean supportsDropIndexIfExists() {
        return true;
    }

    /**
     * @return true if the dialect honours the {@code OR REPLACE} keyword on
     *         {@code CREATE VIEW}
     */
    default boolean supportsCreateOrReplaceView() {
        return true;
    }

    /**
     * @return true if the dialect honours {@code OR REPLACE} on
     *         {@code CREATE TRIGGER}
     */
    default boolean supportsCreateOrReplaceTrigger() {
        return false;
    }

    /**
     * @return true if the dialect honours {@code IF EXISTS} on {@code DROP VIEW}
     */
    default boolean supportsDropViewIfExists() {
        return true;
    }

    /**
     * @return true if the dialect honours {@code IF EXISTS} on
     *         {@code DROP CONSTRAINT}
     */
    default boolean supportsDropConstraintIfExists() {
        return true;
    }

    /**
     * @return true if the dialect honours {@code IF EXISTS} on {@code DROP TABLE}
     */
    default boolean supportsDropTableIfExists() {
        return true;
    }

    /**
     * @return true if the dialect honours {@code IF EXISTS} on {@code DROP SCHEMA}
     */
    default boolean supportsDropSchemaIfExists() {
        return true;
    }

    /**
     * @return true if the dialect requires {@code RESTRICT} after
     *         {@code DROP SCHEMA name}
     */
    default boolean requiresDropSchemaRestrict() {
        return false;
    }

    /** @return true if dialect sharing is allowed */
    boolean allowsDialectSharing();

    /** @return true if regex is supported */
    boolean allowsRegularExpressionInWhereClause();

    /** @return true if multi-value IN is supported */
    boolean supportsMultiValueInExpr();

    /** @return true if unlimited value lists are supported */
    boolean supportsUnlimitedValueList();

    /** @return true if required */
    boolean requiresDrillthroughMaxRowsInLimit();

    /** @return true if parallel loading is supported */
    boolean supportsParallelLoading();

    /** @return true if batch operations are supported */
    boolean supportsBatchOperations();

    /** @return true if AS is allowed in field aliases */
    boolean allowsFieldAlias();

    // -------------------- canonical capability flags --------------------
    //
    // These flat boolean methods are the source of truth — every dialect
    // implements (or inherits) one definitive value per flag. The
    // record-based getters above (getAggregateCapabilities, getJoinCapabilities,
    // …) are convenience aggregations that bundle the flags by concern;
    // callers that only need one bundle ask for that record, callers that
    // need a single flag call the flat method directly.

    boolean allowsFromAlias();

    boolean allowsFromQuery();

    boolean requiresAliasForFromQuery();

    boolean allowsJoinOn();

    boolean allowsCountDistinct();

    boolean allowsMultipleCountDistinct();

    boolean allowsCompoundCountDistinct();

    boolean allowsCountDistinctWithOtherAggs();

    boolean allowsInnerDistinct();

    boolean allowsMultipleDistinctSqlMeasures();

    boolean supportsGroupingSets();

    boolean supportsGroupByExpressions();

    boolean allowsOrderByAlias();

    boolean requiresOrderByAlias();

    boolean requiresGroupByAlias();

    boolean requiresHavingAlias();

    boolean requiresUnionOrderByOrdinal();

    boolean requiresUnionOrderByExprInSelect();

    // Aggregation/window-function feature flags
    // (supportsPercentileDisc/Cont, supportsListAgg, supportsNthValue,
    // supportsNthValueIgnoreNulls) live on AggregationGenerator. They're
    // exposed in WindowFunctionCapabilities below via the dialect's
    // aggregationGenerator() — see Dialect.getWindowFunctionCapabilities().

    default boolean supportsNullsLast() {
        return true;
    }
}
