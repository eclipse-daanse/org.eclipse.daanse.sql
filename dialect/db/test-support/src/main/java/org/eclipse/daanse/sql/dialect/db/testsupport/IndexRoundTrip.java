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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.IndexColumn;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.IndexDefinition;
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;

/**
 * An index on a database as a dialect writes it, read back by the metadata provider and dropped.
 */
public final class IndexRoundTrip {

    private IndexRoundTrip() {
        // static helpers only
    }

    /**
     * Creates the index {@code index} on {@code table (first DESC, second)}, reads it back, expects
     * the first column descending and the second ascending, in that order, and drops it. Names are
     * compared without regard to case, as some databases fold them.
     */
    public static void assertDescendingIndexRoundTrip(Connection conn, DdlGenerator ddl, MetadataProvider provider,
            TableReference table, String index, String first, String second) throws SQLException {
        IndexDefinition definition = new IndexDefinition(index, table,
                List.of(IndexColumn.descending(first), IndexColumn.ascending(second)), false, null);
        try (Statement s = conn.createStatement()) {
            s.execute(ddl.createIndex(definition, false).orElseThrow());
            String schema = table.schema().map(SchemaReference::name).orElse(null);
            List<IndexInfoItem> items = provider.getAllIndexInfo(conn, null, schema).orElseThrow().stream()
                    .filter(info -> info.tableReference().name().equalsIgnoreCase(table.name()))
                    .flatMap((IndexInfo info) -> info.indexInfoItems().stream())
                    .filter(item -> item.indexName().filter(index::equalsIgnoreCase).isPresent())
                    .sorted(Comparator.comparingInt(IndexInfoItem::ordinalPosition)).toList();
            assertThat(items).extracting(item -> item.column().orElseThrow().name().toLowerCase(Locale.ROOT) + " "
                    + item.ascending().map(ascending -> ascending ? "ASC" : "DESC").orElse("?"))
                    .containsExactly(first.toLowerCase(Locale.ROOT) + " DESC", second.toLowerCase(Locale.ROOT) + " ASC");
            s.execute(ddl.dropIndex(index, table, false));
        }
    }
}
