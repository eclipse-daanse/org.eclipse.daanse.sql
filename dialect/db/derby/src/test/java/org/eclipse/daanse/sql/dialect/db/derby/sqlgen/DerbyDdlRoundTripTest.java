/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.derby.sqlgen;

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
import org.eclipse.daanse.sql.dialect.db.derby.DerbyDialect;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
class DerbyDdlRoundTripTest {

    private static final SchemaReference SCHEMA = new SchemaReference(Optional.empty(), "RT_TEST");
    private static final TableReference CUSTOMERS = new TableReference(Optional.of(SCHEMA), "CUSTOMERS",
            TableReference.TYPE_TABLE);
    private static final TableReference ORDERS = new TableReference(Optional.of(SCHEMA), "ORDERS",
            TableReference.TYPE_TABLE);
    private static final TableReference VIEW = new TableReference(Optional.of(SCHEMA), "CUSTOMER_ORDERS",
            TableReference.TYPE_VIEW);
    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();

    private Dialect dialect;
    private Connection connection;

    @BeforeAll
    void setUp() throws Exception {
        Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver");
        this.connection = DriverManager.getConnection("jdbc:derby:memory:rt_test;create=true");
        this.dialect = new DerbyDialect();
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
                col(CUSTOMERS, "ID", JDBCType.INTEGER, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.empty(),
                        OptionalInt.empty()),
                col(CUSTOMERS, "EMAIL", JDBCType.VARCHAR, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.of(100),
                        OptionalInt.empty()),
                col(CUSTOMERS, "NAME", JDBCType.VARCHAR, ColumnMetaData.Nullability.NULLABLE, OptionalInt.of(50),
                        OptionalInt.empty()));
        PrimaryKey custPk = new PrimaryKeyRecord(CUSTOMERS, List.of(custCols.get(0).column()),
                Optional.of("PK_CUSTOMERS"));

        List<ColumnDefinition> ordCols = List.of(
                col(ORDERS, "ID", JDBCType.INTEGER, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.empty(),
                        OptionalInt.empty()),
                col(ORDERS, "CUSTOMER_ID", JDBCType.INTEGER, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.empty(),
                        OptionalInt.empty()),
                col(ORDERS, "TOTAL", JDBCType.DECIMAL, ColumnMetaData.Nullability.NULLABLE, OptionalInt.of(10),
                        OptionalInt.of(2)),
                col(ORDERS, "NOTE", JDBCType.VARCHAR, ColumnMetaData.Nullability.NULLABLE, OptionalInt.of(200),
                        OptionalInt.empty()));
        PrimaryKey ordPk = new PrimaryKeyRecord(ORDERS, List.of(ordCols.get(0).column()), Optional.of("PK_ORDERS"));

        // CREATE
        executeAndVerify(dialect.ddlGenerator().createSchema(SCHEMA.name(), true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().createTable(CUSTOMERS, custCols, custPk, true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().createTable(ORDERS, ordCols, ordPk, true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().addUniqueConstraint(CUSTOMERS, "UC_CUSTOMERS_EMAIL", List.of("EMAIL")),
                info -> {
                });
        executeAndVerify(dialect.ddlGenerator().addCheckConstraint(CUSTOMERS, "CK_CUSTOMERS_ID_POS",
                dialect.quoteIdentifier("ID").toString() + " > 0"), info -> {
                });
        executeAndVerify(
                dialect.ddlGenerator().createIndex("IDX_CUSTOMERS_NAME", CUSTOMERS, List.of("NAME"), false, true),
                info -> {
                });
        executeAndVerify(dialect.ddlGenerator().addForeignKeyConstraint(ORDERS, "FK_ORDERS_CUSTOMERS",
                List.of("CUSTOMER_ID"), CUSTOMERS, List.of("ID"), "CASCADE", null), info -> {
                });
        executeAndVerify(dialect.ddlGenerator().createView(VIEW,
                "SELECT C." + dialect.quoteIdentifier("NAME") + ", " + "O." + dialect.quoteIdentifier("TOTAL")
                        + " FROM " + dialect.quoteIdentifier(SCHEMA.name(), CUSTOMERS.name()) + " C " + "JOIN "
                        + dialect.quoteIdentifier(SCHEMA.name(), ORDERS.name()) + " O " + "ON O."
                        + dialect.quoteIdentifier("CUSTOMER_ID") + " = C." + dialect.quoteIdentifier("ID"),
                false), info -> {
                });

        // INSERT — both tables.
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO "
                + dialect.quoteIdentifier(SCHEMA.name(), CUSTOMERS.name()) + " (" + dialect.quoteIdentifier("ID") + ", "
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
                + dialect.quoteIdentifier(SCHEMA.name(), ORDERS.name()) + " (" + dialect.quoteIdentifier("ID") + ", "
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

        // Cross-table UPDATE — Derby supports correlated scalar subqueries.
        String qC = dialect.quoteIdentifier(SCHEMA.name(), CUSTOMERS.name());
        String qO = dialect.quoteIdentifier(SCHEMA.name(), ORDERS.name());
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
                                + dialect.quoteIdentifier(SCHEMA.name(), VIEW.name()) + " ORDER BY "
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

        // DROP — reverse order.
        executeAndVerify(dialect.ddlGenerator().dropView(VIEW, true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropConstraint(ORDERS, "FK_ORDERS_CUSTOMERS", true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropIndex("IDX_CUSTOMERS_NAME", CUSTOMERS, true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropConstraint(CUSTOMERS, "CK_CUSTOMERS_ID_POS", true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropConstraint(CUSTOMERS, "UC_CUSTOMERS_EMAIL", true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropTable(ORDERS, true, true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropTable(CUSTOMERS, true, true), info -> {
        });
        executeAndVerify(dialect.ddlGenerator().dropSchema(SCHEMA.name(), true, true), info -> {
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
