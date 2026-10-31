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
    NOW,

    /** The English name of the month of a date/time value ({@code January} ... {@code December}); 1 argument. */
    MONTH_NAME,

    /**
     * A value as text, the way the database writes it ({@code 2003}, {@code 2.24}); 1 argument. Transact-SQL writes
     * {@code CONVERT(varchar, x)}.
     */
    TO_TEXT,

    /**
     * A text padded with spaces on the right to a length, or cut to it: {@code PAD_RIGHT(text, length)} with a literal
     * length; 2 arguments. Transact-SQL's {@code CONVERT(CHAR(n), x)} of a text is this (week 1 as {@code "1 "}).
     */
    PAD_RIGHT,

    /**
     * The date of a year, a month and a day, each a number: {@code DATE_FROM_PARTS(year, month, day)}; 3 arguments.
     * Transact-SQL writes {@code DATEFROMPARTS}; a database without such a function reads the ISO text
     * {@code yyyy-mm-dd} as a date.
     */
    DATE_FROM_PARTS,

    /**
     * A whole number as the widest whole number the database has: {@code CAST(x AS BIGINT)}; 1 argument. The operand
     * of a sum over an INTEGER column where the sum keeps its operand's type (SQL Server, Derby) and would overflow
     * past 2 147 483 647 ({@link org.eclipse.daanse.sql.dialect.api.capability.DialectCapabilitiesProvider#sumKeepsTheTypeOfItsOperand()}).
     */
    TO_WIDE_INTEGER
}
