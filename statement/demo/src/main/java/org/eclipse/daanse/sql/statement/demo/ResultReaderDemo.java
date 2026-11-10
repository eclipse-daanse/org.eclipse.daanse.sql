/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.statement.demo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.InsertStatementBuilder;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.exec.JdbcStatementExecutor;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;

/**
 * End-to-end demonstration of the read path: build statements with the dialect-free API,
 * render them, execute against a real (in-memory H2) database, and map result rows back into
 * targets via {@link org.eclipse.daanse.sql.statement.api.result.RowMapper}.
 * <p>
 * Two mapping styles are shown, both keyed by the build-time {@link ProjectionRef} handles:
 * <ol>
 * <li>{@link #loadProducts} maps each row into a {@link Product} record;</li>
 * <li>{@link #loadAsAttributes} maps each row into a generic {@link AttributeBinder} — the
 * direct analogue of an ORM's {@code eObject.eSet(feature, value)} or a ROLAP member setter.</li>
 * </ol>
 */
public final class ResultReaderDemo {

    private static final Dialect DIALECT = new AnsiDialect();
    private static final DialectSqlRenderer RENDERER = new DialectSqlRenderer(DIALECT);

    private ResultReaderDemo() {
    }

    /** A demo target record. */
    public record Product(Integer id, String name) {
    }

    /** A generic sink for column values — mirrors {@code EObject.eSet(feature, value)}. */
    public interface AttributeBinder {
        void set(String attribute, Object value);
    }

    /** A {@link Map}-backed {@link AttributeBinder} standing in for an EObject. */
    public static final class MapAttributeBinder implements AttributeBinder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        @Override
        public void set(String attribute, Object value) {
            values.put(attribute, value);
        }

        public Map<String, Object> values() {
            return values;
        }
    }

    /** DDL for the demo table (quoted lowercase identifiers, matching the renderer's quoting). */
    public static String createTableSql() {
        return "create table \"product\" (\"id\" int primary key, \"name\" varchar(50))";
    }

    /** Inserts two rows and reads them back as {@link Product} records. */
    public static List<Product> loadProducts(Connection connection) {
        JdbcStatementExecutor executor = new JdbcStatementExecutor(connection);

        executor.update(RENDERER.render(InsertStatementBuilder.create().into("product").columns("id", "name")
                .addRow(Expressions.literal(1, Datatype.INTEGER), Expressions.literal("Widget", Datatype.VARCHAR))
                .addRow(Expressions.literal(2, Datatype.INTEGER), Expressions.literal("Gadget", Datatype.VARCHAR))
                .build()));

        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias p = TableAlias.of("p");
        q.from(From.table("product", p));
        ProjectionRef idRef = q.project(Expressions.column(p, "id"), BestFitColumnType.INT);
        ProjectionRef nameRef = q.project(Expressions.column(p, "name"), BestFitColumnType.STRING);
        q.orderOn(idRef, SortSpec.asc());
        RenderedSql sql = RENDERER.render(q.build());

        // The same handles used to build the query read it back, already typed.
        return executor.query(sql, row -> new Product(row.getInt(idRef), row.getString(nameRef)));
    }

    /** Reads the rows into generic attribute maps (the EObject/ROLAP correspondence). */
    public static List<Map<String, Object>> loadAsAttributes(Connection connection) {
        JdbcStatementExecutor executor = new JdbcStatementExecutor(connection);

        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias p = TableAlias.of("p");
        q.from(From.table("product", p));
        ProjectionRef idRef = q.project(Expressions.column(p, "id"), BestFitColumnType.INT);
        ProjectionRef nameRef = q.project(Expressions.column(p, "name"), BestFitColumnType.STRING);
        q.orderOn(idRef, SortSpec.asc());
        RenderedSql sql = RENDERER.render(q.build());

        return executor.query(sql, row -> {
            MapAttributeBinder target = new MapAttributeBinder();
            // In an ORM this is eObject.eSet(idFeature, value); in ROLAP a member setter.
            target.set("id", row.get(idRef));
            target.set("name", row.get(nameRef));
            return target.values();
        });
    }

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute(createTableSql());
            }
            System.out.println("-- as records");
            loadProducts(c).forEach(System.out::println);
            System.out.println("-- as attribute maps (EObject/ROLAP correspondence)");
            loadAsAttributes(c).forEach(System.out::println);
        }
    }
}
