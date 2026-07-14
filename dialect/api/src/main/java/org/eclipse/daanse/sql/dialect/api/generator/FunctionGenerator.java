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

public interface FunctionGenerator {

    default StringBuilder wrapIntoSqlUpperCaseFunction(CharSequence sqlExpression) {
        return new StringBuilder("UPPER(").append(sqlExpression).append(")");
    }

    /**
     * @param thenExpression value when condition is true
     * @param elseExpression value when condition is false
     */
    default StringBuilder wrapIntoSqlIfThenElseFunction(CharSequence condition, CharSequence thenExpression,
            CharSequence elseExpression) {
        return new StringBuilder("CASE WHEN ").append(condition).append(" THEN ").append(thenExpression)
                .append(" ELSE ").append(elseExpression).append(" END");
    }


    /**
     * Wraps an expression in the SQL {@code GROUPING(...)} super-aggregate function, used
     * together with {@code GROUP BY GROUPING SETS}/{@code ROLLUP} to detect which grouping
     * columns contributed to a given output row.
     *
     * @param expression the grouping column expression
     * @return the {@code GROUPING(expression)} fragment
     */
    default StringBuilder generateGrouping(CharSequence expression) {
        return new StringBuilder("GROUPING(").append(expression).append(")");
    }

    /**
     * Renders a portable {@link KnownFunction} intent in this dialect's spelling.
     *
     * <p>
     * The default implementation emits the most portable (ANSI where possible)
     * spelling; dialects override only the functions whose spelling differs.
     *
     * @param function  the portable function intent
     * @param arguments the already-rendered SQL argument expressions
     * @return the rendered function call
     * @throws IllegalArgumentException if the argument count does not match the
     *                                  function's arity
     */
    default StringBuilder generateKnownFunction(KnownFunction function, java.util.List<? extends CharSequence> arguments) {
        return switch (function) {
        case SUBSTRING -> {
            checkArity(function, arguments, 2, 3);
            StringBuilder sb = new StringBuilder("SUBSTRING(").append(arguments.get(0)).append(", ")
                    .append(arguments.get(1));
            if (arguments.size() == 3) {
                sb.append(", ").append(arguments.get(2));
            }
            yield sb.append(")");
        }
        case LENGTH -> {
            checkArity(function, arguments, 1, 1);
            yield new StringBuilder("CHAR_LENGTH(").append(arguments.get(0)).append(")");
        }
        case CONCAT -> {
            checkArity(function, arguments, 2, Integer.MAX_VALUE);
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    sb.append(" || ");
                }
                sb.append(arguments.get(i));
            }
            yield sb.append(")");
        }
        case INDEX_OF -> {
            checkArity(function, arguments, 2, 2);
            yield new StringBuilder("POSITION(").append(arguments.get(0)).append(" IN ").append(arguments.get(1))
                    .append(")");
        }
        case TRIM -> {
            checkArity(function, arguments, 1, 1);
            yield new StringBuilder("TRIM(").append(arguments.get(0)).append(")");
        }
        case LTRIM -> {
            checkArity(function, arguments, 1, 1);
            yield new StringBuilder("TRIM(LEADING FROM ").append(arguments.get(0)).append(")");
        }
        case RTRIM -> {
            checkArity(function, arguments, 1, 1);
            yield new StringBuilder("TRIM(TRAILING FROM ").append(arguments.get(0)).append(")");
        }
        case YEAR, MONTH, DAY, HOUR, MINUTE, SECOND -> {
            checkArity(function, arguments, 1, 1);
            yield new StringBuilder("EXTRACT(").append(function.name()).append(" FROM ").append(arguments.get(0))
                    .append(")");
        }
        case DATE -> {
            checkArity(function, arguments, 1, 1);
            yield new StringBuilder("CAST(").append(arguments.get(0)).append(" AS DATE)");
        }
        case TIME -> {
            checkArity(function, arguments, 1, 1);
            yield new StringBuilder("CAST(").append(arguments.get(0)).append(" AS TIME)");
        }
        case ROUND -> {
            checkArity(function, arguments, 1, 2);
            StringBuilder sb = new StringBuilder("ROUND(").append(arguments.get(0));
            if (arguments.size() == 2) {
                sb.append(", ").append(arguments.get(1));
            }
            yield sb.append(")");
        }
        case FLOOR, ABS, SQRT -> {
            checkArity(function, arguments, 1, 1);
            yield new StringBuilder(function.name()).append("(").append(arguments.get(0)).append(")");
        }
        case CEILING -> {
            checkArity(function, arguments, 1, 1);
            yield new StringBuilder("CEILING(").append(arguments.get(0)).append(")");
        }
        case MOD, POWER -> {
            checkArity(function, arguments, 2, 2);
            yield new StringBuilder(function.name()).append("(").append(arguments.get(0)).append(", ")
                    .append(arguments.get(1)).append(")");
        }
        case NOW -> {
            checkArity(function, arguments, 0, 0);
            yield new StringBuilder("CURRENT_TIMESTAMP");
        }
        };
    }

    /**
     * Validates the argument count for {@link #generateKnownFunction}.
     *
     * @throws IllegalArgumentException if {@code arguments.size()} is outside
     *                                  {@code [min, max]}
     */
    private static void checkArity(KnownFunction function, java.util.List<? extends CharSequence> arguments, int min,
            int max) {
        int size = arguments.size();
        if (size < min || size > max) {
            String expected;
            if (min == max) {
                expected = String.valueOf(min);
            } else if (max == Integer.MAX_VALUE) {
                expected = min + " or more";
            } else {
                expected = min + ".." + max;
            }
            throw new IllegalArgumentException(
                    function.name() + " expects " + expected + " argument(s), got " + size);
        }
    }
}
