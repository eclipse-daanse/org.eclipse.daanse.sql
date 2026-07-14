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
package org.eclipse.daanse.sql.jdbc.api;

/**
 * Combined entry point for snapshot capture ({@link SnapshotBuilder}) and
 * catalog queries ({@link MetaDataQueries}). Consumers that only need one facet
 * should depend on the narrower interface.
 */
public interface DatabaseService extends SnapshotBuilder, MetaDataQueries {
}
