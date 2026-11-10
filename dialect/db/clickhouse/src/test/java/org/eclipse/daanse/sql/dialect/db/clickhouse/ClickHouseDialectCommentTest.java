/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Test;

class ClickHouseDialectCommentTest {

    private final ClickHouseDialect d = new ClickHouseDialect();
    private final TableReference t = new TableReference(Optional.of(new SchemaReference("s")), "t",
            TableReference.TYPE_TABLE);

    @Test
    void comments() {
        assertThat(d.commentOnTable(t, "it's").orElseThrow()).endsWith(" MODIFY COMMENT 'it\\'s'");
        assertThat(d.commentOnColumn(t, "c", "x", null).orElseThrow()).endsWith(" COMMENT COLUMN \"c\" 'x'");
    }
}
