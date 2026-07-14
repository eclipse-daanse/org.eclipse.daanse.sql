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
package org.eclipse.daanse.sql.dialect.api.generator;

import java.util.List;
import java.util.Map;

import org.eclipse.daanse.sql.model.type.Datatype;

public interface SqlGenerator {

    /**
     * @param columnNames names of the columns in the inline table
     * @param valueList   list of rows, each row being an array of values
     */
    StringBuilder generateInline(List<String> columnNames, List<String> columnTypes, List<String[]> valueList);

}
