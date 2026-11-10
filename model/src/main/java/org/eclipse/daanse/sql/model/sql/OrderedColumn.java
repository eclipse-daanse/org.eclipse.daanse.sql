/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.model.sql;

import java.util.Optional;


/**
 * @param tableName     the name of the table (may be null for unqualified
 *                      columns)
 * @param sortDirection empty = use database default, otherwise ASC or DESC
 * @param nullsOrder    empty = use database default, otherwise FIRST or LAST
 */
public record OrderedColumn(String columnName, String tableName, Optional<SortDirection> sortDirection,
        Optional<NullsOrder> nullsOrder) {

    /**
     * @param tableName the name of the table (may be null for unqualified columns)
     */
    public OrderedColumn(String columnName, String tableName, SortDirection sortDirection) {
        this(columnName, tableName, Optional.of(sortDirection), Optional.empty());
    }

    /**
     * @param tableName the name of the table (may be null for unqualified columns)
     */
    public OrderedColumn(String columnName, String tableName) {
        this(columnName, tableName, Optional.empty(), Optional.empty());
    }

    // Factory methods for convenience

    public static OrderedColumn asc(String columnName) {
        return new OrderedColumn(columnName, null, SortDirection.ASC);
    }

    public static OrderedColumn asc(String tableName, String columnName) {
        return new OrderedColumn(columnName, tableName, SortDirection.ASC);
    }

    public static OrderedColumn desc(String columnName) {
        return new OrderedColumn(columnName, null, SortDirection.DESC);
    }

    public static OrderedColumn desc(String tableName, String columnName) {
        return new OrderedColumn(columnName, tableName, SortDirection.DESC);
    }

    public static OrderedColumn of(String columnName, SortDirection direction, NullsOrder nullsOrder) {
        return new OrderedColumn(columnName, null, Optional.ofNullable(direction), Optional.ofNullable(nullsOrder));
    }

    public static OrderedColumn of(String tableName, String columnName, SortDirection direction,
            NullsOrder nullsOrder) {
        return new OrderedColumn(columnName, tableName, Optional.ofNullable(direction),
                Optional.ofNullable(nullsOrder));
    }
}
