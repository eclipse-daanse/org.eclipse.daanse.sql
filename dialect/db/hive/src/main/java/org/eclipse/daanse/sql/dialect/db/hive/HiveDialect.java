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
package org.eclipse.daanse.sql.dialect.db.hive;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding;
import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;
import org.eclipse.daanse.sql.dialect.db.common.DialectUtil;

/**
 * Dialect for the Apache Hive database (HiveQL over HiveServer2).
 */
public class HiveDialect extends AbstractJdbcDialect {

    private static final String SUPPORTED_PRODUCT_NAME = "HIVE";

    /** JDBC-free constructor for SQL generation. Uses HiveQL backtick quoting. */
    public HiveDialect() {
        super(DialectInitData.ansiDefaults().withQuoteIdentifierString("`"));
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public HiveDialect(DialectInitData init) {
        super(init);
    }

    @Override
    public boolean supportsResultSetConcurrency(int type, int concurrency) {
        return false;
    }

    @Override
    public boolean allowsCompoundCountDistinct() {
        return true;
    }

    @Override
    public boolean requiresAliasForFromQuery() {
        return true;
    }

    @Override
    public boolean requiresOrderByAlias() {
        return true;
    }

    // Explicit even though it matches the derived default here: a subclass that
    // flips requiresOrderByAlias() to false must keep ORDER BY aliases allowed.
    @Override
    public boolean allowsOrderByAlias() {
        return true;
    }

    @Override
    public boolean requiresUnionOrderByExprInSelect() {
        return false;
    }

    @Override
    public boolean requiresUnionOrderByOrdinal() {
        return false;
    }

    @Override
    public boolean allowsJoinOn() {
        return false;
    }

    @Override
    public StringBuilder generateInline(List<String> columnNames, List<String> columnTypes, List<String[]> valueList) {
        // The legacy (Mondrian-era) form appended " from dual", which fails on
        // any modern Hive ("Table not found 'dual'" — verified against Hive 4);
        // Hive supports FROM-less SELECTs since 0.13.
        return new StringBuilder("select * from (")
                .append(generateInlineGeneric(columnNames, columnTypes, valueList, null, false))
                .append(") x limit ").append(valueList.size());
    }

    @Override
    protected void quoteDateLiteral(StringBuilder buf, Date date) {
        // Hive doesn't support Date type; treat date as a string '2008-01-23'
        DialectUtil.singleQuoteString(date.toString(), buf);
    }

    @Override
    protected void quoteTimestampLiteral(StringBuilder buf, String value, Timestamp timestamp) {
        buf.append("cast( ");
        DialectUtil.singleQuoteString(value, buf);
        buf.append(" as timestamp )");
    }

    @Override
    public StringBuilder generateOrderByNulls(CharSequence expr, boolean ascending, boolean collateNullsLast) {
        // In Hive, Null values are worth negative infinity.
        return DialectUtil.generateOrderByNullsWithIsnull(expr, ascending, collateNullsLast);
    }

    @Override
    public IdentifierCaseFolding caseFolding() {
        return IdentifierCaseFolding.PRESERVE;
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }

}
