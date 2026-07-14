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
package org.eclipse.daanse.sql.dialect.api.generator;

import java.util.List;

/**
 * A statement-level optimizer hint <em>intent</em> — the vocabulary the
 * {@link HintGenerator} spells (the hint-level sibling of {@link KnownFunction}).
 *
 * <p>
 * A hint expresses what the caller wants the optimizer to do, never how a
 * particular database writes it: dialects that have a matching construct spell
 * it ({@code /*+ ... *}{@code /} block, {@code OPTION (...)} clause, ...);
 * dialects without one silently ignore it.
 *
 * @param name      the hint name (e.g. {@code MAX_EXECUTION_TIME}, {@code RECOMPILE})
 * @param arguments the hint arguments, possibly empty (e.g. {@code ["1000"]})
 */
public record StatementHint(String name, List<String> arguments) {

    public StatementHint {
        arguments = List.copyOf(arguments);
    }
}
