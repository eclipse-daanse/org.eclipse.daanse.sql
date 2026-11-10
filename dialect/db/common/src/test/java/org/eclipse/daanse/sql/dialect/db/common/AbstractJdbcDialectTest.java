/*
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * For more information please visit the Project: Hitachi Vantara - Mondrian
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial adapt parts of Syntax.class
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.dialect.db.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AbstractJdbcDialectTest {

    private static final String ILLEGAL_BOOLEAN_LITERAL = "illegal for base dialect implemetation boolean literal";
    private static final String ILLEGAL_BOOLEAN_LITERAL_MESSAGE = "Illegal BOOLEAN literal:  ";
    private static final String BOOLEAN_LITERAL_TRUE = "True";
    private static final String BOOLEAN_LITERAL_FALSE = "False";
    private static final String BOOLEAN_LITERAL_ONE = "1";
    private static final String BOOLEAN_LITERAL_ZERO = "0";

    private AbstractJdbcDialect jdbcDialect = new AbstractJdbcDialect(
            org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults()) {

        @Override
        public String name() {
            return null;
        }

    };
    private static StringBuilder buf;

    @BeforeEach
    protected void setUp() throws Exception {
        buf = new StringBuilder();
    }

    @Nested
    @DisplayName("Regular Expression Tests")
    class RegularExpressionTests {

        @Test
        void testAllowsRegularExpressionInWhereClause() {
            assertFalse(jdbcDialect.allowsRegularExpressionInWhereClause());
        }

        @Test
        void testGenerateRegularExpression() {
            assertTrue(jdbcDialect.regexGenerator().generateRegularExpression(null, null).isEmpty());
        }
    }

    @Nested
    @DisplayName("Boolean Literal Quoting Tests")
    class BooleanLiteralQuotingTests {

        @Test
        void testQuoteBooleanLiteral_True() throws Exception {
            assertEquals(0, buf.length());
            jdbcDialect.quoteBooleanLiteral(buf, BOOLEAN_LITERAL_TRUE);
            assertEquals(BOOLEAN_LITERAL_TRUE, buf.toString());
        }

        @Test
        void testQuoteBooleanLiteral_False() throws Exception {
            assertEquals(0, buf.length());
            jdbcDialect.quoteBooleanLiteral(buf, BOOLEAN_LITERAL_FALSE);
            assertEquals(BOOLEAN_LITERAL_FALSE, buf.toString());
        }

        @Test
        void testQuoteBooleanLiteral_OneAccepted() throws Exception {
            // 0/1 are accepted as BOOLEAN literals since the quoteBoolean fix.
            assertEquals(0, buf.length());
            jdbcDialect.quoteBooleanLiteral(buf, BOOLEAN_LITERAL_ONE);
            assertEquals(BOOLEAN_LITERAL_ONE, buf.toString());
        }

        @Test
        void testQuoteBooleanLiteral_ZeroAccepted() throws Exception {
            // 0/1 are accepted as BOOLEAN literals since the quoteBoolean fix.
            assertEquals(0, buf.length());
            jdbcDialect.quoteBooleanLiteral(buf, BOOLEAN_LITERAL_ZERO);
            assertEquals(BOOLEAN_LITERAL_ZERO, buf.toString());
        }

        @Test
        void testQuoteBooleanLiteral_TrowsExceptionOnIllegaLiteral() throws Exception {
            assertEquals(0, buf.length());
            try {
                jdbcDialect.quoteBooleanLiteral(buf, ILLEGAL_BOOLEAN_LITERAL);
                fail("The illegal boolean literal exception should appear BUT it was not.");
            } catch (NumberFormatException e) {
                assertEquals(ILLEGAL_BOOLEAN_LITERAL_MESSAGE + ILLEGAL_BOOLEAN_LITERAL, e.getMessage());
            }
        }
    }
}
