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
package org.eclipse.daanse.sql.dialect.api.generator;

import org.eclipse.daanse.sql.model.type.Datatype;

public interface LiteralQuoter {

    void quoteStringLiteral(StringBuilder buf, String s);

    void quoteNumericLiteral(StringBuilder buf, String value);

    void quoteBooleanLiteral(StringBuilder buf, String value);

    /** @param value literal value in ISO format */
    void quoteDateLiteral(StringBuilder buf, String value);

    /** @param value literal value in ISO format */
    void quoteTimeLiteral(StringBuilder buf, String value);

    /** @param value literal value in ISO format */
    void quoteTimestampLiteral(StringBuilder buf, String value);

    StringBuilder quoteDecimalLiteral(CharSequence value);

    /** @param datatype the SQL datatype of the value */
    void quote(StringBuilder buf, Object value, Datatype datatype);

    default char likeEscapeChar() {
        return '\\';
    }

    /** @param value plain-text value to embed verbatim into a LIKE pattern */
    default void quoteLikeLiteral(StringBuilder buf, String value) {
        if (value == null) {
            quoteStringLiteral(buf, null);
            return;
        }
        char esc = likeEscapeChar();
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' || c == '_' || c == esc) {
                escaped.append(esc);
            }
            escaped.append(c);
        }
        quoteStringLiteral(buf, escaped.toString());
    }

    /** @param bytes binary payload (must not be null) */
    default void quoteBinaryLiteral(StringBuilder buf, byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        buf.append("X'");
        for (byte b : bytes) {
            int v = b & 0xff;
            buf.append(Character.forDigit((v >>> 4) & 0xf, 16));
            buf.append(Character.forDigit(v & 0xf, 16));
        }
        buf.append('\'');
    }
}
