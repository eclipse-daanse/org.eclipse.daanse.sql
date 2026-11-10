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

import java.util.Optional;

public non-sealed interface ViewDefinition extends SchemaObject {

    /** @return the table reference representing the view */
    TableReference view();

    /** @return the view body/query, or empty if not available */
    Optional<String> viewBody();

    /** @return the full CREATE VIEW definition, or empty if not available */
    Optional<String> fullDefinition();
}
