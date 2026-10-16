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

package org.eclipse.daanse.sql.guard.jsqltranspiler;

/**
 * Collapses runs of blank lines outside string literals, quoted identifiers and comments into one line break.
 * <p>
 * JSqlParser reads two or more consecutive blank lines as the end of a statement (its {@code ST_SEMICOLON} token),
 * so a well-formatted single query with an empty paragraph in the middle parsed as two broken statements and the
 * guard refused it. Blank lines are whitespace in SQL; inside a literal they are data and stay. A text that is two
 * statements separated only by blank lines becomes one text the parser refuses -- it is never let through.
 */
final class BlankLines {

    private BlankLines() {
    }

    static String collapse(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                int end = closing(sql, i, c);
                out.append(sql, i, end);
                i = end;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                end = end < 0 ? n : end;
                out.append(sql, i, end);
                i = end;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                out.append(sql, i, end);
                i = end;
            } else if (c == '\n' || c == '\r') {
                // one line break for the whole run of breaks and the blanks between them
                int j = i;
                while (j < n && (sql.charAt(j) == '\n' || sql.charAt(j) == '\r' || sql.charAt(j) == ' '
                        || sql.charAt(j) == '\t')) {
                    j++;
                }
                out.append('\n');
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Where a quoted part ends: after its closing quote, a doubled quote standing for the quote itself. */
    private static int closing(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            if (sql.charAt(i) == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }
}
