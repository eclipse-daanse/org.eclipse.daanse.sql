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
package org.eclipse.daanse.sql.jdbc.api.meta;

import java.util.List;

import org.eclipse.daanse.sql.model.schema.CatalogReference;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.MaterializedView;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Sequence;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.jdbc.api.schema.UniqueConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.UserDefinedType;
import org.eclipse.daanse.sql.jdbc.api.schema.ViewDefinition;

public interface StructureInfo {
    List<CatalogReference> catalogs();

    List<SchemaReference> schemas();

    List<TableDefinition> tables();

    List<ColumnDefinition> columns();

    /** @return the imported keys (foreign key constraints) */
    List<ImportedKey> importedKeys();

    List<PrimaryKey> primaryKeys();

    /** @return the triggers, empty list if not available */
    default List<Trigger> triggers() {
        return List.of();
    }

    /** @return the sequences, empty list if not available */
    default List<Sequence> sequences() {
        return List.of();
    }

    /** @return the check constraints, empty list if not available */
    default List<CheckConstraint> checkConstraints() {
        return List.of();
    }

    /** @return the unique constraints, empty list if not available */
    default List<UniqueConstraint> uniqueConstraints() {
        return List.of();
    }

    /** @return the user-defined types, empty list if not available */
    default List<UserDefinedType> userDefinedTypes() {
        return List.of();
    }

    /** @return the view definitions, empty list if not available */
    default List<ViewDefinition> viewDefinitions() {
        return List.of();
    }

    /** @return the procedures, empty list if not available */
    default List<Procedure> procedures() {
        return List.of();
    }

    /** @return the functions, empty list if not available */
    default List<Function> functions() {
        return List.of();
    }

    /** @return the materialized views, empty list if not available */
    default List<MaterializedView> materializedViews() {
        return List.of();
    }

    /** @return the partitions, empty list if not available */
    default List<org.eclipse.daanse.sql.jdbc.api.schema.Partition> partitions() {
        return List.of();
    }

}
