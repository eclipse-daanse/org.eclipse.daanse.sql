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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.eclipse.daanse.sql.dialect.api.generator.FunctionGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.KnownFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Table test for the ANSI/most-portable default spellings of {@link KnownFunction}. */
class KnownFunctionDefaultSpellingTest {

    private final FunctionGenerator generator = new AbstractJdbcDialect(
            org.eclipse.daanse.sql.dialect.api.DialectInitData.ansiDefaults()) {

        @Override
        public String name() {
            return null;
        }

    }.functionGenerator();

    static Stream<Arguments> defaultSpellings() {
        return Stream.of( //
                Arguments.of(KnownFunction.SUBSTRING, List.of("x", "2"), "SUBSTRING(x, 2)"),
                Arguments.of(KnownFunction.SUBSTRING, List.of("x", "2", "3"), "SUBSTRING(x, 2, 3)"),
                Arguments.of(KnownFunction.LENGTH, List.of("x"), "CHAR_LENGTH(x)"),
                Arguments.of(KnownFunction.CONCAT, List.of("a", "b"), "(a || b)"),
                Arguments.of(KnownFunction.CONCAT, List.of("a", "b", "c"), "(a || b || c)"),
                Arguments.of(KnownFunction.INDEX_OF, List.of("n", "h"), "POSITION(n IN h)"),
                Arguments.of(KnownFunction.TRIM, List.of("x"), "TRIM(x)"),
                Arguments.of(KnownFunction.LTRIM, List.of("x"), "TRIM(LEADING FROM x)"),
                Arguments.of(KnownFunction.RTRIM, List.of("x"), "TRIM(TRAILING FROM x)"),
                Arguments.of(KnownFunction.YEAR, List.of("x"), "EXTRACT(YEAR FROM x)"),
                Arguments.of(KnownFunction.MONTH, List.of("x"), "EXTRACT(MONTH FROM x)"),
                Arguments.of(KnownFunction.DAY, List.of("x"), "EXTRACT(DAY FROM x)"),
                Arguments.of(KnownFunction.HOUR, List.of("x"), "EXTRACT(HOUR FROM x)"),
                Arguments.of(KnownFunction.MINUTE, List.of("x"), "EXTRACT(MINUTE FROM x)"),
                Arguments.of(KnownFunction.SECOND, List.of("x"), "EXTRACT(SECOND FROM x)"),
                Arguments.of(KnownFunction.DATE, List.of("x"), "CAST(x AS DATE)"),
                Arguments.of(KnownFunction.TIME, List.of("x"), "CAST(x AS TIME)"),
                Arguments.of(KnownFunction.ROUND, List.of("x"), "ROUND(x)"),
                Arguments.of(KnownFunction.ROUND, List.of("x", "2"), "ROUND(x, 2)"),
                Arguments.of(KnownFunction.FLOOR, List.of("x"), "FLOOR(x)"),
                Arguments.of(KnownFunction.CEILING, List.of("x"), "CEILING(x)"),
                Arguments.of(KnownFunction.ABS, List.of("x"), "ABS(x)"),
                Arguments.of(KnownFunction.MOD, List.of("a", "b"), "MOD(a, b)"),
                Arguments.of(KnownFunction.POWER, List.of("a", "b"), "POWER(a, b)"),
                Arguments.of(KnownFunction.SQRT, List.of("x"), "SQRT(x)"),
                Arguments.of(KnownFunction.NOW, List.of(), "CURRENT_TIMESTAMP"));
    }

    @ParameterizedTest
    @MethodSource("defaultSpellings")
    void default_spelling(KnownFunction function, List<String> arguments, String expected) {
        assertThat(generator.generateKnownFunction(function, arguments)).hasToString(expected);
    }

    static Stream<Arguments> arityViolations() {
        return Stream.of( //
                Arguments.of(KnownFunction.SUBSTRING, List.of("x")),
                Arguments.of(KnownFunction.SUBSTRING, List.of("x", "2", "3", "4")),
                Arguments.of(KnownFunction.LENGTH, List.of("x", "y")),
                Arguments.of(KnownFunction.CONCAT, List.of("a")),
                Arguments.of(KnownFunction.INDEX_OF, List.of("n")),
                Arguments.of(KnownFunction.TRIM, List.of()),
                Arguments.of(KnownFunction.YEAR, List.of("x", "y")),
                Arguments.of(KnownFunction.ROUND, List.of("x", "2", "3")),
                Arguments.of(KnownFunction.MOD, List.of("a")),
                Arguments.of(KnownFunction.NOW, List.of("x")));
    }

    @ParameterizedTest
    @MethodSource("arityViolations")
    void arity_violation_names_function_and_expected_arity(KnownFunction function, List<String> arguments) {
        assertThatThrownBy(() -> generator.generateKnownFunction(function, arguments))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(function.name())
                .hasMessageContaining("argument");
    }
}
