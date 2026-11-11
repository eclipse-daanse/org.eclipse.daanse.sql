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
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.ObjectPrivilege;
import org.eclipse.daanse.sql.jdbc.api.schema.Sequence;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * What every metadata provider reports, checked the same way on every database. Each database gets
 * the same objects:
 *
 * <pre>
 * stock.product      PK (id), UNIQUE uq_product_code (code), code of 20 characters, price DECIMAL(10, 2)
 * sales.orders       PK (id), FK fk_orders_product (product_id) → stock.product,
 *                    CHECK ck_orders_id (id &gt; 0), CHECK without a name (qty &gt; 0), id and qty NOT NULL,
 *                    INDEX ix_orders_ordered_on (ordered_on),
 *                    UNIQUE INDEX ux_orders_product_day (product_id, ordered_on)
 * sales.order_line   PK (order_id, line_no), FK fk_order_line_orders (order_id) → sales.orders
 * sales.v_orders     a view on orders
 * sales.seq_unlimited, sales.seq_limited (1 to 999)
 * sales.f_total      two overloads, where a schema may hold several routines of a name
 * </pre>
 *
 * A subclass opens the database and says how it differs: how it names its schemas, how it creates
 * one, and what it lacks. Names are compared without regard to case, as databases fold them.
 */
@TestInstance(Lifecycle.PER_CLASS)
abstract class MetadataProviderContract {

    private Connection connection;
    private MetadataProvider provider;

    /** Opens the database; the provider reads through this connection. */
    protected abstract Connection open() throws Exception;

    /** The provider under test. */
    protected abstract MetadataProvider provider();

    /** Closes what {@link #open()} started, after the connection is closed. */
    protected void close() throws Exception {
    }

    /** The connection that creates the objects; the reading one unless the database needs more rights. */
    protected Connection fixtureConnection() throws SQLException {
        return connection;
    }

    /** The schema of the orders, as the database stores its name. */
    protected String sales() {
        return "sales";
    }

    /** The schema of the products, as the database stores its name. */
    protected String stock() {
        return "stock";
    }

    /** The statements that create a schema; none where the schema exists already. */
    protected List<String> createSchema(String name) {
        return List.of("CREATE SCHEMA " + name);
    }

    /** The type of a text column of {@code length} characters. */
    protected String text(int length) {
        return "VARCHAR(" + length + ")";
    }

    /** The statement that creates a sequence, with the limits 1 to 999 or without limits. */
    protected String createSequence(String name, boolean limited) {
        return "CREATE SEQUENCE " + name + (limited ? " MINVALUE 1 MAXVALUE 999" : "");
    }

    /**
     * The name an index is created with in {@code schema}; most databases put an index into the
     * schema of its table and refuse a schema here.
     */
    protected String indexName(String schema, String name) {
        return name;
    }

    /** Statements after the products exist, such as a grant the orders' foreign key needs. */
    protected List<String> afterStock() {
        return List.of();
    }

    /** Whether a foreign key may refer to a table in another schema. */
    protected boolean foreignKeysAcrossSchemas() {
        return true;
    }

    /** Whether the database has sequences. */
    protected boolean sequences() {
        return true;
    }

    /** Whether the database keeps the length of a text column. */
    protected boolean keepsTextLength() {
        return true;
    }

    /** Whether the database keeps the names given to constraints. */
    protected boolean keepsConstraintNames() {
        return true;
    }

    /**
     * The statements that create two overloads of the function {@code f_total} in the orders' schema
     * and grant {@code EXECUTE} on the first of them to {@code PUBLIC}:
     *
     * <pre>
     * f_total(qty INT) RETURNS INT
     * f_total(qty INT, INOUT price DECIMAL(10, 2), OUT total DECIMAL(10, 2))
     * </pre>
     *
     * None where a schema holds one routine of a name only.
     */
    protected List<String> createOverloads() {
        return List.of();
    }

    @BeforeAll
    void setUp() throws Exception {
        connection = open();
        provider = provider();
        try (Statement statement = fixtureConnection().createStatement()) {
            for (String sql : fixture()) {
                statement.execute(sql);
            }
        }
    }

    @AfterAll
    void tearDown() throws Exception {
        try {
            if (connection != null) {
                connection.close();
            }
        } finally {
            close();
        }
    }

    private List<String> fixture() {
        String stock = stock();
        String sales = sales();
        List<String> sql = new ArrayList<>(createSchema(stock));
        sql.add("CREATE TABLE " + stock + ".product (id INT NOT NULL, code " + text(20) + " NOT NULL,"
                + " price DECIMAL(10, 2), CONSTRAINT pk_product PRIMARY KEY (id),"
                + " CONSTRAINT uq_product_code UNIQUE (code))");
        sql.addAll(afterStock());
        sql.addAll(createSchema(sales));
        sql.add("CREATE TABLE " + sales + ".orders (id INT NOT NULL, product_id INT, qty INT NOT NULL,"
                + " ordered_on DATE, CONSTRAINT pk_orders PRIMARY KEY (id),"
                + (foreignKeysAcrossSchemas()
                        ? " CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES " + stock
                                + ".product (id),"
                        : "")
                + " CONSTRAINT ck_orders_id CHECK (id > 0), CHECK (qty > 0))");
        sql.add("CREATE TABLE " + sales + ".order_line (order_id INT NOT NULL, line_no INT NOT NULL,"
                + " CONSTRAINT pk_order_line PRIMARY KEY (order_id, line_no),"
                + " CONSTRAINT fk_order_line_orders FOREIGN KEY (order_id) REFERENCES " + sales + ".orders (id))");
        sql.add("CREATE INDEX " + indexName(sales, "ix_orders_ordered_on") + " ON " + sales + ".orders (ordered_on)");
        sql.add("CREATE UNIQUE INDEX " + indexName(sales, "ux_orders_product_day") + " ON " + sales
                + ".orders (product_id, ordered_on)");
        sql.add("CREATE VIEW " + sales + ".v_orders AS SELECT id, qty FROM " + sales + ".orders");
        if (sequences()) {
            sql.add(createSequence(sales + ".seq_unlimited", false));
            sql.add(createSequence(sales + ".seq_limited", true));
        }
        sql.addAll(createOverloads());
        return sql;
    }

    @Test
    void primaryKeysWithTheirColumnsInOrder() throws SQLException {
        var keys = provider.getAllPrimaryKeys(connection, null, sales()).orElseThrow();

        assertThat(keys).filteredOn(key -> is(key.table(), sales(), "orders")).singleElement()
                .satisfies(key -> assertThat(names(key.columns())).containsExactly("id"));
        assertThat(keys).filteredOn(key -> is(key.table(), sales(), "order_line")).singleElement()
                .satisfies(key -> assertThat(names(key.columns())).containsExactly("order_id", "line_no"));
    }

    /**
     * A text column with its length in characters, and a decimal one with its precision and scale. A
     * provider that leaves the columns to the JDBC driver is checked through the driver, where the
     * snapshot reads them too.
     */
    @Test
    void columnsWithTheirLengthPrecisionAndScale() throws SQLException {
        Map<String, ColumnSize> columns = columns(stock(), "product");

        if (keepsTextLength()) {
            assertThat(columns.get("code").size()).as("length of code").hasValue(20);
        }
        assertThat(columns.get("price").size()).as("precision of price").hasValue(10);
        assertThat(columns.get("price").digits()).as("scale of price").hasValue(2);
    }

    @Test
    void foreignKeyWithinASchema() throws SQLException {
        List<ImportedKey> keys = provider.getAllImportedKeys(connection, null, sales()).orElseThrow();

        assertThat(keys).filteredOn(key -> is(key.foreignKeyColumn(), sales(), "order_line", "order_id"))
                .singleElement().satisfies(key -> {
                    assertThat(is(key.primaryKeyColumn(), sales(), "orders", "id")).isTrue();
                    if (keepsConstraintNames()) {
                        assertThat(key.name()).isEqualToIgnoringCase("fk_order_line_orders");
                    }
                });
    }

    @Test
    void foreignKeyAcrossSchemasNamesTheSchemaOfEachSide() throws SQLException {
        assumeTrue(foreignKeysAcrossSchemas(), "the database has no foreign keys across schemas");

        List<ImportedKey> imported = provider.getAllImportedKeys(connection, null, sales()).orElseThrow();
        List<ImportedKey> exported = provider.getAllExportedKeys(connection, null, stock()).orElseThrow();

        for (List<ImportedKey> keys : List.of(imported, exported)) {
            assertThat(keys).filteredOn(key -> is(key.foreignKeyColumn(), sales(), "orders", "product_id"))
                    .singleElement().satisfies(key -> {
                        assertThat(is(key.primaryKeyColumn(), stock(), "product", "id")).isTrue();
                        assertThat(key.name()).isEqualToIgnoringCase("fk_orders_product");
                    });
        }
    }

    @Test
    void uniqueConstraintWithItsColumns() throws SQLException {
        var constraints = provider.getAllUniqueConstraints(connection, null, stock());

        assertThat(constraints).filteredOn(c -> is(c.table(), stock(), "product")).singleElement()
                .satisfies(c -> {
                    assertThat(names(c.columns())).containsExactly("code");
                    if (keepsConstraintNames()) {
                        assertThat(c.name()).isEqualToIgnoringCase("uq_product_code");
                    }
                });
    }

    /**
     * The check named {@code ck_orders_id} and the one without a name; a column's {@code NOT NULL} is
     * its nullability, not a check, though some databases keep it as one.
     */
    @Test
    void namedAndUnnamedChecksButNoNotNull() throws SQLException {
        List<CheckConstraint> checks = provider.getAllCheckConstraints(connection, null, sales()).stream()
                .filter(check -> is(check.table(), sales(), "orders")).toList();

        assertThat(checks).hasSize(2);
        assertThat(checks).noneSatisfy(
                check -> assertThat(check.checkClause()).containsIgnoringCase("NOT NULL"));
        assertThat(checks).anySatisfy(check -> {
            assertThat(check.checkClause()).containsIgnoringCase("id").doesNotContainIgnoringCase("qty");
            if (keepsConstraintNames()) {
                assertThat(check.name()).isEqualToIgnoringCase("ck_orders_id");
            }
        });
        assertThat(checks).anySatisfy(check -> assertThat(check.checkClause()).containsIgnoringCase("qty"));
    }

    @Test
    void indexesWithTheirColumnsInOrder() throws SQLException {
        List<IndexInfo> indexes = provider.getAllIndexInfo(connection, null, sales()).orElseThrow();
        List<IndexInfoItem> items = indexes.stream().filter(index -> is(index.tableReference(), sales(), "orders"))
                .flatMap(index -> index.indexInfoItems().stream()).toList();

        assertThat(index(items, "ix_orders_ordered_on")).extracting(item -> name(item.column().orElseThrow()))
                .containsExactly("ordered_on");
        assertThat(index(items, "ix_orders_ordered_on")).allMatch(item -> !item.unique());
        assertThat(index(items, "ux_orders_product_day")).extracting(item -> name(item.column().orElseThrow()))
                .containsExactly("product_id", "ordered_on");
        assertThat(index(items, "ux_orders_product_day")).allMatch(IndexInfoItem::unique);
    }

    @Test
    void viewWithItsQuery() throws SQLException {
        var views = provider.getAllViewDefinitions(connection, null, sales());

        assertThat(views).filteredOn(view -> is(view.view(), sales(), "v_orders")).singleElement()
                .satisfies(view -> assertThat(view.viewBody().or(view::fullDefinition).orElseThrow())
                        .containsIgnoringCase("orders"));
    }

    @Test
    void sequencesWithAndWithoutLimits() throws SQLException {
        assumeTrue(sequences(), "the database has no sequences");

        List<Sequence> sequences = provider.getAllSequences(connection, null, sales());

        assertThat(sequences).filteredOn(s -> s.name().equalsIgnoreCase("seq_limited")).singleElement()
                .satisfies(s -> {
                    assertThat(s.minValue()).contains(1L);
                    assertThat(s.maxValue()).contains(999L);
                });
        // without a limit: none, or the largest value its type holds
        assertThat(sequences).filteredOn(s -> s.name().equalsIgnoreCase("seq_unlimited")).singleElement()
                .satisfies(s -> assertThat(s.maxValue().orElse(Long.MAX_VALUE))
                        .isGreaterThanOrEqualTo(Integer.MAX_VALUE));
    }

    /**
     * Two functions of one name with different parameters are two routines, each with its own
     * parameters in order and a specific name that tells it from the other. A privilege on one of them
     * names that one by its specific name.
     */
    @Test
    void overloadsAreTwoRoutinesWithTheirOwnParameters() throws SQLException {
        assumeFalse(createOverloads().isEmpty(), "a schema holds one routine of a name only");

        List<Function> overloads = provider.getAllFunctions(connection, null, sales()).stream()
                .filter(f -> f.reference().name().equalsIgnoreCase("f_total")).toList();

        assertThat(overloads).hasSize(2);
        assertThat(overloads).extracting(f -> f.reference().specificName()).doesNotHaveDuplicates();
        assertThat(overloads).extracting(MetadataProviderContract::parameters).containsExactlyInAnyOrder(
                List.of("1 IN qty INTEGER"),
                List.of("1 IN qty INTEGER", "2 INOUT price DECIMAL", "3 OUT total DECIMAL"));
        Function first = overloads.stream().filter(f -> f.columns().size() == 1).findFirst().orElseThrow();

        Optional<List<ObjectPrivilege>> privileges = provider.getAllObjectPrivileges(connection, null, sales());
        privileges.ifPresent(rows -> assertThat(rows)
                .filteredOn(row -> row.objectName().equalsIgnoreCase("f_total")).isNotEmpty()
                .allSatisfy(row -> assertThat(row.specificName()).isEqualTo(first.reference().specificName())));
    }

    /** The connection the provider reads through. */
    protected Connection connection() {
        return connection;
    }

    private record ColumnSize(OptionalInt size, OptionalInt digits) {
    }

    private Map<String, ColumnSize> columns(String schema, String table) throws SQLException {
        Optional<List<ColumnDefinition>> own = provider.getAllColumnDefinitions(connection, null, schema, null,
                null);
        if (own.isPresent()) {
            Map<String, ColumnSize> columns = new HashMap<>();
            for (ColumnDefinition column : own.get()) {
                if (column.column().table().filter(t -> is(t, schema, table)).isPresent()) {
                    ColumnMetaData meta = column.columnMetaData();
                    columns.put(name(column.column()), new ColumnSize(meta.columnSize(), meta.decimalDigits()));
                }
            }
            return columns;
        }
        DatabaseMetaData meta = connection.getMetaData();
        Map<String, ColumnSize> columns = jdbcColumns(meta.getColumns(null, schema, null, null), schema, table);
        // MySQL and MariaDB report a schema as a catalog
        return columns.isEmpty() ? jdbcColumns(meta.getColumns(schema, null, null, null), schema, table) : columns;
    }

    private static Map<String, ColumnSize> jdbcColumns(ResultSet rs, String schema, String table)
            throws SQLException {
        Map<String, ColumnSize> columns = new HashMap<>();
        try (rs) {
            while (rs.next()) {
                boolean inSchema = schema.equalsIgnoreCase(rs.getString("TABLE_CAT"))
                        || schema.equalsIgnoreCase(rs.getString("TABLE_SCHEM"));
                if (inSchema && table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    String name = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                    columns.put(name,
                            new ColumnSize(optionalInt(rs, "COLUMN_SIZE"), optionalInt(rs, "DECIMAL_DIGITS")));
                }
            }
        }
        return columns;
    }

    private static OptionalInt optionalInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? OptionalInt.empty() : OptionalInt.of(value);
    }

    /** Each parameter as {@code position mode name type}; a decimal is a decimal, as NUMERIC or DECIMAL. */
    private static List<String> parameters(Function function) {
        return function.columns().stream().sorted(Comparator.comparingInt(FunctionColumn::ordinalPosition))
                .map(c -> c.ordinalPosition() + " " + c.columnType() + " " + c.name().toLowerCase(Locale.ROOT) + " "
                        + (c.dataType() == JDBCType.NUMERIC ? JDBCType.DECIMAL : c.dataType()))
                .toList();
    }

    private static List<IndexInfoItem> index(List<IndexInfoItem> items, String name) {
        List<IndexInfoItem> index = items.stream()
                .filter(item -> item.indexName().filter(name::equalsIgnoreCase).isPresent())
                .sorted((a, b) -> Integer.compare(a.ordinalPosition(), b.ordinalPosition())).toList();
        assertThat(index).as("index %s", name).isNotEmpty();
        return index;
    }

    static boolean is(TableReference table, String schema, String name) {
        return table.name().equalsIgnoreCase(name)
                && table.schema().map(SchemaReference::name).filter(schema::equalsIgnoreCase).isPresent();
    }

    static boolean is(ColumnReference column, String schema, String table, String name) {
        return column.name().equalsIgnoreCase(name)
                && column.table().filter(t -> is(t, schema, table)).isPresent();
    }

    static String name(ColumnReference column) {
        return column.name().toLowerCase(Locale.ROOT);
    }

    static List<String> names(List<ColumnReference> columns) {
        return columns.stream().map(MetadataProviderContract::name).toList();
    }
}
