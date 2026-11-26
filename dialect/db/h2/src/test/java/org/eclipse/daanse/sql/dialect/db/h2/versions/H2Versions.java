/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.dialect.db.h2.versions;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;

/**
 * An H2 of a given version, in memory. H2 is embedded: each version is the jar the {@code h2-versions} profile copies
 * into {@code target/h2-versions}, loaded in a class loader of its own, and the connection is that version's driver's;
 * the dialect is built from it, as from a connection to a server. The H2 the module is built with, on the class path,
 * is not seen.
 */
final class H2Versions {

    /** The lines of H2 the tests run on. */
    static final String[] VERSIONS = { "1.4.200", "2.0.206", "2.1.214", "2.2.224", "2.3.232" };

    private static final AtomicInteger DATABASES = new AtomicInteger();

    private H2Versions() {
    }

    /** A connection to a database in memory of its own, of the H2 of the version, and the dialect built from it. */
    record Session(Connection connection, H2Dialect dialect, URLClassLoader loader, Driver driver, String url)
            implements AutoCloseable {

        /** Another connection to the same database, as the user with the password. */
        Connection login(String user, String password) throws SQLException {
            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", password);
            return driver.connect(url, props);
        }

        Statement statement() throws SQLException {
            return connection.createStatement();
        }

        /** The first column of each row, NULL as {@code null}. */
        List<String> rows(String sql) throws SQLException {
            List<String> out = new ArrayList<>();
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            } catch (SQLException e) {
                throw new SQLException(e.getMessage() + " in " + sql, e);
            }
            return out;
        }

        void execute(String sql) throws SQLException {
            try (Statement st = connection.createStatement()) {
                st.execute(sql);
            } catch (SQLException e) {
                throw new SQLException(e.getMessage() + " in " + sql, e);
            }
        }

        @Override
        public void close() throws SQLException, IOException {
            try (loader; connection) {
                // closes both
            }
        }
    }

    static Session open(String version) throws Exception {
        Path jar = Path.of("target", "h2-versions", "h2-" + version + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(jar + " is missing: run with -Dintegration.docker=true, whose profile"
                    + " copies the jars of the H2 versions");
        }
        URLClassLoader loader = new URLClassLoader(new URL[] { jar.toUri().toURL() },
                ClassLoader.getPlatformClassLoader());
        Driver driver = (Driver) Class.forName("org.h2.Driver", true, loader).getDeclaredConstructor().newInstance();
        Properties props = new Properties();
        props.setProperty("user", "sa");
        props.setProperty("password", "");
        String url = "jdbc:h2:mem:v" + DATABASES.incrementAndGet();
        Connection connection = driver.connect(url, props);
        String reported = connection.getMetaData().getDatabaseProductVersion();
        if (!reported.startsWith(version)) {
            connection.close();
            loader.close();
            throw new IllegalStateException("h2 " + version + " reports " + reported);
        }
        return new Session(connection, new H2Dialect(DialectInitData.fromConnection(connection)), loader, driver,
                url);
    }
}
