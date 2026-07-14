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

import org.eclipse.daanse.sql.model.schema.TableReference;

import java.util.Optional;

public interface Partition {

    String name();

    /** Table this partition belongs to. */
    TableReference table();

    Optional<Integer> ordinalPosition();

    /** The partitioning strategy. */
    PartitionMethod method();

    Optional<String> expression();

    Optional<String> description();

    Optional<Long> rowCount();

    Optional<String> parentPartitionName();

    Optional<PartitionMethod> subPartitionMethod();

    /**
     * Sub-partitioning key expression, paired with {@link #subPartitionMethod()}.
     */
    Optional<String> subPartitionExpression();
}
