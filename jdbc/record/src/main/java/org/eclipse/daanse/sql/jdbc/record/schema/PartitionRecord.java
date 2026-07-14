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
package org.eclipse.daanse.sql.jdbc.record.schema;

import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.schema.Partition;
import org.eclipse.daanse.sql.jdbc.api.schema.PartitionMethod;
import org.eclipse.daanse.sql.model.schema.TableReference;

public record PartitionRecord(
        String name,
        TableReference table,
        Optional<Integer> ordinalPosition,
        PartitionMethod method,
        Optional<String> expression,
        Optional<String> description,
        Optional<Long> rowCount,
        Optional<String> parentPartitionName,
        Optional<PartitionMethod> subPartitionMethod,
        Optional<String> subPartitionExpression) implements Partition {
}
