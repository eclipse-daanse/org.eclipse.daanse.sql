/*
* Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.jdbc.api.schema;

import org.eclipse.daanse.sql.model.schema.Named;
import org.eclipse.daanse.sql.model.schema.SchemaReference;

import java.util.Optional;

public non-sealed interface Sequence extends SchemaObject, Named {

    SequenceReference reference();

    @Override
    default String name() {
        return reference().name();
    }

    /** Convenience: the schema of this sequence. */
    default Optional<SchemaReference> schema() {
        return reference().schema();
    }

    long startValue();

    long incrementBy();

    Optional<Long> minValue();

    Optional<Long> maxValue();

    boolean cycle();

    Optional<Long> cacheSize();

    Optional<String> dataType();
}
