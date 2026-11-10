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
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.sql.jdbc.importer.csv.impl;

public class CsvDataImporterException extends RuntimeException {

    private static final long serialVersionUID = 5181035593294590458L;

    public CsvDataImporterException(String msg, Exception e) {
        super(msg, e);
    }

    public CsvDataImporterException(String msg) {
        super(msg);
    }
}
