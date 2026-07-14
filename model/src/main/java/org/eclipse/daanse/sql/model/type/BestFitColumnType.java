/*
* Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
*   Sergei Semenkov - initial
*/
package org.eclipse.daanse.sql.model.type;

/** Java result-set value type that best represents a JDBC column. */
public enum BestFitColumnType {

    /** Generic {@link Object} — fallback for unrecognized SQL types. */
    OBJECT,

    /** {@code double} — for {@code DOUBLE}, {@code FLOAT}, {@code REAL}. */
    DOUBLE,

    /** {@code int} — for {@code INTEGER} and smaller integer types. */
    INT,

    /** {@code long} — for {@code BIGINT} and large integer types. */
    LONG,

    /**
     * {@link String} — for {@code CHAR}/{@code VARCHAR}/{@code TEXT}-like types.
     */
    STRING,

    /** {@link java.math.BigDecimal} — for {@code DECIMAL}/{@code NUMERIC}. */
    DECIMAL
}
