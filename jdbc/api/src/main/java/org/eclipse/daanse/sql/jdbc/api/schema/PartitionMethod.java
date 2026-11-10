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
package org.eclipse.daanse.sql.jdbc.api.schema;

/** Strategy used to assign rows to a table partition. */
public enum PartitionMethod {

    /** Partitions defined by contiguous value ranges of the partition key. */
    RANGE,

    /**
     * Partitions defined by an explicit set of values (each value to one
     * partition).
     */
    LIST,

    /** Partitions chosen by a hash of the partition key (engine-defined hash). */
    HASH,

    /**
     * MySQL {@code PARTITION BY KEY} — like {@link #HASH} but using the engine's
     * internal hash.
     */
    KEY,

    /**
     * MySQL {@code PARTITION BY LINEAR HASH} — power-of-two-friendly variant of
     * {@link #HASH}.
     */
    LINEAR_HASH,

    /**
     * MySQL {@code PARTITION BY LINEAR KEY} — power-of-two-friendly variant of
     * {@link #KEY}.
     */
    LINEAR_KEY,

    /** Engine-specific or unrecognized partition method. */
    OTHER
}
