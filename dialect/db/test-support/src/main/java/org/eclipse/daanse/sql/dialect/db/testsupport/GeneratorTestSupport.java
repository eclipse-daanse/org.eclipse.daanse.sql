/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.testsupport;

import java.sql.JDBCType;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.generator.MergeGenerator;

/**
 * Static factories for SchemaReference / TableReference / UpsertSpec used by
 * per-engine generator tests.
 */
public final class GeneratorTestSupport {

    private GeneratorTestSupport() {
        // static helpers only
    }

    /** A {@link SchemaReference} with no catalog and the given name. */
    public static SchemaReference schema(String name) {
        return new SchemaReference(Optional.empty(), name);
    }

    public static TableReference table(String schemaName, String name) {
        return new TableReference(Optional.of(schema(schemaName)), name, TableReference.TYPE_TABLE);
    }

    public static TableReference table(String name) {
        return new TableReference(Optional.empty(), name, TableReference.TYPE_TABLE);
    }

    /**
     * @param target  table being upserted
     * @param columns column names in insert order; first is the key
     */
    public static MergeGenerator.UpsertSpec upsertSpec(TableReference target, String... columns) {
        if (columns.length < 1) {
            throw new IllegalArgumentException("at least one column (the key) required");
        }
        List<String> all = List.of(columns);
        List<String> key = List.of(columns[0]);
        List<String> update = all.subList(1, all.size());
        return new MergeGenerator.UpsertSpec(target, key, all, update);
    }

    public static MergeGenerator.UpsertSpec upsertSpecDoNothing(TableReference target, String... columns) {
        if (columns.length < 1) {
            throw new IllegalArgumentException("at least one column (the key) required");
        }
        return new MergeGenerator.UpsertSpec(target, List.of(columns[0]), List.of(columns), List.of());
    }

    /** A minimal {@link ColumnMetaData} for tests that just need some valid metadata. */
    public static ColumnMetaData columnMeta(JDBCType jdbc, OptionalInt size, ColumnMetaData.Nullability nullability) {
        return new SimpleColumnMetaData(jdbc, jdbc.getName(), size, OptionalInt.empty(), OptionalInt.empty(),
                nullability, OptionalInt.empty(), Optional.empty(), Optional.empty(),
                ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN);
    }

    private record SimpleColumnMetaData(JDBCType dataType, String typeName, OptionalInt columnSize,
            OptionalInt decimalDigits, OptionalInt numPrecRadix, ColumnMetaData.Nullability nullability,
            OptionalInt charOctetLength, Optional<String> remarks, Optional<String> columnDefault,
            ColumnMetaData.AutoIncrement autoIncrement, ColumnMetaData.GeneratedColumn generatedColumn)
            implements ColumnMetaData {
    }
}
