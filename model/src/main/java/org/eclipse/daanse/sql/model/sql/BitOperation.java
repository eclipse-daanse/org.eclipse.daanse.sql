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
package org.eclipse.daanse.sql.model.sql;

/**
 * Bitwise operation kind that the dialect's {@code FunctionGenerator} can emit.
 */
public enum BitOperation {

    /** Bitwise AND. */
    AND,

    /** Bitwise OR. */
    OR,

    /** Bitwise XOR (exclusive OR). */
    XOR,

    /** Bitwise NAND (negated AND). */
    NAND,

    /** Bitwise NOR (negated OR). */
    NOR,

    /** Bitwise NXOR (negated XOR; equivalence). */
    NXOR
}
