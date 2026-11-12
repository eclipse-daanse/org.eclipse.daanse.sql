/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.h2;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.Test;

/** The comments the dialect writes on a view and on one of its columns are those H2 keeps. */
class H2ViewCommentTest {

    @Test
    void view_comments_are_written_and_kept() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:h2viewcomments", "sa", "");
                Statement s = conn.createStatement()) {
            H2Dialect dialect = new H2Dialect(DialectInitData.fromConnection(conn));
            TableReference view = new TableReference(Optional.of(new SchemaReference(Optional.empty(), "PUBLIC")),
                    "V", TableReference.TYPE_VIEW);
            s.execute("CREATE TABLE PUBLIC.T (A INT, B INT)");
            s.execute("CREATE VIEW PUBLIC.V AS SELECT A, B FROM PUBLIC.T");
            s.execute(dialect.ddlGenerator().commentOnTable(view, "the view's comment").orElseThrow());
            s.execute(dialect.ddlGenerator().commentOnColumn(view, "A", "the column's comment", null).orElseThrow());
            try (ResultSet rs = conn.getMetaData().getTables(null, "PUBLIC", "V", null)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("REMARKS")).isEqualTo("the view's comment");
            }
            try (ResultSet rs = conn.getMetaData().getColumns(null, "PUBLIC", "V", "A")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("REMARKS")).isEqualTo("the column's comment");
            }
        }
    }
}
