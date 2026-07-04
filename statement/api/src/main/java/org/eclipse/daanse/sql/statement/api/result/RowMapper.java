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
package org.eclipse.daanse.sql.statement.api.result;

/**
 * Maps one result {@link Row} to a target value. This is the single seam a
 * consumer implements to turn rows into domain objects — e.g. setting EObject
 * attributes via {@code eSet}, or populating a ROLAP member — without touching
 * {@code java.sql}.
 *
 * @param <T> the produced type
 */
@FunctionalInterface
public interface RowMapper<T> {

    T map(Row row);
}
