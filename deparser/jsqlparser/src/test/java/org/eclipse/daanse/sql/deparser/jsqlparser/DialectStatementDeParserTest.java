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

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

class DialectStatementDeParserTest {

    @Test
    void testSimpleSelect_AnsiDialect() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse("SELECT col1 FROM table1");
        stmt.accept(deparser);

        String result = buffer.toString();
        // Identifiers are always quoted
        assertThat(result).contains("\"col1\"");
        assertThat(result).contains("\"table1\"");
    }

    @Test
    void testSimpleSelect_MySqlDialect() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createMySqlDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse("SELECT col1 FROM table1");
        stmt.accept(deparser);

        String result = buffer.toString();
        // MySQL uses backticks; identifiers always quoted
        assertThat(result).contains("`col1`");
        assertThat(result).contains("`table1`");
    }

    @Test
    void testSelectWithStringLiteral() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse("SELECT * FROM table1 WHERE name = 'test'");
        stmt.accept(deparser);

        String result = buffer.toString();
        assertThat(result).contains("'test'");
    }

    @Test
    void testSelectWithDateLiteral() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse("SELECT * FROM table1 WHERE created = DATE '2023-12-15'");
        stmt.accept(deparser);

        String result = buffer.toString();
        assertThat(result).contains("DATE '2023-12-15'");
    }

    @Test
    void testSelectWithJoin() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil
                .parse("SELECT t1.col1, t2.col2 FROM table1 t1 JOIN table2 t2 ON t1.id = t2.id");
        stmt.accept(deparser);

        String result = buffer.toString();
        // Aliases used as qualifiers must remain unquoted; FROM table names are quoted
        assertThat(result).contains("t1.\"col1\"");
        assertThat(result).contains("t2.\"col2\"");
        assertThat(result).contains("\"table1\"");
        assertThat(result).contains("\"table2\"");
        assertThat(result).contains("AS t1");
        assertThat(result).contains("AS t2");
        assertThat(result).doesNotContain("\"t1\"");
        assertThat(result).doesNotContain("\"t2\"");
    }

    @Test
    void testSelectWithSubquery() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse("SELECT * FROM table1 WHERE id IN (SELECT id FROM table2)");
        stmt.accept(deparser);

        String result = buffer.toString();
        assertThat(result).contains("\"id\"");
    }

    /**
     * Regression for the original H2 bug: a column reference's table-qualifier
     * must be emitted verbatim so it matches the unquoted alias declared in the
     * FROM clause. Mixed-case real table/column names are quoted as usual.
     */
    @Test
    void testQualifiedColumnQualifierIsNotQuoted_H2Compatible() throws JSQLParserException {
        Dialect dialect = MockDialectHelper.createAnsiDialect();
        StringBuilder buffer = new StringBuilder();
        BasicDialectStatementDeParser deparser = new BasicDialectStatementDeParser(buffer, dialect);

        Statement stmt = CCJSqlParserUtil.parse(
                "SELECT pc.EnglishProductCategoryName, sum(f.OrderQuantity) "
                        + "FROM Fact f JOIN ProductCategory pc ON f.cat = pc.cat");
        stmt.accept(deparser);

        String result = buffer.toString();
        // Column names always quoted
        assertThat(result).contains("f.\"OrderQuantity\"");
        assertThat(result).contains("pc.\"EnglishProductCategoryName\"");
        assertThat(result).contains("f.\"cat\"");
        assertThat(result).contains("pc.\"cat\"");
        // Aliases as qualifiers stay unquoted
        assertThat(result).contains("AS f");
        assertThat(result).contains("AS pc");
        assertThat(result).doesNotContain("\"f\".");
        assertThat(result).doesNotContain("\"pc\".");
    }

}
