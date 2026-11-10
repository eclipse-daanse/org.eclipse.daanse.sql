/*
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*/
package org.eclipse.daanse.sql.jdbc.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.junit.jupiter.api.Test;

/**
 * The metadata contract on DuckDB, in memory. DuckDB has no foreign key across schemas and names
 * every constraint itself.
 */
class DuckDbMetadataContractTest extends MetadataProviderContract {

    @Override
    protected Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:duckdb:");
    }

    @Override
    protected MetadataProvider provider() {
        return new DuckDbMetadataProvider();
    }

    @Override
    protected boolean foreignKeysAcrossSchemas() {
        return false;
    }

    /** DuckDB takes {@code VARCHAR(n)} for any length. */
    @Override
    protected boolean keepsTextLength() {
        return false;
    }

    @Override
    protected boolean keepsConstraintNames() {
        return false;
    }

    @Test
    void theProviderIsFoundByTheProductName() throws SQLException {
        assertThat(MetadataProviders.forConnection(connection())).containsInstanceOf(DuckDbMetadataProvider.class);
    }

    @Test
    void anIndexOnAnExpressionHasAnItemWithoutAColumn() throws SQLException {
        try (Statement s = connection().createStatement()) {
            s.execute("CREATE TABLE main.word (txt VARCHAR, lang VARCHAR)");
            s.execute("CREATE INDEX ix_word_lower ON main.word (lower(txt), lang)");
        }

        var items = provider().getAllIndexInfo(connection(), null, "main").orElseThrow().stream()
                .filter(index -> is(index.tableReference(), "main", "word"))
                .flatMap(index -> index.indexInfoItems().stream()).toList();

        assertThat(items).extracting(IndexInfoItem::ordinalPosition).containsExactly(1, 2);
        assertThat(items.get(0).column()).isEmpty();
        assertThat(items.get(1).column()).map(MetadataProviderContract::name).contains("lang");
    }

    @Test
    void anIndexOnQuotedColumnsNamesThemAsTheyAre() throws SQLException {
        try (Statement s = connection().createStatement()) {
            s.execute("CREATE TABLE main.\"LABEL\" (\"NAME\" VARCHAR, \"Mixed Case\" INT, plain INT)");
            s.execute("CREATE INDEX \"IX_LABEL\" ON main.\"LABEL\" (\"NAME\", \"Mixed Case\", plain)");
        }

        var items = provider().getAllIndexInfo(connection(), null, "main").orElseThrow().stream()
                .filter(index -> is(index.tableReference(), "main", "LABEL"))
                .flatMap(index -> index.indexInfoItems().stream()).toList();

        assertThat(items).extracting(item -> item.column().orElseThrow().name())
                .containsExactly("NAME", "Mixed Case", "plain");
    }

    @Test
    void aListKeepsTheCommasInsideParenthesesAndQuotes() {
        assertThat(DuckDbMetadataProvider.splitList("[a, f(b, c), \"d, e\"]"))
                .containsExactly("a", "f(b, c)", "\"d, e\"");
        assertThat(DuckDbMetadataProvider.splitList("['\"NAME\"', lower_col, 'it''s']"))
                .containsExactly("\"NAME\"", "lower_col", "it's");
        assertThat(DuckDbMetadataProvider.splitList("[]")).isEmpty();
    }
}
