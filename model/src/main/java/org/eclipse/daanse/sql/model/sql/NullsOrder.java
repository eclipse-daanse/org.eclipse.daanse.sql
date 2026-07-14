/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.model.sql;

/** Where {@code NULL} values appear within an {@code ORDER BY} result. */
public enum NullsOrder {

    /** {@code NULL} values appear at the beginning of the result. */
    FIRST,

    /** {@code NULL} values appear at the end of the result. */
    LAST
}
