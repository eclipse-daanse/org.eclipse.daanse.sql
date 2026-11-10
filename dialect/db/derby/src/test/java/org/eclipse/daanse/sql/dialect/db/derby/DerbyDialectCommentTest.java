/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.derby;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Test;

class DerbyDialectCommentTest {

    private final DerbyDialect d = new DerbyDialect();
    private final TableReference t = new TableReference(Optional.of(new SchemaReference("s")), "t",
            TableReference.TYPE_TABLE);

    @Test
    void comments() {
        assertThat(d.commentOnTable(t, "x")).isEmpty();
        assertThat(d.commentOnColumn(t, "c", "x", null)).isEmpty();
    }
}
