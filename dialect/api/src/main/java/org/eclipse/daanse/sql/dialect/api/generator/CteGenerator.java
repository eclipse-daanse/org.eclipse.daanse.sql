/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 */
package org.eclipse.daanse.sql.dialect.api.generator;

import java.util.List;

public interface CteGenerator {

    /** A single common-table expression (name + body). */
    record Cte(String name, String body) {
        public Cte {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (body == null || body.isBlank()) {
                throw new IllegalArgumentException("body must not be blank");
            }
        }
    }

    default String withClause(List<Cte> ctes, boolean recursive) {
        if (ctes == null || ctes.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder("WITH ");
        if (recursive && supportsRecursiveCte() && emitsRecursiveKeyword()) {
            sb.append("RECURSIVE ");
        }
        boolean first = true;
        for (Cte c : ctes) {
            if (!first)
                sb.append(", ");
            sb.append(c.name()).append(" AS (").append(c.body()).append(")");
            first = false;
        }
        sb.append(' ');
        return sb.toString();
    }

    default boolean supportsRecursiveCte() {
        return true;
    }

    default boolean emitsRecursiveKeyword() {
        return true;
    }
}
