/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 */
package org.eclipse.daanse.sql.dialect.api.generator;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.TableReference;

public interface MergeGenerator {

    /**
     * Identifier columns + columns to update on match + columns to insert on miss.
     */
    record UpsertSpec(TableReference target, List<String> keyColumns, List<String> insertColumns,
            List<String> updateColumns) {
        public UpsertSpec {
            if (target == null)
                throw new IllegalArgumentException("target");
            if (keyColumns == null || keyColumns.isEmpty()) {
                throw new IllegalArgumentException("keyColumns must not be empty");
            }
            if (insertColumns == null || insertColumns.isEmpty()) {
                throw new IllegalArgumentException("insertColumns must not be empty");
            }
            // updateColumns may be empty (insert-or-ignore semantics).
        }
    }

    /**
     * @return the upsert SQL, or empty if this dialect doesn't support the
     *         requested shape (insert-or-ignore vs insert-or-update)
     */
    default Optional<String> upsert(UpsertSpec spec, List<String> values) {
        return Optional.empty();
    }

    default boolean supportsMerge() {
        return false;
    }

    /**
     * @param values one row of values, ordered like {@code spec.insertColumns()}
     * @throws IllegalArgumentException if {@code values} doesn't match the spec
     */
    static void requireValuesMatch(UpsertSpec spec, List<String> values) {
        if (values == null || values.size() != spec.insertColumns().size()) {
            throw new IllegalArgumentException(
                    "values must match insertColumns: expected " + spec.insertColumns().size() + ", got "
                            + (values == null ? "null" : String.valueOf(values.size())));
        }
    }
}
