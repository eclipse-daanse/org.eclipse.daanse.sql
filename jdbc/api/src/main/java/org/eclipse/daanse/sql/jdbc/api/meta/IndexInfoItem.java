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
package org.eclipse.daanse.sql.jdbc.api.meta;

import java.sql.DatabaseMetaData;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.sql.model.schema.ColumnReference;

public interface IndexInfoItem {

    Optional<String> indexName();

    IndexType type();

    Optional<ColumnReference> column();

    int ordinalPosition();

    Optional<Boolean> ascending();

    long cardinality();

    long pages();

    Optional<String> filterCondition();

    boolean unique();

    enum IndexType {
        TABLE_INDEX_STATISTIC(DatabaseMetaData.tableIndexStatistic),
        TABLE_INDEX_CLUSTERED(DatabaseMetaData.tableIndexClustered),
        TABLE_INDEX_HASHED(DatabaseMetaData.tableIndexHashed), TABLE_INDEX_OTHER(DatabaseMetaData.tableIndexOther);

        private final int value;

        IndexType(int value) {
            this.value = value;
        }

        /**
         * Raw JDBC integer constant for this enum value (per
         * {@link java.sql.DatabaseMetaData}).
         */
        public int getValue() {
            return value;
        }

        /** Look up the enum constant matching the JDBC int code. Throws if no match. */
        public static IndexType of(int value) {
            return Stream.of(IndexType.values()).filter(t -> t.value == value).findFirst().orElse(TABLE_INDEX_OTHER);
        }
    }
}
