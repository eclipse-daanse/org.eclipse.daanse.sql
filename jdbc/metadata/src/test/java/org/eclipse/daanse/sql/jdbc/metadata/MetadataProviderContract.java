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
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
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
 *                    UNIQUE INDEX ux_orders_product_day (product_id, ordered_on),
 *                    INDEX ix_orders_large (qty) WHERE qty &gt; 10, where the database has partial indexes,
 *                    INDEX ix_orders_qty_desc (qty DESC, id)
 * sales.order_line   PK (order_id, line_no), FK fk_order_line_orders (order_id) → sales.orders
 * sales.late_line    PK (id), FK fk_late_line_orders (orders_id) → sales.orders, checked at commit,
 *                    where the database has deferrable keys
 * sales.child_bare   PK (id), FK fk_child_bare (orders_id) → sales.orders, no index of its own
 * sales.child_indexed
 *                    PK (id), INDEX ix_child_indexed (orders_id), then FK fk_child_indexed (orders_id)
 * sales.v_orders     a view on orders
 * sales.mv_orders    a materialized view on orders, where the database has them
 * sales.seq_unlimited, sales.seq_limited (1 to 999)
 * sales.f_total      two overloads, where a schema may hold several routines of a name
 * sales.f_answer     a function without parameters, with a comment, where the provider reads it
 * </pre>
 *
 * The product table, its column {@code code} and the view carry a comment each.
 *
 * A subclass opens the database and says how it differs: how it names its schemas, how it creates
 * one, and what it lacks. Names are compared without regard to case, as databases fold them.
 */
@TestInstance(Lifecycle.PER_CLASS)
abstract class MetadataProviderContract {

    /** The comment on {@code stock.product}. */
    protected static final String PRODUCT_COMMENT = "What the shop sells";

    /** The comment on the column {@code code} of {@code stock.product}. */
    protected static final String CODE_COMMENT = "The code on the label";

    /** The comment on the view {@code sales.v_orders}. */
    protected static final String VIEW_COMMENT = "The orders in short";

    /** The comment on the column {@code qty} of the view {@code sales.v_orders}. */
    protected static final String VIEW_COLUMN_COMMENT = "How many are ordered";

    /** The comment on the function {@code sales.f_answer}. */
    protected static final String FUNCTION_COMMENT = "The answer to everything";

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
     * The statements that create the materialized view {@code mv_orders} on the orders in their
     * schema; none where the database has no materialized views.
     */
    protected List<String> createMaterializedView() {
        return List.of();
    }

    /**
     * The statements that create the partial index {@code ix_orders_large} on the column {@code qty}
     * of the orders, over the rows where {@code qty > 10}; none where the database has no partial
     * indexes.
     */
    protected List<String> createFilteredIndex() {
        return List.of();
    }

    /**
     * The statements that create {@code late_line} in the orders' schema, with the foreign key
     * {@code fk_late_line_orders (orders_id)} to the orders that is checked at the end of the
     * transaction ({@code DEFERRABLE INITIALLY DEFERRED}); none where the database checks every key
     * at once.
     */
    protected List<String> createDeferrableForeignKey() {
        return List.of();
    }

    /** Whether the database keeps the order of an index column that sorts descending. */
    protected boolean keepsDescendingIndexColumns() {
        return true;
    }

    /** Whether the database makes an index of its own for a foreign key whose columns have none. */
    protected boolean makesForeignKeyIndexes() {
        return false;
    }

    /**
     * The statements that create the children of the orders: {@code sales.child_bare} with the
     * foreign key {@code fk_child_bare (orders_id)} and no index of its own, and
     * {@code sales.child_indexed}, whose index {@code ix_child_indexed (orders_id)} is declared
     * before its foreign key {@code fk_child_indexed (orders_id)} is added.
     */
    protected List<String> createChildren() {
        String sales = sales();
        return List.of("CREATE TABLE " + sales + ".child_bare (id INT NOT NULL, orders_id INT,"
                + " CONSTRAINT pk_child_bare PRIMARY KEY (id),"
                + " CONSTRAINT fk_child_bare FOREIGN KEY (orders_id) REFERENCES " + sales + ".orders (id))",
                "CREATE TABLE " + sales + ".child_indexed (id INT NOT NULL, orders_id INT,"
                        + " CONSTRAINT pk_child_indexed PRIMARY KEY (id))",
                "CREATE INDEX " + indexName(sales, "ix_child_indexed") + " ON " + sales + ".child_indexed (orders_id)",
                "ALTER TABLE " + sales + ".child_indexed ADD CONSTRAINT fk_child_indexed FOREIGN KEY (orders_id)"
                        + " REFERENCES " + sales + ".orders (id)");
    }

    /**
     * The statements that put the comments above on {@code stock.product}, its column {@code code}
     * and, where {@link #viewComments()}, the view {@code sales.v_orders} and its column {@code qty};
     * by default {@code COMMENT ON}.
     */
    protected List<String> comments() {
        return List.of("COMMENT ON TABLE " + stock() + ".product IS '" + PRODUCT_COMMENT + "'",
                "COMMENT ON COLUMN " + stock() + ".product.code IS '" + CODE_COMMENT + "'",
                "COMMENT ON VIEW " + sales() + ".v_orders IS '" + VIEW_COMMENT + "'",
                "COMMENT ON COLUMN " + sales() + ".v_orders.qty IS '" + VIEW_COLUMN_COMMENT + "'");
    }

    /** Whether the database keeps a comment on a view. */
    protected boolean viewComments() {
        return true;
    }

    /**
     * The statements that create the function {@code f_answer} without parameters in the orders'
     * schema, with the comment {@link #FUNCTION_COMMENT}; none where the database keeps no comment on
     * a function or the provider reads no functions.
     */
    protected List<String> createCommentedFunction() {
        return List.of();
    }

    /**
     * The language of the body of {@code f_answer} as the provider names it; empty where the
     * provider names none.
     */
    protected Optional<String> functionLanguage() {
        return Optional.empty();
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
        sql.addAll(createMaterializedView());
        sql.addAll(createFilteredIndex());
        sql.add("CREATE INDEX " + indexName(sales, "ix_orders_qty_desc") + " ON " + sales + ".orders (qty DESC, id)");
        sql.addAll(createChildren());
        sql.addAll(createDeferrableForeignKey());
        if (sequences()) {
            sql.add(createSequence(sales + ".seq_unlimited", false));
            sql.add(createSequence(sales + ".seq_limited", true));
        }
        sql.addAll(comments());
        sql.addAll(createOverloads());
        sql.addAll(createCommentedFunction());
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
        Map<String, Column> columns = columns(stock(), "product");

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

    /**
     * A foreign key is reported as checked at once, and one checked at the end of the transaction as
     * initially deferred, where the database has such keys.
     */
    @Test
    void foreignKeyWithWhenItIsChecked() throws SQLException {
        List<ImportedKey> keys = provider.getAllImportedKeys(connection, null, sales()).orElseThrow();

        assertThat(keys).filteredOn(key -> is(key.foreignKeyColumn(), sales(), "order_line", "order_id"))
                .isNotEmpty().allSatisfy(key -> assertThat(key.deferrability())
                        .isEqualTo(ImportedKey.Deferrability.NOT_DEFERRABLE));
        if (!createDeferrableForeignKey().isEmpty()) {
            assertThat(keys).filteredOn(key -> is(key.foreignKeyColumn(), sales(), "late_line", "orders_id"))
                    .singleElement().satisfies(key -> assertThat(key.deferrability())
                            .isEqualTo(ImportedKey.Deferrability.INITIALLY_DEFERRED));
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

    /**
     * An index the database made for a foreign key is marked as made for a constraint; one declared
     * before the foreign key and used by it is not. A database that makes no such index has no index
     * on the foreign key's column.
     */
    @Test
    void anIndexTheDatabaseMadeForAForeignKeyIsMarked() throws SQLException {
        List<IndexInfo> indexes = provider.getAllIndexInfo(connection, null, sales()).orElseThrow();
        List<IndexInfoItem> bare = itemsOn(indexes, "child_bare", "orders_id");
        List<IndexInfoItem> indexed = itemsOn(indexes, "child_indexed", "orders_id");

        if (makesForeignKeyIndexes()) {
            assertThat(bare).as("the index made for fk_child_bare").singleElement()
                    .satisfies(item -> assertThat(item.createdForConstraint()).as("made for a constraint").isTrue());
        } else {
            assertThat(bare).as("an index on the column of fk_child_bare").isEmpty();
        }
        assertThat(indexed).as("the index declared before fk_child_indexed").singleElement().satisfies(item -> {
            assertThat(item.indexName()).hasValueSatisfying(n -> assertThat(n).isEqualToIgnoringCase("ix_child_indexed"));
            assertThat(item.createdForConstraint()).as("made for a constraint").isFalse();
        });
    }

    /** A partial index is reported with the condition on its rows, where the database has them. */
    @Test
    void anIndexWithTheConditionOnItsRows() throws SQLException {
        assumeFalse(createFilteredIndex().isEmpty(), "the database has no partial indexes");

        List<IndexInfo> indexes = provider.getAllIndexInfo(connection, null, sales()).orElseThrow();
        List<IndexInfoItem> items = indexes.stream().filter(index -> is(index.tableReference(), sales(), "orders"))
                .flatMap(index -> index.indexInfoItems().stream()).toList();

        assertThat(index(items, "ix_orders_large")).allSatisfy(item -> assertThat(item.filterCondition())
                .hasValueSatisfying(condition -> assertThat(condition).containsIgnoringCase("qty").contains("10")));
    }

    /** An index with a descending column is reported with the order of each of its columns. */
    @Test
    void anIndexWithTheOrderOfItsColumns() throws SQLException {
        assumeTrue(keepsDescendingIndexColumns(), "the database keeps no order in an index");

        List<IndexInfoItem> items = provider.getAllIndexInfo(connection, null, sales()).orElseThrow().stream()
                .filter(index -> is(index.tableReference(), sales(), "orders"))
                .flatMap(index -> index.indexInfoItems().stream()).toList();

        assertThat(index(items, "ix_orders_qty_desc"))
                .extracting(item -> name(item.column().orElseThrow()) + " "
                        + item.ascending().map(ascending -> ascending ? "ASC" : "DESC").orElse("?"))
                .containsExactly("qty DESC", "id ASC");
    }

    @Test
    void viewWithItsQuery() throws SQLException {
        var views = provider.getAllViewDefinitions(connection, null, sales());

        assertThat(views).filteredOn(view -> is(view.view(), sales(), "v_orders")).singleElement()
                .satisfies(view -> assertThat(view.viewBody().or(view::fullDefinition).orElseThrow())
                        .containsIgnoringCase("orders"));
    }

    /** A materialized view is reported as one, with its query, where the database has them. */
    @Test
    void materializedViewWithItsQuery() throws SQLException {
        assumeFalse(createMaterializedView().isEmpty(), "the database has no materialized views");

        assertThat(provider.getAllMaterializedViews(connection, null, sales()))
                .filteredOn(mv -> is(mv.view(), sales(), "mv_orders")).singleElement()
                .satisfies(mv -> assertThat(mv.viewBody().or(mv::fullDefinition).orElseThrow())
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
     * The comments on a table, on one of its columns, on a view and on one of its columns, as the
     * snapshot reads them: from the provider's tables and columns where it reads them itself,
     * otherwise from the JDBC driver.
     */
    @Test
    void commentsOnTablesViewsAndTheirColumns() throws SQLException {
        assertThat(tableRemarks(stock(), "product")).as("comment on the table").contains(PRODUCT_COMMENT);
        assertThat(columns(stock(), "product").get("code").remarks()).as("comment on the column")
                .contains(CODE_COMMENT);
        if (viewComments()) {
            assertThat(tableRemarks(sales(), "v_orders")).as("comment on the view").contains(VIEW_COMMENT);
            assertThat(columns(sales(), "v_orders").get("qty").remarks()).as("comment on the view's column")
                    .contains(VIEW_COLUMN_COMMENT);
        }
    }

    @Test
    void aFunctionWithItsComment() throws SQLException {
        assumeFalse(createCommentedFunction().isEmpty(),
                "the database keeps no comment on a function, or the provider reads none");

        assertThat(provider.getAllFunctions(connection, null, sales()))
                .filteredOn(f -> f.reference().name().equalsIgnoreCase("f_answer")).singleElement()
                .satisfies(f -> assertThat(f.remarks()).contains(FUNCTION_COMMENT));
    }

    /** A function is reported with the language of its body, where the provider names it. */
    @Test
    void aFunctionWithTheLanguageOfItsBody() throws SQLException {
        assumeFalse(createCommentedFunction().isEmpty(),
                "the database keeps no comment on a function, or the provider reads none");

        assertThat(provider.getAllFunctions(connection, null, sales()))
                .filteredOn(f -> f.reference().name().equalsIgnoreCase("f_answer")).singleElement()
                .satisfies(f -> assertThat(f.language()).isEqualTo(functionLanguage()));
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

    /** What the contract reads of a column. */
    private record Column(OptionalInt size, OptionalInt digits, Optional<String> remarks) {
    }

    private Map<String, Column> columns(String schema, String table) throws SQLException {
        Optional<List<ColumnDefinition>> own = provider.getAllColumnDefinitions(connection, null, schema, null,
                null);
        if (own.isPresent()) {
            Map<String, Column> columns = new HashMap<>();
            for (ColumnDefinition column : own.get()) {
                if (column.column().table().filter(t -> is(t, schema, table)).isPresent()) {
                    ColumnMetaData meta = column.columnMetaData();
                    columns.put(name(column.column()),
                            new Column(meta.columnSize(), meta.decimalDigits(), meta.remarks()));
                }
            }
            return columns;
        }
        DatabaseMetaData meta = connection.getMetaData();
        Map<String, Column> columns = jdbcColumns(meta.getColumns(null, schema, null, null), schema, table);
        // MySQL and MariaDB report a schema as a catalog
        return columns.isEmpty() ? jdbcColumns(meta.getColumns(schema, null, null, null), schema, table) : columns;
    }

    private static Map<String, Column> jdbcColumns(ResultSet rs, String schema, String table) throws SQLException {
        Map<String, Column> columns = new HashMap<>();
        try (rs) {
            while (rs.next()) {
                if (inSchema(rs, schema) && table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    String name = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                    columns.put(name, new Column(optionalInt(rs, "COLUMN_SIZE"), optionalInt(rs, "DECIMAL_DIGITS"),
                            Optional.ofNullable(rs.getString("REMARKS"))));
                }
            }
        }
        return columns;
    }

    /** The remarks of a table or view, as the provider or else the JDBC driver reads them. */
    private Optional<String> tableRemarks(String schema, String table) throws SQLException {
        Optional<List<TableDefinition>> own = provider.getAllTableDefinitions(connection, null, schema, null, null);
        if (own.isPresent()) {
            return own.get().stream().filter(t -> is(t.table(), schema, table)).findFirst()
                    .orElseThrow(() -> new AssertionError("no table " + schema + "." + table))
                    .tableMetaData().remarks();
        }
        DatabaseMetaData meta = connection.getMetaData();
        Optional<Optional<String>> remarks = jdbcTableRemarks(meta.getTables(null, schema, null, null), schema, table);
        // MySQL and MariaDB report a schema as a catalog
        return remarks.or(() -> {
            try {
                return jdbcTableRemarks(meta.getTables(schema, null, null, null), schema, table);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }).orElseThrow(() -> new AssertionError("no table " + schema + "." + table));
    }

    private static Optional<Optional<String>> jdbcTableRemarks(ResultSet rs, String schema, String table)
            throws SQLException {
        try (rs) {
            while (rs.next()) {
                if (inSchema(rs, schema) && table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    return Optional.of(Optional.ofNullable(rs.getString("REMARKS")));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean inSchema(ResultSet rs, String schema) throws SQLException {
        return schema.equalsIgnoreCase(rs.getString("TABLE_CAT"))
                || schema.equalsIgnoreCase(rs.getString("TABLE_SCHEM"));
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

    /** The items of all indexes of a table of the orders' schema that cover a column. */
    private List<IndexInfoItem> itemsOn(List<IndexInfo> indexes, String table, String column) {
        return indexes.stream().filter(index -> is(index.tableReference(), sales(), table))
                .flatMap(index -> index.indexInfoItems().stream())
                .filter(item -> item.column().filter(c -> c.name().equalsIgnoreCase(column)).isPresent()).toList();
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
