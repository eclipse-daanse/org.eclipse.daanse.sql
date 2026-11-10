/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 */
package org.eclipse.daanse.sql.dialect.api.generator;

import org.eclipse.daanse.sql.model.type.Datatype;

public interface CastGenerator {

    default String cast(String expr, Datatype targetType) {
        return "CAST(" + expr + " AS " + nativeType(targetType) + ")";
    }

    default String nativeType(Datatype targetType) {
        return targetType.getValue().toUpperCase();
    }

    default boolean supportsTryCast() {
        return false;
    }

    default String tryCast(String expr, Datatype targetType) {
        return cast(expr, targetType);
    }
}
