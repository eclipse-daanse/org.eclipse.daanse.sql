/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.jdbc.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.jdbc.metadata.H2MetadataProvider;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The snapshot holds the tables of every schema, so it holds their keys and constraints too — not only
 * those of the connection's current schema, which a provider reads when it is given no schema. The
 * routines and sequences of every schema are read as well, also of a schema that holds no table.
 */
class DatabaseServiceReadsEverySchemaH2Test {

    private static Connection connection;
    private static StructureInfo structure;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:everySchema;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA STOCK");
            stmt.execute("""
                    CREATE TABLE STOCK.PRODUCT (
                        ID INT NOT NULL,
                        CODE VARCHAR(20) NOT NULL,
                        CONSTRAINT PK_PRODUCT PRIMARY KEY (ID),
                        CONSTRAINT UQ_PRODUCT_CODE UNIQUE (CODE)
                    )
                    """);
            stmt.execute("CREATE SCHEMA SALES");
            stmt.execute("""
                    CREATE TABLE SALES.ORDERS (
                        ID INT NOT NULL,
                        PRODUCT_ID INT NOT NULL,
                        QTY INT NOT NULL,
                        CONSTRAINT PK_ORDERS PRIMARY KEY (ID),
                        CONSTRAINT FK_ORDERS_PRODUCT FOREIGN KEY (PRODUCT_ID) REFERENCES STOCK.PRODUCT (ID),
                        CONSTRAINT CK_ORDERS_QTY CHECK (QTY > 0)
                    )
                    """);
            // the current schema stays PUBLIC: no table, a sequence
            stmt.execute("CREATE SEQUENCE PUBLIC.SEQ_ORDER_NO");
            // a schema without a table: a function and a sequence
            stmt.execute("CREATE SCHEMA ROUTINES");
            stmt.execute("CREATE ALIAS ROUTINES.F_ALONE FOR 'java.lang.System.currentTimeMillis'");
            stmt.execute("CREATE SEQUENCE ROUTINES.SEQ_ALONE");
        }
        MetaInfo metaInfo = new DatabaseServiceImpl().createMetaInfo(connection, new H2MetadataProvider());
        structure = metaInfo.structureInfo();
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
        connection.close();
    }

    @Test
    void primaryKeysOfBothSchemas() {
        assertThat(structure.primaryKeys()).extracting(pk -> name(pk.table()))
                .contains("STOCK.PRODUCT", "SALES.ORDERS");
    }

    @Test
    void foreignKeyAcrossSchemasPointsIntoTheOtherSchema() {
        assertThat(structure.importedKeys()).filteredOn(fk -> "FK_ORDERS_PRODUCT".equals(fk.name()))
                .singleElement().satisfies(fk -> {
                    assertThat(name(fk.foreignKeyColumn().table().orElseThrow())).isEqualTo("SALES.ORDERS");
                    assertThat(name(fk.primaryKeyColumn().table().orElseThrow())).isEqualTo("STOCK.PRODUCT");
                });
    }

    @Test
    void checkAndUniqueConstraintsOfBothSchemas() {
        assertThat(structure.checkConstraints()).filteredOn(c -> "CK_ORDERS_QTY".equals(c.name()))
                .singleElement().satisfies(c -> assertThat(name(c.table())).isEqualTo("SALES.ORDERS"));
        assertThat(structure.uniqueConstraints()).filteredOn(c -> "UQ_PRODUCT_CODE".equals(c.name()))
                .singleElement().satisfies(c -> assertThat(name(c.table())).isEqualTo("STOCK.PRODUCT"));
    }

    @Test
    void theCurrentSchemaIsReadThoughItHoldsNoTable() {
        assertThat(structure.sequences()).extracting(s -> s.name()).contains("SEQ_ORDER_NO");
    }

    @Test
    void routinesAndSequencesOfASchemaWithoutATableAreRead() {
        assertThat(structure.functions()).anySatisfy(f -> {
            assertThat(f.reference().name()).isEqualTo("F_ALONE");
            assertThat(f.reference().schema()).map(SchemaReference::name).contains("ROUTINES");
        });
        assertThat(structure.sequences()).extracting(s -> s.name()).contains("SEQ_ALONE");
    }

    private static String name(TableReference table) {
        return table.schema().map(SchemaReference::name).orElse("?") + "." + table.name();
    }
}
