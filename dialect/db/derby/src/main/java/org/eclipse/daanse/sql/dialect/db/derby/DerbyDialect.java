/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * History:
 *  This files came from the mondrian project. Some of the Flies
 *  (mostly the Tests) did not have License Header.
 *  But the Project is EPL Header. 2002-2022 Hitachi Vantara.
 *
 * Contributors:
 *   Hitachi Vantara.
 *   SmartCity Jena - initial  Java 8, Junit5
 */
package org.eclipse.daanse.sql.dialect.db.derby;

import java.sql.Date;
import java.util.List;

import org.eclipse.daanse.sql.dialect.db.common.AbstractJdbcDialect;
import org.eclipse.daanse.sql.dialect.db.common.DialectUtil;

/**
 * @author jhyde
 * @since Nov 23, 2008
 */
public class DerbyDialect extends AbstractJdbcDialect {

    private static final String SUPPORTED_PRODUCT_NAME = "DERBY";

    /** JDBC-free constructor for SQL generation. */
    public DerbyDialect() {
        super(org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults());
    }

    /** Construct from a captured snapshot — the canonical entry point. */
    public DerbyDialect(org.eclipse.daanse.sql.dialect.api.DialectInitData init) {
        super(init);
    }

    /** Derby has no {@code IF NOT EXISTS} on any DDL. */
    @Override
    public boolean supportsCreateTableIfNotExists() {
        return false;
    }

    @Override
    public boolean supportsCreateIndexIfNotExists() {
        return false;
    }

    @Override
    public boolean supportsDropIndexIfExists() {
        return false;
    }

    @Override
    public boolean supportsCreateOrReplaceView() {
        return false;
    }

    @Override
    public boolean supportsDropViewIfExists() {
        return false;
    }

    @Override
    public boolean supportsDropConstraintIfExists() {
        return false;
    }

    @Override
    public boolean supportsDropSchemaIfExists() {
        return false;
    }

    /** Derby's {@code DROP TABLE} doesn't accept {@code CASCADE}. */
    @Override
    public boolean supportsDropTableCascade() {
        return false;
    }

    @Override
    public boolean supportsDropTableIfExists() {
        return false;
    }

    /** Derby has no {@code IF NOT EXISTS} on {@code CREATE SCHEMA} — strip it. */
    @Override
    public String createSchema(String schemaName, boolean ifNotExists) {
        return "CREATE SCHEMA " + quoteIdentifier(schemaName);
    }

    /**
     * Derby requires the SQL-92 {@code RESTRICT} keyword on {@code DROP SCHEMA}.
     */
    @Override
    public boolean requiresDropSchemaRestrict() {
        return true;
    }

    @Override
    protected void quoteDateLiteral(StringBuilder buf, Date date) {
        // Derby accepts DATE('2008-01-23') but not SQL:2003 format.
        buf.append("DATE(");
        DialectUtil.singleQuoteString(date.toString(), buf);
        buf.append(")");
    }

    @Override
    public boolean requiresAliasForFromQuery() {
        return true;
    }

    @Override
    public boolean allowsMultipleCountDistinct() {
        // Derby allows at most one distinct-count per query.
        return false;
    }

    @Override
    public StringBuilder generateInline(List<String> columnNames, List<String> columnTypes, List<String[]> valueList) {
        return generateInlineForAnsi("t", columnNames, columnTypes, valueList, true);
    }

    @Override
    public boolean supportsGroupByExpressions() {
        return false;
    }

    @Override
    public boolean allowsFieldAlias() {
        // Derby fully supports (quoted) field aliases — `select x as "Store Sqft"` executes
        // fine (the false setting was inherited for DB2/AS400-style dialects and made the
        // engine drop select-list aliases, so drill-through result labels degraded to the
        // physical column names and per-dialect SQL asserts diverged).
        return true;
    }

    @Override
    public String name() {
        return SUPPORTED_PRODUCT_NAME.toLowerCase();
    }
}
