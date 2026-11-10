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

import org.eclipse.daanse.sql.statement.api.model.Statement;

/**
 * Turns an immutable {@link Statement} into dialect-specific SQL.
 * Implementations are the only dialect-aware part of the query builder; the
 * model and assembler stay dialect-free.
 */
public interface SqlRenderer {

    /** Renders the statement with the given options. */
    RenderedSql render(Statement statement, RenderOptions options);

    /** Renders the statement with {@link RenderOptions#compact()}. */
    default RenderedSql render(Statement statement) {
        return render(statement, RenderOptions.compact());
    }
}
