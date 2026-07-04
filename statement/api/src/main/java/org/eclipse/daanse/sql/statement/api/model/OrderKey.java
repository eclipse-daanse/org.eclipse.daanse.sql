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

import java.util.Optional;

import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

/**
 * One {@code ORDER BY} item.
 * <p>
 * The {@link #expression()} is always present (so a dialect that orders by
 * expression can render it directly). When {@link #projectionRef()} is also
 * present, a dialect that requires/uses order-by aliases may render the
 * projection's alias instead.
 *
 * @param expression    the expression to order by
 * @param projectionRef the projection this key refers to, if it was added by
 *                      handle
 * @param sort          the sort specification
 */
public record OrderKey(SqlExpression expression, Optional<ProjectionRef> projectionRef, SortSpec sort) {
}
