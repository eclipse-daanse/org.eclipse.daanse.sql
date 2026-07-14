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

public interface ParameterPlaceholderGenerator {

    /**
     * @param n one-based parameter index (must be ≥ 1)
     * @return the placeholder marker (never null/empty)
     */
    default String placeholder(int n) {
        if (n < 1)
            throw new IllegalArgumentException("parameter index must be >= 1");
        return "?";
    }

    default boolean placeholdersAreIndexed() {
        return false;
    }
}
