/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.api;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Objects;

public record DialectVersion(int major, int minor, int patch) implements Comparable<DialectVersion> {

    private static final Comparator<DialectVersion> ORDER = Comparator.comparingInt(DialectVersion::major)
            .thenComparingInt(DialectVersion::minor).thenComparingInt(DialectVersion::patch);

    /** Sentinel used when the connection's driver reports no usable version. */
    public static final DialectVersion UNKNOWN = new DialectVersion(0, 0, 0);

    public DialectVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException(
                    "DialectVersion components must be non-negative: " + major + "." + minor + "." + patch);
        }
    }

    /**
     * Convenience: a version with patch=0. Use when the patch level isn't tracked.
     */
    public DialectVersion(int major, int minor) {
        this(major, minor, 0);
    }

    public static DialectVersion of(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        DatabaseMetaData md = connection.getMetaData();
        int maj;
        int min;
        try {
            maj = md.getDatabaseMajorVersion();
            min = md.getDatabaseMinorVersion();
        } catch (SQLException | UnsupportedOperationException e) {
            return UNKNOWN;
        }
        if (maj < 0 || min < 0) {
            return UNKNOWN;
        }
        int patch = parsePatch(md.getDatabaseProductVersion());
        return new DialectVersion(maj, min, patch);
    }

    /** Returns true iff {@code this >= other}. */
    public boolean atLeast(DialectVersion other) {
        return ORDER.compare(this, other) >= 0;
    }

    /** Returns true iff {@code this >= (major.minor.0)}. */
    public boolean atLeast(int major, int minor) {
        return atLeast(new DialectVersion(major, minor, 0));
    }

    /** @return {@code true} if UNKNOWN or {@code this >= (major.minor.0)} */
    public boolean isUnknownOrAtLeast(int major, int minor) {
        return this.equals(UNKNOWN) || atLeast(major, minor);
    }

    @Override
    public int compareTo(DialectVersion o) {
        return ORDER.compare(this, o);
    }

    private static int parsePatch(String productVersion) {
        if (productVersion == null) {
            return 0;
        }
        // Match the third dotted integer (`14.5.2` → `2`, `9.4.26-server` → `26`).
        int dot1 = productVersion.indexOf('.');
        if (dot1 < 0)
            return 0;
        int dot2 = productVersion.indexOf('.', dot1 + 1);
        if (dot2 < 0)
            return 0;
        int end = dot2 + 1;
        while (end < productVersion.length() && Character.isDigit(productVersion.charAt(end))) {
            end++;
        }
        if (end == dot2 + 1)
            return 0;
        try {
            return Integer.parseInt(productVersion.substring(dot2 + 1, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
