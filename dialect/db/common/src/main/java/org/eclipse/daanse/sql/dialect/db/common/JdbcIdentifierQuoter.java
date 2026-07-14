/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.common;

import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.eclipse.daanse.sql.dialect.api.IdentifierCaseFolding;
import org.eclipse.daanse.sql.dialect.api.IdentifierQuotingPolicy;

/**
 * Default identifier-quoting logic. Pure: callers supply
 * {@code quoteIdentifierString}, {@code quotingPolicy}, {@code caseFolding},
 * {@code sqlKeywordsLower}, and the protected {@code needsQuoting} hook (for
 * subclass overrides).
 */
final class JdbcIdentifierQuoter {

    static final int SINGLE_QUOTE_SIZE = 10;
    static final int DOUBLE_QUOTE_SIZE = 2 * SINGLE_QUOTE_SIZE + 1;
    static final Pattern TRIVIAL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final Supplier<String> quoteIdentifierString;
    private final Supplier<IdentifierQuotingPolicy> quotingPolicy;
    private final Supplier<IdentifierCaseFolding> caseFolding;
    private final Supplier<Set<String>> sqlKeywordsLower;
    private final Predicate<String> needsQuotingHook;

    JdbcIdentifierQuoter(Supplier<String> quoteIdentifierString, Supplier<IdentifierQuotingPolicy> quotingPolicy,
            Supplier<IdentifierCaseFolding> caseFolding, Supplier<Set<String>> sqlKeywordsLower,
            Predicate<String> needsQuotingHook) {
        this.quoteIdentifierString = quoteIdentifierString;
        this.quotingPolicy = quotingPolicy;
        this.caseFolding = caseFolding;
        this.sqlKeywordsLower = sqlKeywordsLower;
        this.needsQuotingHook = needsQuotingHook;
    }

    String quote(CharSequence val) {
        int size = val.length() + SINGLE_QUOTE_SIZE;
        StringBuilder buf = new StringBuilder(size);
        quote(val.toString(), buf);
        return buf.toString();
    }

    void quote(final String val, final StringBuilder buf) {
        emit(val, buf, quotingPolicy.get());
    }

    void quoteWith(final String val, final StringBuilder buf, IdentifierQuotingPolicy policy) {
        if (val == null)
            return;
        emit(val, buf, policy == null ? IdentifierQuotingPolicy.ALWAYS : policy);
    }

    String quoteWith(CharSequence val, IdentifierQuotingPolicy policy) {
        if (val == null)
            return "";
        StringBuilder buf = new StringBuilder(val.length() + SINGLE_QUOTE_SIZE);
        emit(val.toString(), buf, policy == null ? IdentifierQuotingPolicy.ALWAYS : policy);
        return buf.toString();
    }

    String quoteIfNeeded(CharSequence val) {
        if (val == null)
            return "";
        String s = val.toString();
        if (!needsQuotingHook.test(s))
            return s;
        String q = quoteIdentifierString.get();
        if (q == null || (s.startsWith(q) && s.endsWith(q)))
            return s;
        String escaped = s.replace(q, q + q);
        StringBuilder buf = new StringBuilder(s.length() + SINGLE_QUOTE_SIZE);
        buf.append(q).append(escaped).append(q);
        return buf.toString();
    }

    String quoteQualified(final String qual, final String name) {
        int size = name != null ? name.length()
                : 0 + ((qual == null) ? SINGLE_QUOTE_SIZE : (qual.length() + DOUBLE_QUOTE_SIZE));
        StringBuilder buf = new StringBuilder(size);
        quoteParts(buf, qual, name);
        return buf.toString();
    }

    void quoteParts(final StringBuilder buf, final String... names) {
        int nonNullNameCount = 0;
        for (String name : names) {
            if (name == null)
                continue;
            if (nonNullNameCount > 0) {
                buf.append('.');
            }
            assert name.length() > 0 : "name should probably be null, not empty";
            quote(name, buf);
            ++nonNullNameCount;
        }
    }

    private void emit(String val, StringBuilder buf, IdentifierQuotingPolicy policy) {
        String q = quoteIdentifierString.get();
        if (q == null || (val.startsWith(q) && val.endsWith(q))) {
            buf.append(val);
            return;
        }
        switch (policy) {
        case NEVER:
            buf.append(val);
            return;
        case WHEN_NEEDED:
            if (!needsQuotingHook.test(val)) {
                buf.append(val);
                return;
            }
            break;
        case ALWAYS:
        default:
            break;
        }
        String val2 = val.replace(q, q + q);
        buf.append(q).append(val2).append(q);
    }

    /** Default {@code needsQuoting} body — used unless the dialect overrides. */
    boolean defaultNeedsQuoting(String val) {
        if (val == null || val.isEmpty())
            return true;
        if (!TRIVIAL_IDENTIFIER.matcher(val).matches())
            return true;
        if (sqlKeywordsLower.get().contains(val.toLowerCase(Locale.ROOT)))
            return true;
        IdentifierCaseFolding folding = caseFolding.get();
        return folding != IdentifierCaseFolding.PRESERVE && !folding.isCanonical(val);
    }
}
