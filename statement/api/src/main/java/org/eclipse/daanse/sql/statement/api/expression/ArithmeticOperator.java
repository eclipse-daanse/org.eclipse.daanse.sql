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

/** Binary arithmetic operators for {@link SqlExpression.Binary}. */
public enum ArithmeticOperator {

    ADD("+"), SUBTRACT("-"), MULTIPLY("*"), DIVIDE("/"), MODULO("%");

    private final String symbol;

    ArithmeticOperator(String symbol) {
        this.symbol = symbol;
    }

    /** @return the SQL symbol, e.g. {@code "+"} */
    public String symbol() {
        return symbol;
    }
}
