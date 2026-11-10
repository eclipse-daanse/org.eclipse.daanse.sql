/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.hive.sqlgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;

import org.apache.hive.jdbc.HiveDriver;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.hive.HiveDialect;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Round trip against a real HiveServer2 (apache/hive quickstart image,
 * standalone local execution). Hive has no enforced unique/foreign keys and no
 * CREATE INDEX (removed in Hive 3), so the trip covers what the dialect
 * actually emits for Hive: schema + table DDL, backtick quoting, the
 * ISNULL-based ORDER BY NULLS emulation, the {@code cast(... as timestamp)}
 * literal form, a view, and the drops.
 */
@Testcontainers
@EnabledIfSystemProperty(named = "integration.docker", matches = "true")
@TestInstance(Lifecycle.PER_CLASS)
class HiveDdlRoundTripTest {

    private static final int HS2_PORT = 10000;

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> CONTAINER = new GenericContainer<>("apache/hive:4.2.0")
            .withEnv("SERVICE_NAME", "hiveserver2").withExposedPorts(HS2_PORT)
            .waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofMinutes(5));

    private static final SchemaReference SCHEMA = new SchemaReference(Optional.empty(), "RT_TEST");
    private static final TableReference CUSTOMERS = new TableReference(Optional.of(SCHEMA), "CUSTOMERS",
            TableReference.TYPE_TABLE);
    private static final TableReference VIEW = new TableReference(Optional.of(SCHEMA), "CUSTOMER_NAMES",
            TableReference.TYPE_VIEW);

    private Dialect dialect;
    private Connection connection;

    @BeforeAll
    void setUp() throws Exception {
        this.dialect = new HiveDialect();
        String url = "jdbc:hive2://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(HS2_PORT) + "/default";
        // HS2 accepts TCP before sessions work — retry until a statement runs.
        HiveDriver driver = new HiveDriver();
        SQLException last = null;
        for (int i = 0; i < 48; i++) {
            try {
                Connection c = driver.connect(url, new Properties());
                try (Statement s = c.createStatement()) {
                    s.execute("SELECT 1");
                }
                this.connection = c;
                return;
            } catch (SQLException e) {
                last = e;
                Thread.sleep(5000);
            }
        }
        throw new IllegalStateException("HiveServer2 did not become ready", last);
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

    private void execute(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
        }
    }

    @Test
    void full_round_trip() throws Exception {
        List<ColumnDefinition> custCols = List.of(
                col(CUSTOMERS, "ID", JDBCType.INTEGER, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.empty(),
                        OptionalInt.empty()),
                col(CUSTOMERS, "EMAIL", JDBCType.VARCHAR, ColumnMetaData.Nullability.NO_NULLS, OptionalInt.of(100),
                        OptionalInt.empty()),
                col(CUSTOMERS, "NAME", JDBCType.VARCHAR, ColumnMetaData.Nullability.NULLABLE, OptionalInt.of(50),
                        OptionalInt.empty()));

        // CREATE — no PK: Hive constraints are informational only.
        execute(dialect.ddlGenerator().createSchema(SCHEMA.name(), true));
        execute(dialect.ddlGenerator().createTable(CUSTOMERS, custCols, null, true));

        String qC = dialect.quoteIdentifier(SCHEMA.name(), CUSTOMERS.name());

        // INSERT — multi-row VALUES; runs through Hive's local execution.
        execute("INSERT INTO " + qC + " VALUES (1, 'alice@example.com', 'Alice'), (2, 'bob@example.com', NULL)");

        // ORDER BY through the dialect's ISNULL-based NULLS LAST emulation.
        String orderItem = dialect.orderByGenerator()
                .generateOrderItem(dialect.quoteIdentifier("NAME"), true, true, true).toString();
        assertThat(orderItem).contains("ISNULL");
        try (Statement s = connection.createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT " + dialect.quoteIdentifier("NAME") + " FROM " + qC + " ORDER BY " + orderItem)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("Alice");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isNull();
            assertThat(rs.next()).isFalse();
        }

        // Timestamp literal — Hive form cast( '...' as timestamp ).
        StringBuilder ts = new StringBuilder();
        dialect.quoteTimestampLiteral(ts, "2024-01-15 10:30:00");
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT " + ts)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getTimestamp(1)).isEqualTo(Timestamp.valueOf("2024-01-15 10:30:00"));
        }

        // VIEW.
        execute(dialect.ddlGenerator().createView(VIEW,
                "SELECT " + dialect.quoteIdentifier("NAME") + " FROM " + qC, false));
        try (Statement s = connection.createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT COUNT(*) FROM " + dialect.quoteIdentifier(SCHEMA.name(), VIEW.name()))) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }

        // Inline VALUES through the dialect's generateInline form.
        String inlineSql = dialect.sqlGenerator()
                .generateInline(List.of("C1", "C2"), List.of("Varchar", "Numeric"),
                        List.<String[]>of(new String[] { "x", "1" }, new String[] { "y", "2" }))
                .toString();
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(inlineSql)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.next()).isTrue();
            assertThat(rs.next()).isFalse();
        }

        // DROP — reverse order; DROP SCHEMA ... CASCADE cleans up whatever remains.
        execute(dialect.ddlGenerator().dropView(VIEW, true));
        execute(dialect.ddlGenerator().dropTable(CUSTOMERS, true));
        execute(dialect.ddlGenerator().dropSchema(SCHEMA.name(), true, true));
    }
}
