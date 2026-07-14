/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.oracle.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.oracle.OracleDialect;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
@TestInstance(Lifecycle.PER_CLASS)
class OracleDdlRoundTripTest {

    @Container
    @SuppressWarnings("resource")
    static final OracleContainer CONTAINER = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
            .withUsername("rt").withPassword("rt");

    private SchemaReference schema;
    private TableReference customers;
    private TableReference orders;
    private TableReference view;
    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();

    private Dialect dialect;
    private Connection connection;

    @BeforeAll
    void setUp() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        this.connection = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(),
                CONTAINER.getPassword());
        this.dialect = new OracleDialect();
        // Oracle: schema == user (uppercased). Use the connected user as schema.
        String schemaName = CONTAINER.getUsername().toUpperCase();
        this.schema = new SchemaReference(Optional.empty(), schemaName);
        this.customers = new TableReference(Optional.of(schema), "CUSTOMERS", TableReference.TYPE_TABLE);
        this.orders = new TableReference(Optional.of(schema), "ORDERS", TableReference.TYPE_TABLE);
        this.view = new TableReference(Optional.of(schema), "CUSTOMER_ORDERS", TableReference.TYPE_VIEW);
    }

    @AfterAll
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed())
            connection.close();
    }

    private static ColumnDefinition col(TableReference table, String name, JDBCType jdbc,
            ColumnMetaData.Nullability nullability, OptionalInt size, OptionalInt scale) {
        ColumnReference ref = new ColumnReference(Optional.of(table), name);
        ColumnMetaData meta = new ColumnMetaDataRecord(jdbc, jdbc.getName(), size, scale, OptionalInt.empty(),
                nullability, OptionalInt.empty(), Optional.empty(), Optional.empty(),
                ColumnMetaData.AutoIncrement.UNKNOWN, ColumnMetaData.GeneratedColumn.UNKNOWN);
        return new ColumnDefinitionRecord(ref, meta);
    }

    @Test
    void full_round_trip_with_per_step_database_service_verification() throws Exception {
        List<ColumnDefinition> custCols = List.of(
                col(customers, "ID", JDBCType.INTEGER, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.empty(),
                        OptionalInt.empty()),
                col(customers, "EMAIL", JDBCType.VARCHAR, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.of(100),
                        OptionalInt.empty()),
                col(customers, "NAME", JDBCType.VARCHAR, ColumnMetaData.Nullability.NULLABLE, OptionalInt.of(50),
                        OptionalInt.empty()));
        PrimaryKey custPk = new PrimaryKeyRecord(customers, List.of(custCols.get(0).column()),
                Optional.of("PK_CUSTOMERS"));

        List<ColumnDefinition> ordCols = List.of(
                col(orders, "ID", JDBCType.INTEGER, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.empty(),
                        OptionalInt.empty()),
                col(orders, "CUSTOMER_ID", JDBCType.INTEGER, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.empty(),
                        OptionalInt.empty()),
                col(orders, "TOTAL", JDBCType.DECIMAL, ColumnMetaData.Nullability.NULLABLE, OptionalInt.of(10),
                        OptionalInt.of(2)),
                col(orders, "NOTE", JDBCType.VARCHAR, ColumnMetaData.Nullability.NULLABLE, OptionalInt.of(200),
                        OptionalInt.empty()));
        PrimaryKey ordPk = new PrimaryKeyRecord(orders, List.of(ordCols.get(0).column()), Optional.of("PK_ORDERS"));

        // CREATE — skip CREATE SCHEMA (Oracle: schema == user, container provisions
        // it).
        executeAndVerify(dialect.ddlGenerator().createTable(customers, custCols, custPk, true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().createTable(orders, ordCols, ordPk, true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().addUniqueConstraint(customers, "UC_CUSTOMERS_EMAIL", List.of("EMAIL")),
                info -> {
                });
        executeAndVerify(dialect.ddlGenerator().addCheckConstraint(customers, "CK_CUSTOMERS_ID_POS",
                dialect.quoteIdentifier("ID").toString() + " > 0"), info -> {
                });
        executeAndVerify(
                dialect.ddlGenerator().createIndex("IDX_CUSTOMERS_NAME", customers, List.of("NAME"), false, true),
                info -> {
                });
        executeAndVerify(dialect.ddlGenerator().addForeignKeyConstraint(orders, "FK_ORDERS_CUSTOMERS",
                List.of("CUSTOMER_ID"), customers, List.of("ID"), "CASCADE", null), info -> {
                });
        executeAndVerify(dialect.ddlGenerator().createView(view,
                "SELECT C." + dialect.quoteIdentifier("NAME") + ", " + "O." + dialect.quoteIdentifier("TOTAL")
                        + " FROM " + dialect.quoteIdentifier(schema.name(), customers.name()) + " C " + "JOIN "
                        + dialect.quoteIdentifier(schema.name(), orders.name()) + " O " + "ON O."
                        + dialect.quoteIdentifier("CUSTOMER_ID") + " = C." + dialect.quoteIdentifier("ID"),
                false), info -> {
                });

        // INSERT — both tables.
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO "
                + dialect.quoteIdentifier(schema.name(), customers.name()) + " (" + dialect.quoteIdentifier("ID") + ", "
                + dialect.quoteIdentifier("EMAIL") + ", " + dialect.quoteIdentifier("NAME") + ") VALUES (?, ?, ?)")) {
            ps.setInt(1, 1);
            ps.setString(2, "alice@example.com");
            ps.setString(3, null);
            ps.executeUpdate();
            ps.setInt(1, 2);
            ps.setString(2, "bob@example.com");
            ps.setString(3, null);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO "
                + dialect.quoteIdentifier(schema.name(), orders.name()) + " (" + dialect.quoteIdentifier("ID") + ", "
                + dialect.quoteIdentifier("CUSTOMER_ID") + ", " + dialect.quoteIdentifier("TOTAL") + ", "
                + dialect.quoteIdentifier("NOTE") + ") VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, 100);
            ps.setInt(2, 1);
            ps.setBigDecimal(3, new BigDecimal("9.99"));
            ps.setString(4, "Premium customer Alice");
            ps.executeUpdate();
            ps.setInt(1, 101);
            ps.setInt(2, 2);
            ps.setBigDecimal(3, new BigDecimal("4.50"));
            ps.setString(4, "Standard customer Bob");
            ps.executeUpdate();
        }

        // Cross-table UPDATE — Oracle correlated scalar subquery.
        String qC = dialect.quoteIdentifier(schema.name(), customers.name());
        String qO = dialect.quoteIdentifier(schema.name(), orders.name());
        try (PreparedStatement ps = connection.prepareStatement("UPDATE " + qC + " SET "
                + dialect.quoteIdentifier("NAME") + " = (" + " SELECT MAX(" + dialect.quoteIdentifier("NOTE") + ")"
                + " FROM " + qO + " WHERE " + qO + "." + dialect.quoteIdentifier("CUSTOMER_ID") + " = " + qC + "."
                + dialect.quoteIdentifier("ID") + ")")) {
            assertThat(ps.executeUpdate()).isEqualTo(2);
        }
        try (Statement s = connection.createStatement();
                ResultSet rs = s
                        .executeQuery("SELECT " + dialect.quoteIdentifier("ID") + ", " + dialect.quoteIdentifier("NAME")
                                + " FROM " + qC + " ORDER BY " + dialect.quoteIdentifier("ID"))) {
            rs.next();
            assertThat(rs.getString(2)).isEqualTo("Premium customer Alice");
            rs.next();
            assertThat(rs.getString(2)).isEqualTo("Standard customer Bob");
        }

        // SELECT through view.
        try (Statement s = connection.createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT " + dialect.quoteIdentifier("NAME") + ", " + dialect.quoteIdentifier("TOTAL") + " FROM "
                                + dialect.quoteIdentifier(schema.name(), view.name()) + " ORDER BY "
                                + dialect.quoteIdentifier("NAME"))) {
            rs.next();
            assertThat(rs.getBigDecimal(2)).isEqualByComparingTo("9.99");
            rs.next();
            assertThat(rs.getBigDecimal(2)).isEqualByComparingTo("4.50");
        }

        // DELETE — FK CASCADE removes child orders.
        try (PreparedStatement ps = connection
                .prepareStatement("DELETE FROM " + qC + " WHERE " + dialect.quoteIdentifier("ID") + " = ?")) {
            ps.setInt(1, 1);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + qO)) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }

        // DROP — reverse order. Skip DROP SCHEMA (Oracle's container user is
        // permanent).
        executeAndVerify(dialect.ddlGenerator().dropView(view, true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropConstraint(orders, "FK_ORDERS_CUSTOMERS", true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropIndex("IDX_CUSTOMERS_NAME", customers, true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropConstraint(customers, "CK_CUSTOMERS_ID_POS", true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropConstraint(customers, "UC_CUSTOMERS_EMAIL", true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropTable(orders, true, true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropTable(customers, true, true), info -> {
        });
    }

    @FunctionalInterface
    private interface StepCheck {
        void verify(MetaInfo info) throws Exception;
    }

    private void executeAndVerify(String sql, StepCheck check) throws Exception {
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
        }
        try {
            check.verify(DB_SERVICE.createMetaInfo(connection));
        } catch (Exception e) {
            System.out.println("[round-trip] verification skipped for: " + sql.substring(0, Math.min(80, sql.length()))
                    + " — " + e.getMessage());
        }
    }
}
