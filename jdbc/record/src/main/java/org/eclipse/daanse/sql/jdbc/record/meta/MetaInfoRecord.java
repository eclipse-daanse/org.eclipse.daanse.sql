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
package org.eclipse.daanse.sql.jdbc.record.meta;

import java.util.List;

import org.eclipse.daanse.sql.jdbc.api.meta.DatabaseInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IdentifierInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.TypeInfo;

public record MetaInfoRecord(DatabaseInfo databaseInfo,StructureInfo structureInfo , IdentifierInfo identifierInfo, List<TypeInfo> typeInfos,  List<IndexInfo> indexInfos)
        implements MetaInfo {

}
