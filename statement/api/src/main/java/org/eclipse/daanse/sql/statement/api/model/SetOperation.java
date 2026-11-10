/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.statement.api.model;

import java.util.List;
import java.util.Optional;

/**
 * A set operation ({@code UNION ALL}, {@code UNION}, {@code INTERSECT},
 * {@code EXCEPT}) over two or more input statements, with optional trailing
 * {@code ORDER BY} / row limit. Useful e.g. for ORM table-per-class inheritance
 * (a {@code UNION ALL} of per-subclass selects).
 *
 * @param op            the set operator
 * @param inputs        the input statements (size ≥ 2)
 * @param orderKeys     trailing {@code ORDER BY} applied to the whole set, if
 *                      any
 * @param rowLimit      trailing row limit applied to the whole set, if any
 * @param footerComment an optional trailing explanatory comment, appended on
 *                      its own line at the very end of the rendered statement —
 *                      only when the renderer is asked to emit comments; never
 *                      part of the executed SQL
 */
public record SetOperation(SetOp op, List<Statement> inputs, List<OrderKey> orderKeys, Optional<RowLimit> rowLimit,
        Optional<String> footerComment) implements Statement {

    /** Backwards-compatible form without a footer comment. */
    public SetOperation(SetOp op, List<Statement> inputs, List<OrderKey> orderKeys, Optional<RowLimit> rowLimit) {
        this(op, inputs, orderKeys, rowLimit, Optional.empty());
    }

    public static SetOperation unionAll(List<Statement> inputs) {
        return new SetOperation(SetOp.UNION_ALL, inputs, List.of(), Optional.empty());
    }

    /** The set operator. */
    public enum SetOp {
        UNION_ALL("union all"), UNION("union"), INTERSECT("intersect"), EXCEPT("except");

        private final String keyword;

        SetOp(String keyword) {
            this.keyword = keyword;
        }

        /** @return the SQL keyword, e.g. {@code "union all"} */
        public String keyword() {
            return keyword;
        }
    }
}
