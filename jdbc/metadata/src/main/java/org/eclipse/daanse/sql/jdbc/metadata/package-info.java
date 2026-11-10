/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
/**
 * Engine-specific {@link org.eclipse.daanse.sql.jdbc.api.MetadataProvider} implementations —
 * the {@code information_schema}/system-catalog readers formerly embedded in the SQL dialects —
 * plus their {@link org.eclipse.daanse.sql.jdbc.api.MetadataProviderFactory} OSGi services and
 * the static {@code MetadataProviders} resolver. Reading only: no SQL spelling, no dialect
 * dependency.
 */
@org.osgi.annotation.bundle.Export
@org.osgi.annotation.versioning.Version("0.0.1")
package org.eclipse.daanse.sql.jdbc.metadata;
