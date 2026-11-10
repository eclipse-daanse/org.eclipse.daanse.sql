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

import org.eclipse.daanse.sql.model.type.Datatype;

/**
 * One bind parameter of a rendered statement, in placeholder order.
 *
 * @param value    the value to bind ({@code null} allowed) when {@code bound}
 *                 is true
 * @param bound    {@code true} if {@code value} is to be bound; {@code false}
 *                 for a marker whose value is supplied at execute time (e.g.
 *                 per batch row)
 * @param datatype the parameter's SQL datatype
 */
public record BoundParameter(Object value, boolean bound, Datatype datatype) {
}
