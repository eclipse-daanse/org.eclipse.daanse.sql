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

/**
 * A stable handle to a projection returned by the assembler when an expression
 * is added to the {@code SELECT} list.
 * <p>
 * Callers pass this handle to {@code groupOn}/{@code orderOn} instead of
 * re-deriving an alias string. The renderer resolves the handle's
 * {@link #ordinal()} to the effective alias (or back to the expression) per the
 * dialect's capabilities at render time — which is what removes the
 * alias-return coupling of the old builder.
 *
 * @param ordinal       zero-based position of the projection in the
 *                      {@code SELECT} list
 * @param explicitAlias the alias requested by the caller, if any (else the
 *                      renderer may auto-generate one when the dialect allows
 *                      column aliases)
 */
public record ProjectionRef(int ordinal, Optional<ColumnAlias> explicitAlias) {
}
