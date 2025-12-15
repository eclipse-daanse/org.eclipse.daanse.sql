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
package org.eclipse.daanse.sql.deparser.jsqlparser;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.junit.jupiter.api.Test;

import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

class BaiscDialectExpressionDeParserTest {

    @Test
    void testColumnWithoutTable_AnsiDialect() {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        Column column = new Column("columnName");
        deparser.visit(column, null);

        assertThat(deparser.getBuilder().toString()).isEqualTo("\"columnName\"");
    }

    @Test
    void testColumnWithoutTable_MySqlDialect() {
        Dialect dialect = MockDialectHelper.createMySqlDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        Column column = new Column("columnName");
        deparser.visit(column, null);

        assertThat(deparser.getBuilder().toString()).isEqualTo("`columnName`");
    }

    @Test
    void testColumnWithoutTable_SqlServerDialect() {
        Dialect dialect = MockDialectHelper.createSqlServerDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        Column column = new Column("columnName");
        deparser.visit(column, null);

        assertThat(deparser.getBuilder().toString()).isEqualTo("[columnName]");
    }

    @Test
    void testColumnWithTable_AnsiDialect() {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        Table table = new Table("tableName");
        Column column = new Column(table, "columnName");
        deparser.visit(column, null);

        assertThat(deparser.getBuilder().toString()).isEqualTo("\"tableName\".\"columnName\"");
    }

    @Test
    void testColumnWithTable_MySqlDialect() {
        Dialect dialect = MockDialectHelper.createMySqlDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        Table table = new Table("tableName");
        Column column = new Column(table, "columnName");
        deparser.visit(column, null);

        assertThat(deparser.getBuilder().toString()).isEqualTo("`tableName`.`columnName`");
    }

    @Test
    void testColumnWithTableAlias() {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        Table table = new Table("tableName");
        table.setAlias(new net.sf.jsqlparser.expression.Alias("t"));
        Column column = new Column(table, "columnName");
        deparser.visit(column, null);

        // When table has alias, use alias name
        assertThat(deparser.getBuilder().toString()).isEqualTo("\"t\".\"columnName\"");
    }

    @Test
    void testColumnWithSchemaAndTable() {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        Table table = new Table("schemaName", "tableName");
        Column column = new Column(table, "columnName");
        deparser.visit(column, null);

        // Should use fully qualified name from table
        assertThat(deparser.getBuilder().toString()).isEqualTo("\"schemaName.tableName\".\"columnName\"");
    }

    @Test
    void testStringValue_SimpleString() {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        StringValue stringValue = new StringValue("hello");
        deparser.visit(stringValue, null);

        assertThat(deparser.getBuilder().toString()).isEqualTo("'hello'");
    }

    @Test
    void testStringValue_WithSingleQuote() {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        StringValue stringValue = new StringValue("it's");
        deparser.visit(stringValue, null);

        assertThat(deparser.getBuilder().toString()).isEqualTo("'it''s'");
    }

    @Test
    void testDateValue_AnsiDialect() {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        // Use java.sql.Date directly to avoid parsing issues
        DateValue dateValue = new DateValue(java.sql.Date.valueOf("2023-12-15"));
        deparser.visit(dateValue, null);

        assertThat(deparser.getBuilder().toString()).isEqualTo("DATE '2023-12-15'");
    }

    @Test
    void testTimeValue_AnsiDialect() {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        // Use withValue to set java.sql.Time directly to avoid timezone issues
        TimeValue timeValue = new TimeValue().withValue(java.sql.Time.valueOf("14:30:00"));
        deparser.visit(timeValue, null);

        // The output depends on timezone; just verify it contains TIME keyword and
        // proper format
        assertThat(deparser.getBuilder().toString()).startsWith("TIME '");
        assertThat(deparser.getBuilder().toString()).endsWith("'");
    }

    @Test
    void testTimestampValue_AnsiDialect() {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        // Use withValue to set java.sql.Timestamp directly
        TimestampValue tsValue = new TimestampValue().withValue(java.sql.Timestamp.valueOf("2023-12-15 14:30:00"));
        deparser.visit(tsValue, null);

        assertThat(deparser.getBuilder().toString()).startsWith("TIMESTAMP '");
        assertThat(deparser.getBuilder().toString()).contains("2023-12-15");
    }

    @Test
    void testDateValue_SqlServerDialect() {
        Dialect dialect = MockDialectHelper.createSqlServerDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        DateValue dateValue = new DateValue(java.sql.Date.valueOf("2023-12-15"));
        deparser.visit(dateValue, null);

        assertThat(deparser.getBuilder().toString()).isEqualTo("CONVERT(DATE, '2023-12-15')");
    }

    @Test
    void testLongValue() {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        LongValue longValue = new LongValue(12345);
        deparser.visit(longValue, null);

        assertThat(deparser.getBuilder().toString()).isEqualTo("12345");
    }

    @Test
    void testDoubleValue() {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        BasicDialectExpressionDeParser deparser = new BasicDialectExpressionDeParser(dialect);

        DoubleValue doubleValue = new DoubleValue("123.45");
        deparser.visit(doubleValue, null);

        assertThat(deparser.getBuilder().toString()).isEqualTo("123.45");
    }

}
