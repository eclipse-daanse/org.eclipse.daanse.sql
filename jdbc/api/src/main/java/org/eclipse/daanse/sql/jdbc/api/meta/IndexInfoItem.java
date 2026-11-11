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

    /**
     * Whether the database created the index itself for a constraint, rather than someone declaring
     * it. A reader that writes the indexes out again leaves such an index to the database.
     * <ul>
     * <li>H2 says so for each index ({@code INFORMATION_SCHEMA.INDEXES.IS_GENERATED}): the index it
     * makes for a foreign key whose columns have no usable index, and those behind a primary key and
     * a unique constraint. An index declared before the constraint and used by it is not.</li>
     * <li>MySQL and MariaDB say it nowhere. For a foreign key whose columns have no usable index
     * (one that starts with them, also a wider one) they make a non-unique index with the name of the
     * constraint over exactly its columns, in order; such an index of a table with that foreign key is
     * taken for the database's own. The rule goes by the name: an index declared by hand under the
     * name of the foreign key is taken for the database's own too. The index stays when the foreign
     * key is dropped; it is then an ordinary index and no longer taken for one.</li>
     * <li>PostgreSQL, Oracle, SQL Server, DuckDB and the others make no index for a foreign key, and
     * their providers report none as created for a constraint, not even one behind a primary key or
     * a unique constraint.</li>
     * </ul>
     *
     * @return true if the database created the index for a constraint
     */
    default boolean createdForConstraint() {
        return false;
    }

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
