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
import org.eclipse.daanse.sql.model.schema.TableReference;

/**
 * @author jhyde
 * @since Nov 23, 2008
 */
public class DerbyDialect extends AbstractJdbcDialect {

    private static final String SUPPORTED_PRODUCT_NAME = "DERBY";

    /**
     * {@code SMALLINT}.
     *
     * <p>
     * Derby gained a native BOOLEAN in 10.7, but boolean levels are read back
     * with {@code ResultSet.getInt}, which it refuses on that type.
     */
    @Override
    public String booleanTypeName() {
        return "SMALLINT";
    }

    @Override
    public void quote(StringBuilder buf, Object value, org.eclipse.daanse.sql.model.type.Datatype datatype) {
        // Derby refuses an untyped null in a select list ("Syntax error: Encountered null"), as a union arm without a
        // column gives one (writeback view, database run 30.10.2026): a null of a known type is spelled typed, in a
        // type the union takes beside any column of its family. A text null stays untyped: VARCHAR is also the type a
        // null takes where nothing better is known (a CASE branch beside a quotient), and a typed one there clashes
        // ("Types 'VARCHAR' and 'DOUBLE' are not type compatible"), where Derby takes the untyped null
        if (value == null && datatype != null && datatype != org.eclipse.daanse.sql.model.type.Datatype.VARCHAR) {
            buf.append("CAST(NULL AS ").append(switch (datatype) {
            case DOUBLE, FLOAT, REAL -> "DOUBLE";
            case BIGINT -> "BIGINT";
            case BOOLEAN -> booleanTypeName();
            case DATE -> "DATE";
            case TIME -> "TIME";
            case TIMESTAMP -> "TIMESTAMP";
            default -> "INTEGER";
            }).append(")");
            return;
        }
        super.quote(buf, value, datatype);
    }

    /**
     * {@code 1} and {@code 0}: this dialect stores a truth value as SMALLINT ({@link #booleanTypeName()}), and Derby does
     * not compare SMALLINT with BOOLEAN ("ERROR 42818: Comparisons between 'SMALLINT' and 'BOOLEAN' are not supported",
     * a boolean level's key, database run 20.10.).
     */
    @Override
    public void quoteBooleanLiteral(StringBuilder buf, String value) {
        String literal = value.trim();
        if (literal.equalsIgnoreCase("TRUE") || literal.equals("1")) {
            buf.append('1');
        } else if (literal.equalsIgnoreCase("FALSE") || literal.equals("0")) {
            buf.append('0');
        } else {
            throw new NumberFormatException("Illegal BOOLEAN literal:  " + value);
        }
    }

    /**
     * Derby has no EXTRACT ("Syntax error: Encountered FROM", database run 30.10.2026); its own YEAR(x), MONTH(x),
     * DAY(x), HOUR(x), MINUTE(x) and SECOND(x) take a part of a date or time.
     */
    @Override
    public StringBuilder generateKnownFunction(org.eclipse.daanse.sql.dialect.api.generator.KnownFunction function,
            List<? extends CharSequence> arguments) {
        return switch (function) {
        case YEAR, MONTH, DAY, HOUR, MINUTE, SECOND -> arguments.size() == 1
                ? new StringBuilder(function.name()).append("(").append(arguments.get(0)).append(")")
                : super.generateKnownFunction(function, arguments);
        default -> super.generateKnownFunction(function, arguments);
        };
    }

    /** A sum keeps the type of its operand: an INTEGER column sums to an INTEGER and overflows past 2^31 - 1. */
    @Override
    public boolean sumKeepsTheTypeOfItsOperand() {
        return true;
    }

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

    /** Derby has no constraint rename — verified unsupported. */
    @Override
    public boolean supportsRenameConstraint() {
        return false;
    }

    /** Derby has no {@code RENAME VIEW} — verified unsupported. */
    @Override
    public boolean supportsRenameView() {
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

    /**
     * Derby averages an integer column to an integer (31 for 31 and 32, the Measure Aggregator Base tutorial, database
     * run 20.10.); the operand is read as DOUBLE, as on SQL Server.
     */
    @Override
    public CharSequence averageOperand(CharSequence operand) {
        return "CAST(" + operand + " AS DOUBLE)";
    }

    /**
     * Derby divides decimals at a scale its precision leaves over: DECIMAL(31,4) by DECIMAL(31,4) at scale 0, so a
     * ratio of 1.9 is 1; integers divide to an integer. The dividend is read as DOUBLE (a native Filter over a calculated
     * measure, FilterTest#testCmNativeFilter, database run 20.10.).
     */
    @Override
    public CharSequence quotientDividend(CharSequence dividend) {
        return "CAST(" + dividend + " AS DOUBLE)";
    }

    /**
     * One writer: Derby locks its system tables for DDL, and tables and indexes created at once deadlock ("A lock could
     * not be obtained due to a deadlock", CREATE INDEX beside CREATE TABLE while FoodMart is loaded, database run 20.10.).
     */
    @Override
    public boolean supportsParallelLoading() {
        return false;
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

    /**
     * Derby: {@code RENAME TABLE "schema"."old" TO "new"} — its own statement
     * shape, not the SQL-99 {@code ALTER TABLE ... RENAME TO ...} default. The
     * new name is unqualified; only the source side carries the schema.
     */
    @Override
    public String renameTable(TableReference table, String newName) {
        if (!supportsRenameTable()) {
            return null;
        }
        return new StringBuilder("RENAME TABLE ").append(qualified(table)).append(" TO ")
                .append(quoteIdentifier(newName)).toString();
    }

    /**
     * Derby: {@code RENAME COLUMN "schema"."table"."old" TO "new"} — the column
     * being renamed is addressed as a schema-qualified {@code table.column} path,
     * not the SQL-99 {@code ALTER TABLE ... RENAME COLUMN ...} default.
     */
    @Override
    public String renameColumn(TableReference table, String oldName, String newName) {
        if (!supportsRenameColumn()) {
            return null;
        }
        return new StringBuilder("RENAME COLUMN ").append(qualified(table)).append('.')
                .append(quoteIdentifier(oldName)).append(" TO ").append(quoteIdentifier(newName)).toString();
    }

    /**
     * Derby: {@code RENAME INDEX "old" TO "new"} — unqualified and with no
     * owning table, unlike the SQL-99 {@code ALTER INDEX ... RENAME TO ...}
     * default.
     */
    @Override
    public String renameIndex(String oldName, String newName, TableReference table) {
        if (!supportsRenameIndex()) {
            return null;
        }
        return new StringBuilder("RENAME INDEX ").append(quoteIdentifier(oldName)).append(" TO ")
                .append(quoteIdentifier(newName)).toString();
    }

    /** Derby cannot store comments on tables or columns. */
    @Override
    public java.util.Optional<String> commentOnTable(TableReference table, String comment) {
        return java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<String> commentOnColumn(TableReference table, String columnName, String comment,
            org.eclipse.daanse.sql.model.schema.ColumnMetaData currentMeta) {
        return java.util.Optional.empty();
    }
}
