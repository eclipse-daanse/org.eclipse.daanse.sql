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
package org.eclipse.daanse.sql.statement.api.model;

import java.util.List;

/**
 * One named common-table expression in a {@link WithStatement}: a name, an
 * optional explicit column list, and the statement that produces its rows.
 * Reference it from the body by using its {@code name} as a table (e.g.
 * {@code From.table(name, alias)}).
 * <p>
 * An explicit {@code columns} list renders as {@code name(col1, col2, …)} and
 * is recommended for recursive CTEs (where databases like H2 require it).
 *
 * @param name    the CTE name (unquoted; the renderer quotes it consistently)
 * @param columns explicit column names (unquoted); empty means "derive from the
 *                body"
 * @param query   the statement producing the CTE's rows
 */
public record CommonTableExpression(String name, List<String> columns, Statement query) {
}
