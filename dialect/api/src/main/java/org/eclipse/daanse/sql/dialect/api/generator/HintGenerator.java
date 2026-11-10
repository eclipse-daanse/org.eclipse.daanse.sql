/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.api.generator;

import java.util.List;
import java.util.Map;

public interface HintGenerator {

    /**
     * @param buf   buffer being built (the hint, if any, is appended here)
     * @param hints map of hint name → value (semantics dialect-specific)
     */
    default void appendHintsAfterFromClause(StringBuilder buf, Map<String, String> hints) {
        // dialect-specific; default no-op
    }

    /**
     * The optimizer-hint block placed directly after the {@code SELECT} keyword
     * (Oracle/MySQL {@code /*+ ... *}{@code /} style), including a trailing space when
     * non-empty.
     *
     * @param hints the statement-level hint intents
     * @return the hint block, or an empty builder when this dialect has no such block
     *         (the default: the hints are silently ignored)
     */
    default StringBuilder selectHint(List<StatementHint> hints) {
        return new StringBuilder();
    }

    /**
     * The trailing statement option clause (SQL Server {@code OPTION (...)} style),
     * including a leading space when non-empty.
     *
     * @param hints the statement-level hint intents
     * @return the option clause, or an empty builder when this dialect has no such
     *         clause (the default: the hints are silently ignored)
     */
    default StringBuilder statementOption(List<StatementHint> hints) {
        return new StringBuilder();
    }
}
