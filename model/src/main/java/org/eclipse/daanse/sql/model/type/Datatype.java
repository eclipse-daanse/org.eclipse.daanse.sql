/*
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * For more information please visit the Project: Hitachi Vantara - Mondrian
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial adapt parts of Syntax.class
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.model.type;

import java.util.stream.Stream;


/**
 * Engine-neutral SQL data type tag used by literal-quoting and DDL emission.
 */
public enum Datatype {

    /** Variable-length character string ({@code VARCHAR}). */
    VARCHAR("Varchar"),
    /** Generic exact-numeric type ({@code NUMERIC}). */
    NUMERIC("Numeric"),
    /** 32-bit integer ({@code INTEGER}). */
    INTEGER("Integer"),
    /** Exact numeric with explicit precision/scale ({@code DECIMAL}). */
    DECIMAL("Decimal"),
    /** Approximate single-precision floating-point ({@code FLOAT}). */
    FLOAT("Float"),
    /** Approximate single-precision floating-point ({@code REAL}). */
    REAL("Real"),
    /** 64-bit integer ({@code BIGINT}). */
    BIGINT("BigInt"),
    /** 16-bit integer ({@code SMALLINT}). */
    SMALLINT("SmallInt"),
    /** Approximate double-precision floating-point ({@code DOUBLE}). */
    DOUBLE("Double"),
    /** Boolean truth value ({@code BOOLEAN}). */
    BOOLEAN("Boolean"),
    /** Calendar date without time-of-day ({@code DATE}). */
    DATE("Date"),
    /** Time-of-day without a date ({@code TIME}). */
    TIME("Time"),
    /** Date and time-of-day ({@code TIMESTAMP}). */
    TIMESTAMP("Timestamp"),
    /** Binary / variable-length octet string (BLOB, VARBINARY, BYTEA). */
    BINARY("Binary"),
    /**
     * UUID / GUID. PostgreSQL native, MSSQL {@code uniqueidentifier}, MySQL stored
     * as {@code CHAR(36)}.
     */
    UUID("Uuid"),
    /** JSON / JSONB document. */
    JSON("Json"),
    /** XML document. */
    XML("Xml"),
    /** Day–time or year–month interval. */
    INTERVAL("Interval"),
    /** SQL ARRAY collection of one of the scalar types above. */
    ARRAY("Array"),
    /** Composite / row / struct / object type. */
    STRUCT("Struct");

    private final String value;

    Datatype(String value) {
        this.value = value;
    }

    /** @return the canonical mixed-case display name (e.g. {@code BigInt}) */
    public String getValue() {
        return value;
    }

    /** @return true if this datatype represents a numeric value */
    public boolean isNumeric() {
        return switch (this) {
        case NUMERIC, INTEGER, DECIMAL, FLOAT, REAL, BIGINT, SMALLINT, DOUBLE -> true;
        case VARCHAR, BOOLEAN, DATE, TIME, TIMESTAMP, BINARY, UUID, JSON, XML, INTERVAL, ARRAY, STRUCT -> false;
        };
    }

    /**
     * @return {@code true} for character or character-like types ({@link #VARCHAR},
     *         {@link #JSON}, {@link #XML}, {@link #UUID})
     */
    public boolean isText() {
        return switch (this) {
        case VARCHAR, JSON, XML, UUID -> true;
        default -> false;
        };
    }

    /** @return {@code true} for date, time, timestamp, or interval types */
    public boolean isTemporal() {
        return switch (this) {
        case DATE, TIME, TIMESTAMP, INTERVAL -> true;
        default -> false;
        };
    }

    /** True for binary octet-stream types ({@link #BINARY}). */
    public boolean isBinary() {
        return this == BINARY;
    }

    /** True for collection/composite types ({@link #ARRAY}, {@link #STRUCT}). */
    public boolean isComposite() {
        return this == ARRAY || this == STRUCT;
    }

    /**
     * @param v SQL type name or canonical mixed-case display name; tolerates
     *          whitespace and case differences
     * @return the matching {@link Datatype}, or {@link #NUMERIC} when {@code v} is
     *         {@code null} or unrecognized
     */
    public static Datatype fromValue(String v) {
        if (v == null)
            return NUMERIC;
        String key = v.trim().toUpperCase().replaceAll("\\s+", " ");
        Datatype byAlias = SQL_NAME_ALIASES.get(key);
        if (byAlias != null)
            return byAlias;
        return Stream.of(Datatype.values()).filter(e -> e.getValue().equalsIgnoreCase(v)).findFirst().orElse(NUMERIC);
    }

    private static final java.util.Map<String, Datatype> SQL_NAME_ALIASES = sqlNameAliases();

    private static java.util.Map<String, Datatype> sqlNameAliases() {
        java.util.Map<String, Datatype> m = new java.util.HashMap<>();
        // Character types — every flavour maps to VARCHAR.
        m.put("VARCHAR", VARCHAR);
        m.put("CHAR", VARCHAR);
        m.put("CHARACTER", VARCHAR);
        m.put("CHARACTER VARYING", VARCHAR);
        m.put("CHARACTER LARGE OBJECT", VARCHAR);
        m.put("LONGVARCHAR", VARCHAR);
        m.put("NVARCHAR", VARCHAR);
        m.put("NCHAR", VARCHAR);
        m.put("NATIONAL CHARACTER", VARCHAR);
        m.put("NATIONAL CHARACTER VARYING", VARCHAR);
        m.put("CLOB", VARCHAR);
        m.put("NCLOB", VARCHAR);
        // Integral.
        m.put("INT", INTEGER);
        m.put("INTEGER", INTEGER);
        m.put("BIGINT", BIGINT);
        m.put("SMALLINT", SMALLINT);
        m.put("TINYINT", SMALLINT);
        // Numeric (non-integral).
        m.put("NUMERIC", NUMERIC);
        m.put("DECIMAL", DECIMAL);
        m.put("FLOAT", FLOAT);
        m.put("REAL", REAL);
        m.put("DOUBLE", DOUBLE);
        m.put("DOUBLE PRECISION", DOUBLE);
        // Boolean / bit.
        m.put("BOOLEAN", BOOLEAN);
        m.put("BIT", BOOLEAN);
        // Temporal.
        m.put("DATE", DATE);
        m.put("TIME", TIME);
        m.put("TIME WITH TIMEZONE", TIME);
        m.put("TIMESTAMP", TIMESTAMP);
        m.put("TIMESTAMP WITH TIMEZONE", TIMESTAMP);
        m.put("INTERVAL", INTERVAL);
        // Binary.
        m.put("BINARY", BINARY);
        m.put("VARBINARY", BINARY);
        m.put("LONGVARBINARY", BINARY);
        m.put("BLOB", BINARY);
        m.put("BYTEA", BINARY);
        m.put("RAW", BINARY);
        m.put("BINARY LARGE OBJECT", BINARY);
        // Modern scalars.
        m.put("UUID", UUID);
        m.put("UNIQUEIDENTIFIER", UUID);
        m.put("JSON", JSON);
        m.put("JSONB", JSON);
        m.put("XML", XML);
        // Collections.
        m.put("ARRAY", ARRAY);
        m.put("STRUCT", STRUCT);
        m.put("ROW", STRUCT);
        return m;
    }

    /**
     * @param v the string value to parse
     * @return the corresponding Datatype, or {@code null} if not found
     */
    public static Datatype fromValueOrNull(String v) {
        if (v == null)
            return null;
        String key = v.trim().toUpperCase().replaceAll("\\s+", " ");
        Datatype byAlias = SQL_NAME_ALIASES.get(key);
        if (byAlias != null)
            return byAlias;
        return Stream.of(Datatype.values()).filter(e -> e.getValue().equalsIgnoreCase(v)).findFirst().orElse(null);
    }
}
