/*
* Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.sql.jdbc.api.schema;

import org.eclipse.daanse.sql.model.schema.TableReference;

import java.time.Instant;
import java.util.Optional;

public non-sealed interface MaterializedView extends SchemaObject {

    /** @return the table reference for the view */
    TableReference view();

    /** @return the table reference for the view */
    default TableReference reference() {
        return view();
    }

    /** @return the defining SELECT, or empty */
    Optional<String> viewBody();

    /** @return the full CREATE MATERIALIZED VIEW definition, or empty */
    Optional<String> fullDefinition();

    /** @return the refresh mode, or empty if not applicable */
    Optional<String> refreshMode();

    /** @return the last refresh time, or empty */
    Optional<Instant> lastRefresh();
}
