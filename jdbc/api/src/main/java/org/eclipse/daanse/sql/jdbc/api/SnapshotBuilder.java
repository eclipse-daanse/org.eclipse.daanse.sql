/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.sql.jdbc.api;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;

/**
 * Captures a {@link MetaInfo} snapshot from a JDBC source. Narrow facet of
 * {@link DatabaseService} for callers that only need the snapshot path.
 */
public interface SnapshotBuilder {

    /**
     * @param dataSource pooled or unpooled source from which a {@link Connection}
     *                   is borrowed and immediately released
     * @return MetaInfo snapshot
     * @throws SQLException on database access error
     */
    MetaInfo createMetaInfo(DataSource dataSource) throws SQLException;

    /**
     * @param connection caller-managed connection (not closed by this method)
     * @return MetaInfo snapshot
     * @throws SQLException on database access error
     */
    MetaInfo createMetaInfo(Connection connection) throws SQLException;

    /**
     * @param dataSource       pooled or unpooled source
     * @param metadataProvider dialect-specific override; default implementation
     *                         ignores it and delegates to
     *                         {@link #createMetaInfo(DataSource)}
     * @return MetaInfo snapshot
     * @throws SQLException on database access error
     */
    default MetaInfo createMetaInfo(DataSource dataSource, MetadataProvider metadataProvider) throws SQLException {
        return createMetaInfo(dataSource);
    }

    /**
     * @param connection       caller-managed connection (not closed by this method)
     * @param metadataProvider dialect-specific override; default implementation
     *                         ignores it and delegates to
     *                         {@link #createMetaInfo(Connection)}
     * @return MetaInfo snapshot
     * @throws SQLException on database access error
     */
    default MetaInfo createMetaInfo(Connection connection, MetadataProvider metadataProvider) throws SQLException {
        return createMetaInfo(connection);
    }
}
