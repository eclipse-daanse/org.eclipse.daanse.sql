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

import java.util.OptionalLong;

/**
 * A row-count limit and/or offset. The renderer turns this into the dialect's
 * pagination syntax (leading {@code TOP} or trailing
 * {@code OFFSET ... FETCH}/{@code LIMIT}).
 *
 * @param maxRows maximum rows to return, if limited
 * @param offset  rows to skip, if any
 */
public record RowLimit(OptionalLong maxRows, OptionalLong offset) {

    public static RowLimit of(long maxRows) {
        return new RowLimit(OptionalLong.of(maxRows), OptionalLong.empty());
    }

    public static RowLimit of(long maxRows, long offset) {
        return new RowLimit(OptionalLong.of(maxRows), OptionalLong.of(offset));
    }
}
