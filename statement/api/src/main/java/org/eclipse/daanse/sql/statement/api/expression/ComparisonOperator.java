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
package org.eclipse.daanse.sql.statement.api.expression;

/** Binary comparison operators for {@link Predicate.Comparison}. */
public enum ComparisonOperator {

    EQ("="), NE("<>"), LT("<"), LE("<="), GT(">"), GE(">=");

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    /** @return the SQL symbol, e.g. {@code "="} or {@code "<>"} */
    public String symbol() {
        return symbol;
    }
}
