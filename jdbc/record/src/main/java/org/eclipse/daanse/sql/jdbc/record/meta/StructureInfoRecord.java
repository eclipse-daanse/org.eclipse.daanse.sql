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

import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.model.schema.CatalogReference;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.MaterializedView;
import org.eclipse.daanse.sql.jdbc.api.schema.Partition;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Sequence;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.jdbc.api.schema.UniqueConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.UserDefinedType;
import org.eclipse.daanse.sql.jdbc.api.schema.ViewDefinition;

public record StructureInfoRecord(
        List<CatalogReference> catalogs,
        List<SchemaReference> schemas,
        List<TableDefinition> tables,
        List<ColumnDefinition> columns,
        List<ImportedKey> importedKeys,
        List<PrimaryKey> primaryKeys,
        List<Trigger> triggers,
        List<Sequence> sequences,
        List<CheckConstraint> checkConstraints,
        List<UniqueConstraint> uniqueConstraints,
        List<UserDefinedType> userDefinedTypes,
        List<ViewDefinition> viewDefinitions,
        List<Procedure> procedures,
        List<Function> functions,
        List<MaterializedView> materializedViews,
        List<Partition> partitions) implements StructureInfo {
}
