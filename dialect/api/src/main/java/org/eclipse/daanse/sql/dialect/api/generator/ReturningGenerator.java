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

public interface ReturningGenerator {

    default Optional<String> returning(List<String> columns) {
        return Optional.empty();
    }

    /** Whether this dialect supports any form of RETURNING/OUTPUT clause. */
    default boolean supportsReturning() {
        return false;
    }
}
