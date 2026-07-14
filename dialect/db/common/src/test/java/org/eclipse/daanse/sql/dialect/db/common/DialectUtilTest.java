/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.dialect.db.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DialectUtilTest {

    @Nested
    @DisplayName("Single Quote String Tests")
    class SingleQuoteStringTests {

        @Test
        void testSingleQuoteString_Simple() {
            assertEquals("'foo'", DialectUtil.singleQuoteString("foo"));
        }

        @Test
        void testSingleQuoteString_WithSingleQuote() {
            assertEquals("'don''t'", DialectUtil.singleQuoteString("don't"));
        }

        @Test
        void testSingleQuoteString_WithMultipleSingleQuotes() {
            assertEquals("'it''s a ''test'''", DialectUtil.singleQuoteString("it's a 'test'"));
        }

        @Test
        void testSingleQuoteString_Empty() {
            assertEquals("''", DialectUtil.singleQuoteString(""));
        }

        @Test
        void testSingleQuoteString_StringBuilder() {
            StringBuilder buf = new StringBuilder("SELECT ");
            DialectUtil.singleQuoteString("value", buf);
            assertEquals("SELECT 'value'", buf.toString());
        }

        @Test
        void testSingleQuoteString_StringBuilder_WithQuotes() {
            StringBuilder buf = new StringBuilder();
            DialectUtil.singleQuoteString("it's", buf);
            assertEquals("'it''s'", buf.toString());
        }
    }

    @Nested
    @DisplayName("Unicode Case Flag Tests")
    class UnicodeCaseFlagTests {

        @Test
        void testCleanUnicodeAwareCaseFlag_WithFlag() {
            String regex = "(?i)|(?u).*pattern.*";
            String cleaned = DialectUtil.cleanUnicodeAwareCaseFlag(regex);
            assertEquals("(?i).*pattern.*", cleaned);
        }

        @Test
        void testCleanUnicodeAwareCaseFlag_WithoutFlag() {
            String regex = "(?i).*pattern.*";
            String cleaned = DialectUtil.cleanUnicodeAwareCaseFlag(regex);
            assertEquals("(?i).*pattern.*", cleaned);
        }

        @Test
        void testCleanUnicodeAwareCaseFlag_Null() {
            String cleaned = DialectUtil.cleanUnicodeAwareCaseFlag(null);
            assertEquals(null, cleaned);
        }
    }

    @Nested
    @DisplayName("Escape Pattern Tests")
    class EscapePatternTests {

        @Test
        void testEscapePattern_NotNull() {
            assertNotNull(DialectUtil.ESCAPE_PATTERN);
        }

        @Test
        void testEscapePattern_MatchesQuotedSequence() {
            String input = "prefix\\Qescaped content\\Esuffix";
            Matcher matcher = DialectUtil.ESCAPE_PATTERN.matcher(input);
            assertTrue(matcher.find());
            assertEquals("\\Qescaped content\\E", matcher.group(1));
            assertEquals("escaped content", matcher.group(2));
        }

        @Test
        void testEscapePattern_NoMatch() {
            String input = "no escape sequence here";
            Matcher matcher = DialectUtil.ESCAPE_PATTERN.matcher(input);
            assertTrue(!matcher.find());
        }

        @Test
        void testEscapeRegexp_Constant() {
            assertEquals("(\\\\Q([^\\\\Q]+)\\\\E)", DialectUtil.ESCAPE_REGEXP);
        }
    }
}
