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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface RegexGenerator {

    /** {@code "^(\(\?([a-zA-Z]+)\)).*$"} — Java embedded-flag prefix capture. */
    String FLAGS_REGEXP = "^(\\(\\?([a-zA-Z]+)\\)).*$";

    /** Compiled form of {@link #FLAGS_REGEXP}. */
    Pattern FLAGS_PATTERN = Pattern.compile(FLAGS_REGEXP);

    /**
     * @param source     source column or expression to match against
     * @param javaRegExp Java regular expression pattern (may contain leading
     *                   {@code (?flags)})
     * @return regex match expression, or empty if not supported
     */
    default Optional<String> generateRegularExpression(String source, String javaRegExp) {
        return Optional.empty();
    }

    /**
     * @param javaRegex    Java regex possibly starting with {@code (?flags)}
     * @param mapping      flag-letter translation table (Java → dialect)
     * @param dialectFlags receives the translated dialect flags (appended to)
     * @return {@code javaRegex} with the Java flag prefix removed
     */
    default String extractEmbeddedFlags(String javaRegex, String[][] mapping, StringBuilder dialectFlags) {
        final Matcher flagsMatcher = FLAGS_PATTERN.matcher(javaRegex);
        if (flagsMatcher.matches()) {
            final String flags = flagsMatcher.group(2);
            for (String[] flag : mapping) {
                if (flags.contains(flag[0])) {
                    dialectFlags.append(flag[1]);
                }
            }
            javaRegex = javaRegex.substring(0, flagsMatcher.start(1)) + javaRegex.substring(flagsMatcher.end(1));
        }
        return javaRegex;
    }
}
