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
package org.eclipse.daanse.sql.dialect.api.generator;

/**
 * Portable well-known SQL function <em>intent</em>.
 *
 * <p>
 * Each constant names what the caller wants computed, never how a particular
 * database spells it: the concrete spelling (function name, argument order,
 * {@code EXTRACT} vs. {@code DATEPART}, ...) is the job of the dialect's
 * {@link FunctionGenerator}.
 *
 * <p>
 * Upper/lower case folding is deliberately absent from this vocabulary: case
 * folding already has dedicated dialect hooks (identifier {@code CaseFold} and
 * {@link FunctionGenerator#wrapIntoSqlUpperCaseFunction(CharSequence)}).
 */
public enum KnownFunction {

    /** Substring extraction: {@code SUBSTRING(string, start[, length])}; 2 or 3 arguments. */
    SUBSTRING,

    /** Character length of a string; 1 argument. */
    LENGTH,

    /** String concatenation; 2 or more arguments. */
    CONCAT,

    /** 1-based position of a needle inside a haystack: {@code INDEX_OF(needle, haystack)}; 2 arguments. */
    INDEX_OF,

    /** Strip leading and trailing whitespace; 1 argument. */
    TRIM,

    /** Strip leading whitespace; 1 argument. */
    LTRIM,

    /** Strip trailing whitespace; 1 argument. */
    RTRIM,

    /** Year part of a date/time value; 1 argument. */
    YEAR,

    /** Month part of a date/time value; 1 argument. */
    MONTH,

    /** Day-of-month part of a date/time value; 1 argument. */
    DAY,

    /** Hour part of a time value; 1 argument. */
    HOUR,

    /** Minute part of a time value; 1 argument. */
    MINUTE,

    /** Second part of a time value; 1 argument. */
    SECOND,

    /** Date part of a date/time value; 1 argument. */
    DATE,

    /** Time part of a date/time value; 1 argument. */
    TIME,

    /** Numeric rounding: {@code ROUND(value[, digits])}; 1 or 2 arguments. */
    ROUND,

    /** Largest integer not greater than the value; 1 argument. */
    FLOOR,

    /** Smallest integer not less than the value; 1 argument. */
    CEILING,

    /** Absolute value; 1 argument. */
    ABS,

    /** Remainder of an integer division: {@code MOD(dividend, divisor)}; 2 arguments. */
    MOD,

    /** Exponentiation: {@code POWER(base, exponent)}; 2 arguments. */
    POWER,

    /** Square root; 1 argument. */
    SQRT,

    /** Current date and time; no arguments. */
    NOW
}
