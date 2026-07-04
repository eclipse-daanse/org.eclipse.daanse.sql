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
package org.eclipse.daanse.sql.statement.api.render;

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;

/**
 * The result of rendering a statement: the SQL text, the column types to read
 * each {@code SELECT} item back as (in order; an entry may be {@code null} if
 * the type is unknown), and the bind parameters in placeholder order. All three
 * are produced together, so they cannot drift out of sync.
 *
 * @param sql         the rendered SQL
 * @param columnTypes the per-column read types, in {@code SELECT} order
 * @param parameters  the bind parameters, in placeholder order (empty if the
 *                    SQL has none)
 */
public record RenderedSql(String sql, List<BestFitColumnType> columnTypes, List<BoundParameter> parameters) {

    /** Convenience for SQL without bind parameters. */
    public static RenderedSql of(String sql, List<BestFitColumnType> columnTypes) {
        return new RenderedSql(sql, columnTypes, List.of());
    }
}
