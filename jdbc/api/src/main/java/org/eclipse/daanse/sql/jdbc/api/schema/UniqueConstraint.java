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
import org.eclipse.daanse.sql.model.schema.ColumnReference;

import java.util.List;

public non-sealed interface UniqueConstraint extends Constraint {

    String name();

    TableReference table();

    /** @return ordered list of column references */
    List<ColumnReference> columns();
}
