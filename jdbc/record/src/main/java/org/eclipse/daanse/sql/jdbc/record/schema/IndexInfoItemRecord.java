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
package org.eclipse.daanse.sql.jdbc.record.schema;

import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.eclipse.daanse.sql.model.schema.ColumnReference;

public record IndexInfoItemRecord(
        Optional<String> indexName,
        IndexType type,
        Optional<ColumnReference> column,
        int ordinalPosition,
        Optional<Boolean> ascending,
        long cardinality,
        long pages,
        Optional<String> filterCondition,
        boolean unique) implements IndexInfoItem {

}
