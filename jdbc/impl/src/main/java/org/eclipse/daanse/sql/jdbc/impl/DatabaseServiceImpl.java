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
package org.eclipse.daanse.sql.jdbc.impl;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.schema.BestRowIdentifier;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.ColumnPrivilege;
import org.eclipse.daanse.sql.jdbc.api.schema.PseudoColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.Sequence;
import org.eclipse.daanse.sql.jdbc.api.schema.SuperTable;
import org.eclipse.daanse.sql.jdbc.api.schema.SuperType;
import org.eclipse.daanse.sql.jdbc.api.schema.TablePrivilege;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.jdbc.api.schema.UniqueConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.UserDefinedType;
import org.eclipse.daanse.sql.jdbc.api.schema.UserDefinedTypeReference;
import org.eclipse.daanse.sql.jdbc.api.schema.VersionColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.ViewDefinition;
import org.eclipse.daanse.sql.jdbc.api.MetadataProvider;
import org.eclipse.daanse.sql.jdbc.api.meta.DatabaseInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IdentifierInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.TypeInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.TypeInfo.Nullable;
import org.eclipse.daanse.sql.jdbc.api.meta.TypeInfo.Searchable;
import org.eclipse.daanse.sql.model.schema.CatalogReference;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.FunctionReference;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.MaterializedView;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureColumn;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.jdbc.api.schema.TableMetaData;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.jdbc.record.meta.DatabaseInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.meta.IdentifierInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.meta.MetaInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.meta.StructureInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.meta.TypeInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.BestRowIdentifierRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnPrivilegeRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.FunctionColumnRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.FunctionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ImportedKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoItemRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ProcedureColumnRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ProcedureRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PseudoColumnRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.SuperTableRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.SuperTypeRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TableDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TableMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TablePrivilegeRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.UserDefinedTypeRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.VersionColumnRecord;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = DatabaseService.class, scope = ServiceScope.SINGLETON)
public class DatabaseServiceImpl implements DatabaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseServiceImpl.class);

    // Index info column positions (from JDBC spec)
    private static final int INDEX_NON_UNIQUE = 4;
    private static final int INDEX_NAME = 6;
    private static final int INDEX_TYPE = 7;
    private static final int INDEX_ORDINAL_POSITION = 8;
    private static final int INDEX_COLUMN_NAME = 9;
    private static final int INDEX_ASC_OR_DESC = 10;
    private static final int INDEX_CARDINALITY = 11;
    private static final int INDEX_PAGES = 12;
    private static final int INDEX_FILTER_CONDITION = 13;

    private static final int[] RESULT_SET_TYPE_VALUES = { ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.TYPE_SCROLL_SENSITIVE };

    private static final int[] CONCURRENCY_VALUES = { ResultSet.CONCUR_READ_ONLY, ResultSet.CONCUR_UPDATABLE };

    @Override
    public MetaInfo createMetaInfo(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return createMetaInfo(connection);
        }
    }

    @Override
    public MetaInfo createMetaInfo(Connection connection) throws SQLException {
        DatabaseMetaData databaseMetaData = connection.getMetaData();
        return readMetaInfo(databaseMetaData);
    }

    /**
     * @param dataSource       the data source
     * @param metadataProvider the dialect-specific metadata provider
     * @return MetaInfo snapshot with extended metadata
     * @throws SQLException on database access error
     */
    @Override
    public MetaInfo createMetaInfo(DataSource dataSource, MetadataProvider metadataProvider) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return createMetaInfo(connection, metadataProvider);
        }
    }

    /**
     * @param connection       the connection (not closed by this method)
     * @param metadataProvider the dialect-specific metadata provider
     * @return MetaInfo snapshot with extended metadata
     * @throws SQLException on database access error
     */
    @Override
    public MetaInfo createMetaInfo(Connection connection, MetadataProvider metadataProvider) throws SQLException {
        DatabaseMetaData databaseMetaData = connection.getMetaData();
        return readMetaInfoWithProvider(connection, databaseMetaData, metadataProvider);
    }

    protected MetaInfo readMetaInfoWithProvider(Connection connection, DatabaseMetaData databaseMetaData,
            MetadataProvider provider) throws SQLException {

        // Standard metadata (always via JDBC)
        DatabaseInfo databaseInfo = readDatabaseInfo(databaseMetaData);
        IdentifierInfo identifierInfo = readIdentifierInfo(databaseMetaData);
        List<TypeInfo> typeInfos = getTypeInfo(databaseMetaData);

        // Tables, columns, catalogs, schemas (always via JDBC as the base)
        List<CatalogReference> catalogs = getCatalogs(databaseMetaData);
        List<SchemaReference> schemas = getSchemas(databaseMetaData);
        List<TableDefinition> tables = getTableDefinitions(databaseMetaData);
        // BULK: Columns — dialect-optimized when supported (Oracle's ALL_TAB_COLS
        // avoids the COLUMN_DEF LONG quirk that breaks DatabaseMetaData.getColumns).
        List<ColumnDefinition> columns;
        Optional<List<ColumnDefinition>> providerColumns =
                provider.getAllColumnDefinitions(connection, null, null, null, null);
        if (providerColumns.isPresent()) {
            columns = providerColumns.get();
        } else {
            columns = getColumnDefinitions(databaseMetaData);
        }

        // BULK: Indexes — dialect-optimized or fallback to per-table JDBC
        List<IndexInfo> indexInfos;
        Optional<List<IndexInfo>> providerIndexes = provider.getAllIndexInfo(connection, null, null);
        if (providerIndexes.isPresent()) {
            indexInfos = providerIndexes.get();
        } else {
            indexInfos = getIndexInfo(databaseMetaData);
        }

        // BULK: PrimaryKeys — dialect-optimized or fallback to per-table JDBC
        List<PrimaryKey> primaryKeys;
        Optional<List<PrimaryKey>> providerPKs = provider.getAllPrimaryKeys(connection, null, null);
        if (providerPKs.isPresent()) {
            primaryKeys = providerPKs.get();
        } else {
            primaryKeys = new ArrayList<>();
            for (TableDefinition tableDefinition : tables) {
                PrimaryKey pk = getPrimaryKey(databaseMetaData, tableDefinition.table());
                if (pk != null) {
                    primaryKeys.add(pk);
                }
            }
        }

        // BULK: ImportedKeys — dialect-optimized or fallback to per-table JDBC
        List<ImportedKey> importedKeys;
        Optional<List<ImportedKey>> providerFKs = provider.getAllImportedKeys(connection, null, null);
        if (providerFKs.isPresent()) {
            importedKeys = providerFKs.get();
        } else {
            importedKeys = new ArrayList<>();
            for (TableDefinition tableDefinition : tables) {
                importedKeys.addAll(getImportedKeys(databaseMetaData, tableDefinition.table()));
            }
        }

        // NEW metadata — only via dialect, no JDBC fallback needed
        List<Trigger> triggers = provider.getAllTriggers(connection, null, null);
        List<Sequence> sequences = provider.getAllSequences(connection, null, null);
        List<CheckConstraint> checkConstraints = provider.getAllCheckConstraints(connection, null, null);
        List<UniqueConstraint> uniqueConstraints = provider.getAllUniqueConstraints(connection, null, null);
        List<UserDefinedType> userDefinedTypes = provider.getAllUserDefinedTypes(connection, null, null);
        List<ViewDefinition> viewDefinitions = provider.getAllViewDefinitions(connection, null, null);
        List<Procedure> procedures = provider.getAllProcedures(connection, null, null);
        List<Function> functions = provider.getAllFunctions(connection, null, null);
        List<MaterializedView> materializedViews = provider.getAllMaterializedViews(connection, null, null);
        List<org.eclipse.daanse.sql.jdbc.api.schema.Partition> partitions =
                provider.getAllPartitions(connection, null, null);

        // Deduplicate materialized views out of tables() and viewDefinitions(): Oracle's
        // JDBC driver reports MVs as TABLE_TYPE='TABLE', PostgreSQL's as
        // TABLE_TYPE='MATERIALIZED VIEW'. Either way, when a provider returns them in
        // getAllMaterializedViews we keep them only there.
        if (!materializedViews.isEmpty()) {
            Set<String> mvKeys = new HashSet<>();
            for (MaterializedView mv : materializedViews) {
                mvKeys.add(tableKey(mv.view()));
            }
            List<TableDefinition> filteredTables = new ArrayList<>(tables.size());
            for (TableDefinition td : tables) {
                if (!mvKeys.contains(tableKey(td.table()))) {
                    filteredTables.add(td);
                }
            }
            tables = filteredTables;
            List<ViewDefinition> filteredViews = new ArrayList<>(viewDefinitions.size());
            for (ViewDefinition vd : viewDefinitions) {
                if (!mvKeys.contains(tableKey(vd.view()))) {
                    filteredViews.add(vd);
                }
            }
            viewDefinitions = filteredViews;
        }

        StructureInfo structureInfo = new StructureInfoRecord(catalogs, schemas, tables, columns,
                importedKeys, primaryKeys, triggers, sequences, checkConstraints, uniqueConstraints,
                userDefinedTypes, viewDefinitions, procedures, functions, materializedViews, partitions);
        return new MetaInfoRecord(databaseInfo, structureInfo, identifierInfo, typeInfos, indexInfos);
    }

    private static String tableKey(TableReference table) {
        String schema = table.schema().map(SchemaReference::name).orElse("");
        String catalog = table.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse("");
        return catalog + "\u0001" + schema + "\u0001" + table.name();
    }

    protected MetaInfo readMetaInfo(DatabaseMetaData databaseMetaData) throws SQLException {
        DatabaseInfo databaseInfo = readDatabaseInfo(databaseMetaData);
        IdentifierInfo identifierInfo = readIdentifierInfo(databaseMetaData);
        List<TypeInfo> typeInfos = getTypeInfo(databaseMetaData);
        StructureInfo structureInfo = getStructureInfo(databaseMetaData);
        List<IndexInfo> indexInfos = getIndexInfo(databaseMetaData);
        return new MetaInfoRecord(databaseInfo, structureInfo, identifierInfo, typeInfos, indexInfos);
    }

    public List<IndexInfo> getIndexInfo(DatabaseMetaData databaseMetaData) throws SQLException {
        List<TableDefinition> tables = getTableDefinitions(databaseMetaData);
        List<IndexInfo> indexInfos = new ArrayList<>();
        for (TableDefinition tableDefinition : tables) {
            String catalog = null;
            String schema = null;
            TableReference table = tableDefinition.table();
            List<IndexInfoItem> indexInfoItems = new ArrayList<>();
            Optional<SchemaReference> oSchema = table.schema();
            if (oSchema.isPresent()) {
                SchemaReference sr = oSchema.get();
                schema = oSchema.get().name();
                if (sr.catalog().isPresent()) {
                    catalog = sr.catalog().get().name();
                }
            }
            LOGGER.debug("Reading index info for table: {}.{}.{}", catalog, schema, table.name());
            try (ResultSet resultSet = databaseMetaData.getIndexInfo(catalog, schema, table.name(), false, true)) {
                while (resultSet.next()) {
                    boolean nonUnique = resultSet.getBoolean(INDEX_NON_UNIQUE);
                    Optional<String> indexName = Optional.ofNullable(resultSet.getString(INDEX_NAME));
                    int type = resultSet.getInt(INDEX_TYPE);
                    int ordinalPosition = resultSet.getInt(INDEX_ORDINAL_POSITION);
                    String columnNameStr = resultSet.getString(INDEX_COLUMN_NAME);
                    Optional<ColumnReference> colRef = Optional.ofNullable(columnNameStr)
                            .map(cn -> new ColumnReference(Optional.of(table), cn));
                    String ascOrDesc = resultSet.getString(INDEX_ASC_OR_DESC);
                    Optional<Boolean> ascending = ascOrDesc == null ? Optional.empty() :
                            Optional.of("A".equalsIgnoreCase(ascOrDesc));
                    long cardinality = resultSet.getLong(INDEX_CARDINALITY);
                    long pages = resultSet.getLong(INDEX_PAGES);
                    Optional<String> filterCondition = Optional.ofNullable(resultSet.getString(INDEX_FILTER_CONDITION));

                    IndexInfoItem.IndexType indexType = IndexInfoItem.IndexType.of(type);
                    indexInfoItems.add(new IndexInfoItemRecord(indexName, indexType, colRef, ordinalPosition,
                            ascending, cardinality, pages, filterCondition, !nonUnique));
                }
            } catch (SQLException e) {
                LOGGER.warn("Error reading index info for table: {}.{}.{} - {}", catalog, schema, table.name(),
                        e.getMessage());

                continue;
            }
            indexInfos.add(new IndexInfoRecord(table, indexInfoItems));
        }
        return List.copyOf(indexInfos);
    }

    protected StructureInfo getStructureInfo(DatabaseMetaData databaseMetaData) throws SQLException {
        List<CatalogReference> catalogs = getCatalogs(databaseMetaData);
        List<SchemaReference> schemas = getSchemas(databaseMetaData);
        List<TableDefinition> tables = getTableDefinitions(databaseMetaData);
        List<ColumnDefinition> columns = getColumnDefinitions(databaseMetaData);

        List<ImportedKey> importedKeys = new ArrayList<ImportedKey>();
        List<PrimaryKey> primaryKeys = new ArrayList<PrimaryKey>();
        for (TableDefinition tableDefinition : tables) {
            List<ImportedKey> iks = getImportedKeys(databaseMetaData, tableDefinition.table());
            importedKeys.addAll(iks);

            PrimaryKey pk = getPrimaryKey(databaseMetaData, tableDefinition.table());
            if (pk != null) {
                primaryKeys.add(pk);
            }
        }

        StructureInfo structureInfo = new StructureInfoRecord(catalogs, schemas, tables, columns, importedKeys, primaryKeys,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
        return structureInfo;
    }

    private List<CatalogReference> getCatalogs(DatabaseMetaData databaseMetaData) throws SQLException {

        List<CatalogReference> catalogs = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getCatalogs()) {
            while (rs.next()) {
                final String catalogName = rs.getString("TABLE_CAT");
                catalogs.add(new CatalogReference(catalogName));
            }
        }
        return List.copyOf(catalogs);
    }

    private List<SchemaReference> getSchemas(DatabaseMetaData databaseMetaData) throws SQLException {
        return getSchemas(databaseMetaData, null);
    }

    private List<SchemaReference> getSchemas(DatabaseMetaData databaseMetaData, CatalogReference catalog)
            throws SQLException {

        String catalogName = null;

        if (catalog != null) {
            catalogName = catalog.name();
        }

        List<SchemaReference> schemas = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getSchemas(catalogName, null)) {
            while (rs.next()) {
                final String schemaName = rs.getString("TABLE_SCHEM");
                final Optional<CatalogReference> c = Optional.ofNullable(rs.getString("TABLE_CATALOG"))
                        .map(cat -> new CatalogReference(cat));
                schemas.add(new SchemaReference(c, schemaName));
            }
        }
        return List.copyOf(schemas);
    }

    private List<String> getTableTypes(DatabaseMetaData databaseMetaData) throws SQLException {

        List<String> typeInfos = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getTableTypes()) {
            while (rs.next()) {
                final String tableTypeName = rs.getString("TABLE_TYPE");
                typeInfos.add(tableTypeName);
            }
        }

        return List.copyOf(typeInfos);
    }

    private List<TableDefinition> getTableDefinitions(DatabaseMetaData databaseMetaData) throws SQLException {
        return getTableDefinitions(databaseMetaData, List.of());
    }

    private List<TableDefinition> getTableDefinitions(DatabaseMetaData databaseMetaData, List<String> types)
            throws SQLException {

        String[] typesArr = typesArrayForFilterNull(types);

        return getTableDefinitions(databaseMetaData, null, null, null, typesArr);
    }

    private static String[] typesArrayForFilterNull(List<String> types) {
        String[] typesArr = null;
        if (types != null && !types.isEmpty()) {
            typesArr = types.toArray(String[]::new);
        }
        return typesArr;
    }

    private List<TableDefinition> getTableDefinitions(DatabaseMetaData databaseMetaData, CatalogReference catalog)
            throws SQLException {
        return getTableDefinitions(databaseMetaData, List.of());
    }

    private List<TableDefinition> getTableDefinitions(DatabaseMetaData databaseMetaData, CatalogReference catalog,
            List<String> types) throws SQLException {
        String[] typesArr = typesArrayForFilterNull(types);
        return getTableDefinitions(databaseMetaData, catalog.name(), null, null, typesArr);
    }

    private List<TableDefinition> getTableDefinitions(DatabaseMetaData databaseMetaData, SchemaReference schema)
            throws SQLException {
        return getTableDefinitions(databaseMetaData, schema, List.of());
    }

    private List<TableDefinition> getTableDefinitions(DatabaseMetaData databaseMetaData, SchemaReference schema,
            List<String> types) throws SQLException {
        String[] typesArr = typesArrayForFilterNull(types);

        String catalog = schema.catalog().map(CatalogReference::name).orElse(null);
        return getTableDefinitions(databaseMetaData, catalog, schema.name(), null, typesArr);
    }

    private List<TableDefinition> getTableDefinitions(DatabaseMetaData databaseMetaData, TableReference table)
            throws SQLException {

        Optional<SchemaReference> oSchema = table.schema();
        String schema = oSchema.map(SchemaReference::name).orElse(null);
        Optional<CatalogReference> oCatalog = oSchema.flatMap(SchemaReference::catalog);
        String catalog = oCatalog.map(CatalogReference::name).orElse(null);

        return getTableDefinitions(databaseMetaData, catalog, schema, table.name(), null);
    }

    private List<TableDefinition> getTableDefinitions(DatabaseMetaData databaseMetaData, String catalog,
            String schemaPattern, String tableNamePattern, String types[]) throws SQLException {

        List<TableDefinition> tabeDefinitions = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getTables(catalog, schemaPattern, tableNamePattern, types)) {
            int columnCount = rs.getMetaData().getColumnCount();
            Set<String> columnNames = new HashSet<>();
            for (int i = 1; i <= columnCount; i++) {

                String colName = rs.getMetaData().getColumnName(i);
                columnNames.add(colName);
            }
            while (rs.next()) {
                final Optional<String> oCatalogName = Optional.ofNullable(rs.getString("TABLE_CAT"));
                final Optional<String> oSchemaName = Optional.ofNullable(rs.getString("TABLE_SCHEM"));
                final String tableName = rs.getString("TABLE_NAME");
                final String tableType = rs.getString("TABLE_TYPE");
                final Optional<String> oRemarks = Optional.ofNullable(rs.getString("REMARKS"));

                final Optional<String> oTypeCat = getColumnValue(rs, columnNames, "TYPE_CAT");
                final Optional<String> oTypeSchema = getColumnValue(rs, columnNames, "TYPE_SCHEM");
                final Optional<String> oTypeName = getColumnValue(rs, columnNames, "TYPE_NAME");
                final Optional<String> oSelfRefColName = getColumnValue(rs, columnNames, "SELF_REFERENCING_COL_NAME");
                final Optional<String> oRefGen = getColumnValue(rs, columnNames, "REF_GENERATION");

                Optional<CatalogReference> oCatRef = oCatalogName.map(cn -> new CatalogReference(cn));
                Optional<SchemaReference> oSchemaRef = oSchemaName.map(sn -> new SchemaReference(oCatRef, sn));

                TableReference tableReference = new TableReference(oSchemaRef, tableName, tableType);
                TableMetaData tableMetaData = new TableMetaDataRecord(oRemarks, oTypeCat, oTypeSchema, oTypeName,
                        oSelfRefColName, oRefGen);

                TableDefinition tableDefinition = new TableDefinitionRecord(tableReference, tableMetaData);
                tabeDefinitions.add(tableDefinition);
            }
        }

        return List.copyOf(tabeDefinitions);
    }

    private Optional<String> getColumnValue(ResultSet rs, Set<String> columnNames, String columnName)
            throws SQLException {
        if (!columnNames.contains(columnName)) {
            return Optional.empty();
        }
        String value = rs.getString(columnName);
        if (rs.wasNull()) {
            return Optional.empty();
        } else {
            return Optional.of(value);
        }
    }

    private boolean tableExists(DatabaseMetaData databaseMetaData, TableReference table) throws SQLException {

        Optional<SchemaReference> oSchema = table.schema();
        String schema = oSchema.map(SchemaReference::name).orElse(null);
        Optional<CatalogReference> oCatalog = oSchema.flatMap(SchemaReference::catalog);
        String catalog = oCatalog.map(CatalogReference::name).orElse(null);

        return tableExists(databaseMetaData, catalog, schema, table.name(), null);
    }

    private boolean tableExists(DatabaseMetaData databaseMetaData, String catalog, String schemaPattern,
            String tableNamePattern, String types[]) throws SQLException {

        try (ResultSet rs = databaseMetaData.getTables(catalog, schemaPattern, tableNamePattern, types)) {
            return rs.next();
        }
    }

    private List<TypeInfo> getTypeInfo(DatabaseMetaData databaseMetaData) throws SQLException {

        List<TypeInfo> typeInfos = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getTypeInfo()) {
            while (rs.next()) {
                final String typeName = rs.getString("TYPE_NAME");
                final int dataType = rs.getInt("DATA_TYPE");
                final int precision = rs.getInt("PRECISION");
                final Optional<String> literalPrefix = Optional.ofNullable(rs.getString("LITERAL_PREFIX"));
                final Optional<String> literalSuffix = Optional.ofNullable(rs.getString("LITERAL_SUFFIX"));
                final Optional<String> createParams = Optional.ofNullable(rs.getString("CREATE_PARAMS"));
                final Nullable nullable = TypeInfo.Nullable.of(rs.getShort("NULLABLE"));
                final boolean caseSensitive = rs.getBoolean("CASE_SENSITIVE");
                final Searchable searchable = TypeInfo.Searchable.of(rs.getShort("SEARCHABLE"));
                final boolean unsignedAttribute = rs.getBoolean("UNSIGNED_ATTRIBUTE");
                final boolean fixedPrecScale = rs.getBoolean("FIXED_PREC_SCALE");
                final boolean autoIncrement = rs.getBoolean("AUTO_INCREMENT");
                final Optional<String> localTypeName = Optional.ofNullable(rs.getString("LOCAL_TYPE_NAME"));
                final short minimumScale = rs.getShort("MINIMUM_SCALE");
                final short maximumScale = rs.getShort("MAXIMUM_SCALE");
                final int numPrecRadix = rs.getInt("NUM_PREC_RADIX");

                JDBCType jdbcType;
                try {
                    jdbcType = JDBCType.valueOf(dataType);
                } catch (IllegalArgumentException ex) {
                    jdbcType = JDBCType.OTHER;
                    LOGGER.info("Unknown JDBC-Typcode: " + dataType + " (" + typeName + ")");
                }
                TypeInfoRecord typeInfo = new TypeInfoRecord(typeName, jdbcType, precision, literalPrefix, literalSuffix,
                        createParams, nullable, caseSensitive, searchable, unsignedAttribute, fixedPrecScale,
                        autoIncrement, localTypeName, minimumScale, maximumScale, numPrecRadix);
                typeInfos.add(typeInfo);
            }
        }

        return List.copyOf(typeInfos);
    }

    protected static DatabaseInfo readDatabaseInfo(DatabaseMetaData databaseMetaData) {

        String productName = "";
        try {
            productName = databaseMetaData.getDatabaseProductName();
        } catch (SQLException e) {
            LOGGER.error("Exception while reading productName", e);
        }

        String productVersion = "";
        try {
            productVersion = databaseMetaData.getDatabaseProductVersion();
        } catch (SQLException e) {
            LOGGER.error("Exception while reading productVersion", e);
        }

        int majorVersion = 0;
        try {
            majorVersion = databaseMetaData.getDatabaseMajorVersion();
        } catch (SQLException e) {
            LOGGER.error("Exception while reading majorVersion", e);
        }

        int minorVersion = 0;
        try {
            minorVersion = databaseMetaData.getDatabaseMinorVersion();
        } catch (SQLException e) {
            LOGGER.error("Exception while reading minorVersion", e);
        }

        return new DatabaseInfoRecord(productName, productVersion, majorVersion, minorVersion);
    }

    protected static IdentifierInfo readIdentifierInfo(DatabaseMetaData databaseMetaData) {

        String quoteString = " ";
        boolean readOnly = true;
        int maxColumnNameLength = 0;
        Set<List<Integer>> supportedResultSetStyles = Set.of();
        try {
            quoteString = databaseMetaData.getIdentifierQuoteString();
            maxColumnNameLength = databaseMetaData.getMaxColumnNameLength();
            readOnly = databaseMetaData.isReadOnly();
            supportedResultSetStyles = supportedResultSetStyles(databaseMetaData);
        } catch (SQLException e) {
            LOGGER.error("Exception while reading quoteString", e);
        }
        return new IdentifierInfoRecord(quoteString, maxColumnNameLength, readOnly, supportedResultSetStyles);
    }

    private List<ColumnDefinition> getColumnDefinitions(DatabaseMetaData databaseMetaData) throws SQLException {
        return getColumnDefinitions(databaseMetaData, null, null, null, null);

    }

    private List<ColumnDefinition> getColumnDefinitions(DatabaseMetaData databaseMetaData, TableReference table)
            throws SQLException {

        String sTable = table.name();
        Optional<SchemaReference> oSchema = table.schema();
        String schema = oSchema.map(SchemaReference::name).orElse(null);
        Optional<CatalogReference> oCatalog = oSchema.flatMap(SchemaReference::catalog);
        String catalog = oCatalog.map(CatalogReference::name).orElse(null);

        return getColumnDefinitions(databaseMetaData, catalog, schema, sTable, null);
    }

    private List<ColumnDefinition> getColumnDefinitions(DatabaseMetaData databaseMetaData, ColumnReference column)
            throws SQLException {

        Optional<TableReference> oTable = column.table();
        String table = oTable.map(TableReference::name).orElse(null);
        Optional<SchemaReference> oSchema = oTable.flatMap(TableReference::schema);
        String schema = oSchema.map(SchemaReference::name).orElse(null);
        Optional<CatalogReference> oCatalog = oSchema.flatMap(SchemaReference::catalog);
        String catalog = oCatalog.map(CatalogReference::name).orElse(null);

        return getColumnDefinitions(databaseMetaData, catalog, schema, table, column.name());
    }

    private List<ColumnDefinition> getColumnDefinitions(DatabaseMetaData databaseMetaData, String catalog,
            String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        List<ColumnDefinition> columnDefinitions = new ArrayList<>();

        try (ResultSet rs = databaseMetaData.getColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern);) {
            while (rs.next()) {

                final Optional<String> oCatalogName = Optional.ofNullable(rs.getString("TABLE_CAT"));
                final Optional<String> oSchemaName = Optional.ofNullable(rs.getString("TABLE_SCHEM"));
                final String tableName = rs.getString("TABLE_NAME");
                final String columName = rs.getString("COLUMN_NAME");

                final String typeName = rs.getString("TYPE_NAME");
                OptionalInt oColumnSize = OptionalInt.of(rs.getInt("COLUMN_SIZE"));

                if (rs.wasNull()) {
                    oColumnSize = OptionalInt.empty();
                }

                OptionalInt oDecimalDigits = OptionalInt.of(rs.getInt("DECIMAL_DIGITS"));
                if (rs.wasNull()) {
                    oDecimalDigits = OptionalInt.empty();
                }

                OptionalInt oNumPrecRadix = OptionalInt.of(rs.getInt("NUM_PREC_RADIX"));
                if (rs.wasNull()) {
                    oNumPrecRadix = OptionalInt.empty();
                }

                OptionalInt oNullable = OptionalInt.of(rs.getInt("NULLABLE"));
                if (rs.wasNull()) {
                    oNullable = OptionalInt.empty();
                }

                OptionalInt oCharOctetLength = OptionalInt.of(rs.getInt("CHAR_OCTET_LENGTH"));
                if (rs.wasNull()) {
                    oCharOctetLength = OptionalInt.empty();
                }

                final int dataType = rs.getInt("DATA_TYPE");

                final Optional<String> remarks = Optional.ofNullable(rs.getString("REMARKS"));

                // Additional fields from JDBC spec
                final Optional<String> columnDefault = Optional.ofNullable(rs.getString("COLUMN_DEF"));
                final ColumnMetaData.Nullability nullability = oNullable.isPresent()
                        ? ColumnMetaData.Nullability.of(oNullable.getAsInt())
                        : ColumnMetaData.Nullability.UNKNOWN;

                // IS_AUTOINCREMENT and IS_GENERATEDCOLUMN may not be available in all drivers
                ColumnMetaData.AutoIncrement autoIncrement = ColumnMetaData.AutoIncrement.UNKNOWN;
                ColumnMetaData.GeneratedColumn generatedColumn = ColumnMetaData.GeneratedColumn.UNKNOWN;
                try {
                    String isAutoIncrement = rs.getString("IS_AUTOINCREMENT");
                    autoIncrement = ColumnMetaData.AutoIncrement.ofString(isAutoIncrement);
                } catch (SQLException e) {
                    LOGGER.debug("IS_AUTOINCREMENT not available for column: {}.{}", tableName, columName);
                }
                try {
                    String isGeneratedColumn = rs.getString("IS_GENERATEDCOLUMN");
                    generatedColumn = ColumnMetaData.GeneratedColumn.ofString(isGeneratedColumn);
                } catch (SQLException e) {
                    LOGGER.debug("IS_GENERATEDCOLUMN not available for column: {}.{}", tableName, columName);
                }

                Optional<CatalogReference> oCatRef = oCatalogName.map(cn -> new CatalogReference(cn));
                Optional<SchemaReference> oSchemaRef = oSchemaName.map(sn -> new SchemaReference(oCatRef, sn));

                JDBCType jdbcType;
                try {
                    jdbcType = JDBCType.valueOf(dataType);
                } catch (IllegalArgumentException ex) {
                    jdbcType = JDBCType.OTHER;
                    LOGGER.info("Unknown JDBC-Typcode: " + dataType + " (" + typeName + ") in table: " + tableName
                            + " column: " + columName);
                }

                TableReference tableReference = new TableReference(oSchemaRef, tableName);

                ColumnReference columnReference = new ColumnReference(Optional.of(tableReference), columName);
                ColumnDefinition columnDefinition = new ColumnDefinitionRecord(columnReference, new ColumnMetaDataRecord(
                        jdbcType, typeName, oColumnSize, oDecimalDigits, oNumPrecRadix, nullability,
                        oCharOctetLength, remarks, columnDefault, autoIncrement, generatedColumn));

                columnDefinitions.add(columnDefinition);
            }
        }
        return List.copyOf(columnDefinitions);
    }

    private boolean columnExists(DatabaseMetaData databaseMetaData, ColumnReference column) throws SQLException {

        Optional<TableReference> oTable = column.table();
        String table = oTable.map(TableReference::name).orElse(null);
        Optional<SchemaReference> oSchema = oTable.flatMap(TableReference::schema);
        String schema = oSchema.map(SchemaReference::name).orElse(null);
        Optional<CatalogReference> oCatalog = oSchema.flatMap(SchemaReference::catalog);
        String catalog = oCatalog.map(CatalogReference::name).orElse(null);

        return columnExists(databaseMetaData, catalog, schema, table, column.name());
    }

    private boolean columnExists(DatabaseMetaData databaseMetaData, String catalog, String schemaPattern,
            String tableNamePattern, String columnNamePattern) throws SQLException {

        try (ResultSet rs = databaseMetaData.getColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern)) {
            return rs.next();
        }
    }

    private List<ImportedKey> getImportedKeys(DatabaseMetaData databaseMetaData, TableReference table)
            throws SQLException {

        Optional<SchemaReference> oSchema = table.schema();
        String schema = oSchema.map(SchemaReference::name).orElse(null);
        Optional<CatalogReference> oCatalog = oSchema.flatMap(SchemaReference::catalog);
        String catalog = oCatalog.map(CatalogReference::name).orElse(null);
        return getImportedKeys(databaseMetaData, catalog, schema, table.name());
    }

    private List<ImportedKey> getImportedKeys(DatabaseMetaData databaseMetaData, String catalog, String schema,
            String tableName) throws SQLException {
        List<ImportedKey> importedKeys = new ArrayList<>();

        try (ResultSet rs = databaseMetaData.getImportedKeys(catalog, schema, tableName);) {
            while (rs.next()) {

                final Optional<String> oCatalogNamePK = Optional.ofNullable(rs.getString("PKTABLE_CAT"));
                final Optional<String> oSchemaNamePk = Optional.ofNullable(rs.getString("PKTABLE_SCHEM"));
                final String tableNamePk = rs.getString("PKTABLE_NAME");
                final String columNamePk = rs.getString("PKCOLUMN_NAME");

                final Optional<String> oCatalogNameFK = Optional.ofNullable(rs.getString("FKTABLE_CAT"));
                final Optional<String> oSchemaNameFk = Optional.ofNullable(rs.getString("FKTABLE_SCHEM"));
                final String tableNameFk = rs.getString("FKTABLE_NAME");
                final String columNameFk = rs.getString("FKCOLUMN_NAME");

                // Additional fields from JDBC spec
                final int keySeq = rs.getInt("KEY_SEQ");
                final int updateRule = rs.getInt("UPDATE_RULE");
                final int deleteRule = rs.getInt("DELETE_RULE");
                final String fkName = rs.getString("FK_NAME");
                final Optional<String> pkName = Optional.ofNullable(rs.getString("PK_NAME"));
                final int deferrability = rs.getInt("DEFERRABILITY");

                // PK
                Optional<CatalogReference> oCatRefPk = oCatalogNamePK.map(cn -> new CatalogReference(cn));
                Optional<SchemaReference> oSchemaRefPk = oSchemaNamePk.map(sn -> new SchemaReference(oCatRefPk, sn));
                TableReference tableReferencePk = new TableReference(oSchemaRefPk, tableNamePk);
                ColumnReference primaryKeyColumn = new ColumnReference(Optional.of(tableReferencePk), columNamePk);

                // FK
                Optional<CatalogReference> oCatRefFk = oCatalogNameFK.map(cn -> new CatalogReference(cn));
                Optional<SchemaReference> oSchemaRefFk = oSchemaNameFk.map(sn -> new SchemaReference(oCatRefFk, sn));
                TableReference tableReferenceFk = new TableReference(oSchemaRefFk, tableNameFk);
                ColumnReference foreignKeyColumn = new ColumnReference(Optional.of(tableReferenceFk), columNameFk);

                // Use FK_NAME from database if available, otherwise generate one
                String constraintName;
                if (fkName != null && !fkName.isBlank()) {
                    constraintName = fkName;
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("fk_").append(tableReferenceFk.name()).append("_").append(foreignKeyColumn.name())
                            .append("_").append(tableReferencePk.name()).append("_").append(primaryKeyColumn.name());
                    constraintName = sb.toString();
                }

                ImportedKey importedKey = new ImportedKeyRecord(
                        primaryKeyColumn,
                        foreignKeyColumn,
                        constraintName,
                        keySeq,
                        ImportedKey.ReferentialAction.of(updateRule),
                        ImportedKey.ReferentialAction.of(deleteRule),
                        pkName,
                        ImportedKey.Deferrability.of(deferrability));
                importedKeys.add(importedKey);
            }
        }
        return List.copyOf(importedKeys);
    }

    /**
     * @param databaseMetaData the database metadata
     * @param table the table reference
     * @return the primary key, or null if the table has no primary key
     * @throws SQLException if a database access error occurs
     */
    public PrimaryKey getPrimaryKey(DatabaseMetaData databaseMetaData, TableReference table) throws SQLException {
        Optional<SchemaReference> oSchema = table.schema();
        String schema = oSchema.map(SchemaReference::name).orElse(null);
        Optional<CatalogReference> oCatalog = oSchema.flatMap(SchemaReference::catalog);
        String catalog = oCatalog.map(CatalogReference::name).orElse(null);

        List<ColumnReference> columns = new ArrayList<>();
        String pkName = null;

        try (ResultSet rs = databaseMetaData.getPrimaryKeys(catalog, schema, table.name())) {
            // Results are ordered by COLUMN_NAME, but we need to order by KEY_SEQ
            // So we collect all columns first
            java.util.TreeMap<Integer, ColumnReference> orderedColumns = new java.util.TreeMap<>();

            while (rs.next()) {
                final String columnName = rs.getString("COLUMN_NAME");
                final int keySeq = rs.getInt("KEY_SEQ");
                pkName = rs.getString("PK_NAME"); // Same for all rows

                ColumnReference colRef = new ColumnReference(Optional.of(table), columnName);
                orderedColumns.put(keySeq, colRef);
            }

            // Add columns in KEY_SEQ order
            columns.addAll(orderedColumns.values());
        }

        if (columns.isEmpty()) {
            return null; // No primary key
        }

        return new PrimaryKeyRecord(table, List.copyOf(columns), Optional.ofNullable(pkName));
    }

    private static Set<List<Integer>> supportedResultSetStyles(DatabaseMetaData databaseMetaData) throws SQLException {
        Set<List<Integer>> supports = new HashSet<>();
        for (int type : RESULT_SET_TYPE_VALUES) {
            for (int concurrency : CONCURRENCY_VALUES) {
                if (databaseMetaData.supportsResultSetConcurrency(type, concurrency)) {
                    String driverName = databaseMetaData.getDriverName();
                    if (type != ResultSet.TYPE_FORWARD_ONLY && driverName.equals("JDBC-ODBC Bridge (odbcjt32.dll)")) {
                        // In JDK 1.6, the Jdbc-Odbc bridge announces
                        // that it can handle TYPE_SCROLL_INSENSITIVE
                        // but it does so by generating a 'COUNT(*)'
                        // query, and this query is invalid if the query
                        // contains a single-quote. So, override the
                        // driver.
                        continue;
                    }
                    supports.add(new ArrayList<>(Arrays.asList(type, concurrency)));
                }
            }
        }
        return supports;
    }

    private List<Procedure> getProcedures(DatabaseMetaData databaseMetaData) throws SQLException {
        return getProcedures(databaseMetaData, null, null, null);
    }

    private List<Procedure> getProcedures(DatabaseMetaData databaseMetaData, String catalog, String schemaPattern,
            String procedureNamePattern) throws SQLException {
        List<Procedure> procedures = new ArrayList<>();

        try (ResultSet rs = databaseMetaData.getProcedures(catalog, schemaPattern, procedureNamePattern)) {
            while (rs.next()) {
                final Optional<String> oCatalogName = Optional.ofNullable(rs.getString("PROCEDURE_CAT"));
                final Optional<String> oSchemaName = Optional.ofNullable(rs.getString("PROCEDURE_SCHEM"));
                final String procedureName = rs.getString("PROCEDURE_NAME");
                final Optional<String> remarks = Optional.ofNullable(rs.getString("REMARKS"));
                final int procedureType = rs.getInt("PROCEDURE_TYPE");
                final String specificName = rs.getString("SPECIFIC_NAME");

                Optional<CatalogReference> oCatRef = oCatalogName.map(CatalogReference::new);
                Optional<SchemaReference> oSchemaRef = oSchemaName.map(sn -> new SchemaReference(oCatRef, sn));

                ProcedureReference reference = new ProcedureReference(oSchemaRef, procedureName, specificName);

                // Get procedure columns
                List<ProcedureColumn> columns = getProcedureColumns(databaseMetaData,
                        oCatalogName.orElse(null), oSchemaName.orElse(null), procedureName);

                Procedure procedure = new ProcedureRecord(reference, Procedure.ProcedureType.of(procedureType), remarks,
                        columns, Optional.empty(), Optional.empty(), Optional.empty());
                procedures.add(procedure);
            }
        }

        return List.copyOf(procedures);
    }

    private List<ProcedureColumn> getProcedureColumns(DatabaseMetaData databaseMetaData, String catalog,
            String schema, String procedureName) throws SQLException {
        List<ProcedureColumn> columns = new ArrayList<>();

        try (ResultSet rs = databaseMetaData.getProcedureColumns(catalog, schema, procedureName, null)) {
            while (rs.next()) {
                final String columnName = rs.getString("COLUMN_NAME");
                final int columnType = rs.getInt("COLUMN_TYPE");
                final int dataType = rs.getInt("DATA_TYPE");
                final String typeName = rs.getString("TYPE_NAME");

                OptionalInt precision = OptionalInt.of(rs.getInt("PRECISION"));
                if (rs.wasNull()) {
                    precision = OptionalInt.empty();
                }

                OptionalInt scale = OptionalInt.of(rs.getInt("SCALE"));
                if (rs.wasNull()) {
                    scale = OptionalInt.empty();
                }

                OptionalInt radix = OptionalInt.of(rs.getInt("RADIX"));
                if (rs.wasNull()) {
                    radix = OptionalInt.empty();
                }

                final int nullable = rs.getInt("NULLABLE");
                final Optional<String> remarks = Optional.ofNullable(rs.getString("REMARKS"));
                final Optional<String> columnDefault = Optional.ofNullable(rs.getString("COLUMN_DEF"));
                final int ordinalPosition = rs.getInt("ORDINAL_POSITION");

                JDBCType jdbcType;
                try {
                    jdbcType = JDBCType.valueOf(dataType);
                } catch (IllegalArgumentException ex) {
                    jdbcType = JDBCType.OTHER;
                    LOGGER.debug("Unknown JDBC type code: {} ({}) for procedure column: {}.{}",
                            dataType, typeName, procedureName, columnName);
                }

                ProcedureColumn column = new ProcedureColumnRecord(columnName, ProcedureColumn.ColumnType.of(columnType),
                        jdbcType, typeName, precision, scale, radix, ProcedureColumn.Nullability.of(nullable),
                        remarks, columnDefault, ordinalPosition);
                columns.add(column);
            }
        }

        return List.copyOf(columns);
    }

    private List<Function> getFunctions(DatabaseMetaData databaseMetaData) throws SQLException {
        return getFunctions(databaseMetaData, null, null, null);
    }

    private List<Function> getFunctions(DatabaseMetaData databaseMetaData, String catalog, String schemaPattern,
            String functionNamePattern) throws SQLException {
        List<Function> functions = new ArrayList<>();

        try (ResultSet rs = databaseMetaData.getFunctions(catalog, schemaPattern, functionNamePattern)) {
            while (rs.next()) {
                final Optional<String> oCatalogName = Optional.ofNullable(rs.getString("FUNCTION_CAT"));
                final Optional<String> oSchemaName = Optional.ofNullable(rs.getString("FUNCTION_SCHEM"));
                final String functionName = rs.getString("FUNCTION_NAME");
                final Optional<String> remarks = Optional.ofNullable(rs.getString("REMARKS"));
                final int functionType = rs.getInt("FUNCTION_TYPE");
                final String specificName = rs.getString("SPECIFIC_NAME");

                Optional<CatalogReference> oCatRef = oCatalogName.map(CatalogReference::new);
                Optional<SchemaReference> oSchemaRef = oSchemaName.map(sn -> new SchemaReference(oCatRef, sn));

                FunctionReference reference = new FunctionReference(oSchemaRef, functionName, specificName);

                // Get function columns
                List<FunctionColumn> columns = getFunctionColumns(databaseMetaData,
                        oCatalogName.orElse(null), oSchemaName.orElse(null), functionName);

                Function function = new FunctionRecord(reference, Function.FunctionType.of(functionType), remarks,
                        columns, Optional.empty(), Optional.empty(), Optional.empty());
                functions.add(function);
            }
        }

        return List.copyOf(functions);
    }

    private List<FunctionColumn> getFunctionColumns(DatabaseMetaData databaseMetaData, String catalog,
            String schema, String functionName) throws SQLException {
        List<FunctionColumn> columns = new ArrayList<>();

        try (ResultSet rs = databaseMetaData.getFunctionColumns(catalog, schema, functionName, null)) {
            while (rs.next()) {
                final String columnName = rs.getString("COLUMN_NAME");
                final int columnType = rs.getInt("COLUMN_TYPE");
                final int dataType = rs.getInt("DATA_TYPE");
                final String typeName = rs.getString("TYPE_NAME");

                OptionalInt precision = OptionalInt.of(rs.getInt("PRECISION"));
                if (rs.wasNull()) {
                    precision = OptionalInt.empty();
                }

                OptionalInt scale = OptionalInt.of(rs.getInt("SCALE"));
                if (rs.wasNull()) {
                    scale = OptionalInt.empty();
                }

                OptionalInt radix = OptionalInt.of(rs.getInt("RADIX"));
                if (rs.wasNull()) {
                    radix = OptionalInt.empty();
                }

                final int nullable = rs.getInt("NULLABLE");
                final Optional<String> remarks = Optional.ofNullable(rs.getString("REMARKS"));

                OptionalInt charOctetLength = OptionalInt.of(rs.getInt("CHAR_OCTET_LENGTH"));
                if (rs.wasNull()) {
                    charOctetLength = OptionalInt.empty();
                }

                final int ordinalPosition = rs.getInt("ORDINAL_POSITION");

                JDBCType jdbcType;
                try {
                    jdbcType = JDBCType.valueOf(dataType);
                } catch (IllegalArgumentException ex) {
                    jdbcType = JDBCType.OTHER;
                    LOGGER.debug("Unknown JDBC type code: {} ({}) for function column: {}.{}",
                            dataType, typeName, functionName, columnName);
                }

                FunctionColumn column = new FunctionColumnRecord(columnName, FunctionColumn.ColumnType.of(columnType),
                        jdbcType, typeName, precision, scale, radix, FunctionColumn.Nullability.of(nullable),
                        remarks, charOctetLength, ordinalPosition);
                columns.add(column);
            }
        }

        return List.copyOf(columns);
    }

    private List<ImportedKey> getExportedKeys(DatabaseMetaData databaseMetaData, TableReference table)
            throws SQLException {
        Optional<SchemaReference> oSchema = table.schema();
        String schema = oSchema.map(SchemaReference::name).orElse(null);
        Optional<CatalogReference> oCatalog = oSchema.flatMap(SchemaReference::catalog);
        String catalog = oCatalog.map(CatalogReference::name).orElse(null);
        return getExportedKeys(databaseMetaData, catalog, schema, table.name());
    }

    private List<ImportedKey> getExportedKeys(DatabaseMetaData databaseMetaData, String catalog, String schema,
            String tableName) throws SQLException {
        List<ImportedKey> exportedKeys = new ArrayList<>();

        try (ResultSet rs = databaseMetaData.getExportedKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                ImportedKey key = readForeignKeyFromResultSet(rs);
                exportedKeys.add(key);
            }
        }
        return List.copyOf(exportedKeys);
    }

    private List<ImportedKey> getCrossReference(DatabaseMetaData databaseMetaData, TableReference parentTable,
            TableReference foreignTable) throws SQLException {
        Optional<SchemaReference> parentSchema = parentTable.schema();
        String pSchema = parentSchema.map(SchemaReference::name).orElse(null);
        Optional<CatalogReference> parentCatalog = parentSchema.flatMap(SchemaReference::catalog);
        String pCatalog = parentCatalog.map(CatalogReference::name).orElse(null);

        Optional<SchemaReference> foreignSchema = foreignTable.schema();
        String fSchema = foreignSchema.map(SchemaReference::name).orElse(null);
        Optional<CatalogReference> foreignCatalog = foreignSchema.flatMap(SchemaReference::catalog);
        String fCatalog = foreignCatalog.map(CatalogReference::name).orElse(null);

        return getCrossReference(databaseMetaData, pCatalog, pSchema, parentTable.name(),
                fCatalog, fSchema, foreignTable.name());
    }

    private List<ImportedKey> getCrossReference(DatabaseMetaData databaseMetaData, String parentCatalog,
            String parentSchema, String parentTable, String foreignCatalog, String foreignSchema,
            String foreignTable) throws SQLException {
        List<ImportedKey> crossRefs = new ArrayList<>();

        try (ResultSet rs = databaseMetaData.getCrossReference(parentCatalog, parentSchema, parentTable,
                foreignCatalog, foreignSchema, foreignTable)) {
            while (rs.next()) {
                ImportedKey key = readForeignKeyFromResultSet(rs);
                crossRefs.add(key);
            }
        }
        return List.copyOf(crossRefs);
    }

    private ImportedKey readForeignKeyFromResultSet(ResultSet rs) throws SQLException {
        final Optional<String> oCatalogNamePK = Optional.ofNullable(rs.getString("PKTABLE_CAT"));
        final Optional<String> oSchemaNamePk = Optional.ofNullable(rs.getString("PKTABLE_SCHEM"));
        final String tableNamePk = rs.getString("PKTABLE_NAME");
        final String columNamePk = rs.getString("PKCOLUMN_NAME");

        final Optional<String> oCatalogNameFK = Optional.ofNullable(rs.getString("FKTABLE_CAT"));
        final Optional<String> oSchemaNameFk = Optional.ofNullable(rs.getString("FKTABLE_SCHEM"));
        final String tableNameFk = rs.getString("FKTABLE_NAME");
        final String columNameFk = rs.getString("FKCOLUMN_NAME");

        final int keySeq = rs.getInt("KEY_SEQ");
        final int updateRule = rs.getInt("UPDATE_RULE");
        final int deleteRule = rs.getInt("DELETE_RULE");
        final String fkName = rs.getString("FK_NAME");
        final Optional<String> pkName = Optional.ofNullable(rs.getString("PK_NAME"));
        final int deferrability = rs.getInt("DEFERRABILITY");

        // PK
        Optional<CatalogReference> oCatRefPk = oCatalogNamePK.map(CatalogReference::new);
        Optional<SchemaReference> oSchemaRefPk = oSchemaNamePk.map(sn -> new SchemaReference(oCatRefPk, sn));
        TableReference tableReferencePk = new TableReference(oSchemaRefPk, tableNamePk);
        ColumnReference primaryKeyColumn = new ColumnReference(Optional.of(tableReferencePk), columNamePk);

        // FK
        Optional<CatalogReference> oCatRefFk = oCatalogNameFK.map(CatalogReference::new);
        Optional<SchemaReference> oSchemaRefFk = oSchemaNameFk.map(sn -> new SchemaReference(oCatRefFk, sn));
        TableReference tableReferenceFk = new TableReference(oSchemaRefFk, tableNameFk);
        ColumnReference foreignKeyColumn = new ColumnReference(Optional.of(tableReferenceFk), columNameFk);

        // Use FK_NAME from database if available, otherwise generate one
        String constraintName;
        if (fkName != null && !fkName.isBlank()) {
            constraintName = fkName;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("fk_").append(tableReferenceFk.name()).append("_").append(foreignKeyColumn.name())
                    .append("_").append(tableReferencePk.name()).append("_").append(primaryKeyColumn.name());
            constraintName = sb.toString();
        }

        return new ImportedKeyRecord(
                primaryKeyColumn,
                foreignKeyColumn,
                constraintName,
                keySeq,
                ImportedKey.ReferentialAction.of(updateRule),
                ImportedKey.ReferentialAction.of(deleteRule),
                pkName,
                ImportedKey.Deferrability.of(deferrability));
    }

    private List<UserDefinedType> getUDTs(DatabaseMetaData databaseMetaData, String catalog, String schemaPattern,
            String typeNamePattern, int[] types) throws SQLException {
        List<UserDefinedType> result = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getUDTs(catalog, schemaPattern, typeNamePattern, types)) {
            while (rs.next()) {
                Optional<String> oCat = Optional.ofNullable(rs.getString("TYPE_CAT"));
                Optional<String> oSchema = Optional.ofNullable(rs.getString("TYPE_SCHEM"));
                String typeName = rs.getString("TYPE_NAME");
                String className = rs.getString("CLASS_NAME");
                int dataType = rs.getInt("DATA_TYPE");
                Optional<String> remarks = Optional.ofNullable(rs.getString("REMARKS"));

                JDBCType jdbcType;
                try {
                    jdbcType = JDBCType.valueOf(dataType);
                } catch (IllegalArgumentException e) {
                    jdbcType = JDBCType.OTHER;
                }
                Optional<CatalogReference> catRef = oCat.map(CatalogReference::new);
                Optional<SchemaReference> schemaRef = oSchema.map(sn -> new SchemaReference(catRef, sn));
                result.add(new UserDefinedTypeRecord(
                        new UserDefinedTypeReference(schemaRef, typeName),
                        className, jdbcType, remarks));
            }
        }
        return List.copyOf(result);
    }

    private List<BestRowIdentifier> getBestRowIdentifier(DatabaseMetaData databaseMetaData, TableReference table,
            int scope, boolean nullable) throws SQLException {
        Optional<SchemaReference> oSchema = table.schema();
        String schema = oSchema.map(SchemaReference::name).orElse(null);
        String catalog = oSchema.flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse(null);

        List<BestRowIdentifier> result = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getBestRowIdentifier(catalog, schema, table.name(), scope, nullable)) {
            while (rs.next()) {
                int scopeVal = rs.getInt("SCOPE");
                String columnName = rs.getString("COLUMN_NAME");
                int dataType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");

                OptionalInt columnSize = optInt(rs, "COLUMN_SIZE");
                OptionalInt decimalDigits = optShort(rs, "DECIMAL_DIGITS");
                int pseudoCol = rs.getInt("PSEUDO_COLUMN");

                ColumnReference colRef = new ColumnReference(Optional.of(table), columnName);
                result.add(new BestRowIdentifierRecord(colRef,
                        BestRowIdentifier.Scope.of(scopeVal),
                        jdbcTypeOrOther(dataType),
                        typeName,
                        columnSize,
                        decimalDigits,
                        BestRowIdentifier.PseudoColumnKind.of(pseudoCol)));
            }
        }
        return List.copyOf(result);
    }

    private List<VersionColumn> getVersionColumns(DatabaseMetaData databaseMetaData, TableReference table)
            throws SQLException {
        Optional<SchemaReference> oSchema = table.schema();
        String schema = oSchema.map(SchemaReference::name).orElse(null);
        String catalog = oSchema.flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse(null);

        List<VersionColumn> result = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getVersionColumns(catalog, schema, table.name())) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                int dataType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");
                OptionalInt columnSize = optInt(rs, "COLUMN_SIZE");
                OptionalInt decimalDigits = optShort(rs, "DECIMAL_DIGITS");
                int pseudoCol = rs.getInt("PSEUDO_COLUMN");

                ColumnReference colRef = new ColumnReference(Optional.of(table), columnName);
                result.add(new VersionColumnRecord(colRef,
                        jdbcTypeOrOther(dataType),
                        typeName,
                        columnSize,
                        decimalDigits,
                        VersionColumn.PseudoColumnKind.of(pseudoCol)));
            }
        }
        return List.copyOf(result);
    }

    private List<PseudoColumn> getPseudoColumns(DatabaseMetaData databaseMetaData, String catalog, String schemaPattern,
            String tableNamePattern, String columnNamePattern) throws SQLException {
        List<PseudoColumn> result = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getPseudoColumns(catalog, schemaPattern, tableNamePattern,
                columnNamePattern)) {
            while (rs.next()) {
                Optional<String> oCat = Optional.ofNullable(rs.getString("TABLE_CAT"));
                Optional<String> oSchema = Optional.ofNullable(rs.getString("TABLE_SCHEM"));
                String tableName = rs.getString("TABLE_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                int dataType = rs.getInt("DATA_TYPE");

                OptionalInt columnSize = optInt(rs, "COLUMN_SIZE");
                OptionalInt decimalDigits = optInt(rs, "DECIMAL_DIGITS");
                OptionalInt numPrecRadix = optInt(rs, "NUM_PREC_RADIX");
                Optional<String> remarks = Optional.ofNullable(rs.getString("REMARKS"));
                OptionalInt charOctetLength = optInt(rs, "CHAR_OCTET_LENGTH");
                Optional<String> isNullable = Optional.ofNullable(rs.getString("IS_NULLABLE"));
                String columnUsage = rs.getString("COLUMN_USAGE");

                Optional<CatalogReference> catRef = oCat.map(CatalogReference::new);
                Optional<SchemaReference> schemaRef = oSchema.map(sn -> new SchemaReference(catRef, sn));
                TableReference tableRef = new TableReference(schemaRef, tableName);
                ColumnReference colRef = new ColumnReference(Optional.of(tableRef), columnName);

                result.add(new PseudoColumnRecord(colRef, jdbcTypeOrOther(dataType),
                        columnSize, decimalDigits, numPrecRadix, remarks, charOctetLength, isNullable, columnUsage));
            }
        }
        return List.copyOf(result);
    }

    private List<TablePrivilege> getTablePrivileges(DatabaseMetaData databaseMetaData, String catalog,
            String schemaPattern, String tableNamePattern) throws SQLException {
        List<TablePrivilege> result = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getTablePrivileges(catalog, schemaPattern, tableNamePattern)) {
            while (rs.next()) {
                Optional<String> oCat = Optional.ofNullable(rs.getString("TABLE_CAT"));
                Optional<String> oSchema = Optional.ofNullable(rs.getString("TABLE_SCHEM"));
                String tableName = rs.getString("TABLE_NAME");
                Optional<String> grantor = Optional.ofNullable(rs.getString("GRANTOR"));
                String grantee = rs.getString("GRANTEE");
                String privilege = rs.getString("PRIVILEGE");
                Optional<String> isGrantable = Optional.ofNullable(rs.getString("IS_GRANTABLE"));

                Optional<CatalogReference> catRef = oCat.map(CatalogReference::new);
                Optional<SchemaReference> schemaRef = oSchema.map(sn -> new SchemaReference(catRef, sn));
                TableReference tableRef = new TableReference(schemaRef, tableName);

                result.add(new TablePrivilegeRecord(tableRef, grantor, grantee, privilege, isGrantable));
            }
        }
        return List.copyOf(result);
    }

    private List<ColumnPrivilege> getColumnPrivileges(DatabaseMetaData databaseMetaData, TableReference table,
            String columnNamePattern) throws SQLException {
        Optional<SchemaReference> oSchema = table.schema();
        String schema = oSchema.map(SchemaReference::name).orElse(null);
        String catalog = oSchema.flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse(null);

        List<ColumnPrivilege> result = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getColumnPrivileges(catalog, schema, table.name(), columnNamePattern)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                Optional<String> grantor = Optional.ofNullable(rs.getString("GRANTOR"));
                String grantee = rs.getString("GRANTEE");
                String privilege = rs.getString("PRIVILEGE");
                Optional<String> isGrantable = Optional.ofNullable(rs.getString("IS_GRANTABLE"));

                ColumnReference colRef = new ColumnReference(Optional.of(table), columnName);
                result.add(new ColumnPrivilegeRecord(colRef, grantor, grantee, privilege, isGrantable));
            }
        }
        return List.copyOf(result);
    }

    private List<SuperType> getSuperTypes(DatabaseMetaData databaseMetaData, String catalog, String schemaPattern,
            String typeNamePattern) throws SQLException {
        List<SuperType> result = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getSuperTypes(catalog, schemaPattern, typeNamePattern)) {
            while (rs.next()) {
                Optional<String> oTypeCat = Optional.ofNullable(rs.getString("TYPE_CAT"));
                Optional<String> oTypeSchema = Optional.ofNullable(rs.getString("TYPE_SCHEM"));
                String typeName = rs.getString("TYPE_NAME");
                Optional<String> oSuperCat = Optional.ofNullable(rs.getString("SUPERTYPE_CAT"));
                Optional<String> oSuperSchema = Optional.ofNullable(rs.getString("SUPERTYPE_SCHEM"));
                String superTypeName = rs.getString("SUPERTYPE_NAME");

                Optional<CatalogReference> typeCatRef = oTypeCat.map(CatalogReference::new);
                Optional<SchemaReference> typeSchemaRef = oTypeSchema.map(sn -> new SchemaReference(typeCatRef, sn));
                Optional<CatalogReference> superCatRef = oSuperCat.map(CatalogReference::new);
                Optional<SchemaReference> superSchemaRef = oSuperSchema
                        .map(sn -> new SchemaReference(superCatRef, sn));

                result.add(new SuperTypeRecord(typeName, typeSchemaRef, superTypeName, superSchemaRef));
            }
        }
        return List.copyOf(result);
    }

    private List<SuperTable> getSuperTables(DatabaseMetaData databaseMetaData, String catalog, String schemaPattern,
            String tableNamePattern) throws SQLException {
        List<SuperTable> result = new ArrayList<>();
        try (ResultSet rs = databaseMetaData.getSuperTables(catalog, schemaPattern, tableNamePattern)) {
            while (rs.next()) {
                Optional<String> oCat = Optional.ofNullable(rs.getString("TABLE_CAT"));
                Optional<String> oSchema = Optional.ofNullable(rs.getString("TABLE_SCHEM"));
                String tableName = rs.getString("TABLE_NAME");
                String superTableName = rs.getString("SUPERTABLE_NAME");

                Optional<CatalogReference> catRef = oCat.map(CatalogReference::new);
                Optional<SchemaReference> schemaRef = oSchema.map(sn -> new SchemaReference(catRef, sn));
                TableReference tableRef = new TableReference(schemaRef, tableName);

                result.add(new SuperTableRecord(tableRef, superTableName));
            }
        }
        return List.copyOf(result);
    }

    private static OptionalInt optInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? OptionalInt.empty() : OptionalInt.of(v);
    }

    private static OptionalInt optShort(ResultSet rs, String column) throws SQLException {
        short v = rs.getShort(column);
        return rs.wasNull() ? OptionalInt.empty() : OptionalInt.of(v);
    }

    private static JDBCType jdbcTypeOrOther(int dataType) {
        try {
            return JDBCType.valueOf(dataType);
        } catch (IllegalArgumentException e) {
            return JDBCType.OTHER;
        }
    }

    // ================================================================
    // Modern (Connection, MetadataProvider, …) surface
    // Each method consults the provider first; on Optional.empty the
    // private (DatabaseMetaData, …) helper above handles the JDBC fallback.
    // ================================================================

    @Override
    public List<CatalogReference> getCatalogs(Connection connection, MetadataProvider provider) throws SQLException {
        return provider.getAllCatalogs(connection)
                .orElseGet(() -> invokeJdbc(() -> getCatalogs(connection.getMetaData())));
    }

    @Override
    public List<SchemaReference> getSchemas(Connection connection, MetadataProvider provider, String catalog)
            throws SQLException {
        return provider.getAllSchemas(connection, catalog)
                .orElseGet(() -> invokeJdbc(() -> {
                    CatalogReference catRef = catalog == null ? null : new CatalogReference(catalog);
                    return getSchemas(connection.getMetaData(), catRef);
                }));
    }

    @Override
    public List<String> getTableTypes(Connection connection, MetadataProvider provider) throws SQLException {
        return provider.getAllTableTypes(connection)
                .orElseGet(() -> invokeJdbc(() -> getTableTypes(connection.getMetaData())));
    }

    @Override
    public List<TypeInfo> getTypeInfo(Connection connection, MetadataProvider provider) throws SQLException {
        return provider.getAllTypeInfo(connection)
                .orElseGet(() -> invokeJdbc(() -> getTypeInfo(connection.getMetaData())));
    }

    @Override
    public List<TableDefinition> getTableDefinitions(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
        return provider.getAllTableDefinitions(connection, catalog, schemaPattern, tableNamePattern, types)
                .orElseGet(() -> invokeJdbc(() -> getTableDefinitions(connection.getMetaData(),
                        catalog, schemaPattern, tableNamePattern, types)));
    }

    @Override
    public boolean tableExists(Connection connection, MetadataProvider provider, TableReference table)
            throws SQLException {
        return tableExists(connection.getMetaData(), table);
    }

    @Override
    public boolean tableExists(Connection connection, MetadataProvider provider, String catalog, String schemaPattern,
            String tableNamePattern, String[] types) throws SQLException {
        return tableExists(connection.getMetaData(), catalog, schemaPattern, tableNamePattern, types);
    }

    @Override
    public List<ColumnDefinition> getColumnDefinitions(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        return provider.getAllColumnDefinitions(connection, catalog, schemaPattern, tableNamePattern, columnNamePattern)
                .orElseGet(() -> invokeJdbc(() -> getColumnDefinitions(connection.getMetaData(),
                        catalog, schemaPattern, tableNamePattern, columnNamePattern)));
    }

    @Override
    public boolean columnExists(Connection connection, MetadataProvider provider, String catalog, String schemaPattern,
            String tableNamePattern, String columnNamePattern) throws SQLException {
        return columnExists(connection.getMetaData(), catalog, schemaPattern, tableNamePattern, columnNamePattern);
    }

    @Override
    public boolean columnExists(Connection connection, MetadataProvider provider, ColumnReference column)
            throws SQLException {
        return columnExists(connection.getMetaData(), column);
    }

    @Override
    public List<ImportedKey> getImportedKeys(Connection connection, MetadataProvider provider, String catalog,
            String schema, String tableName) throws SQLException {
        Optional<List<ImportedKey>> allOpt = provider.getAllImportedKeys(connection, catalog, schema);
        if (allOpt.isPresent()) {
            List<ImportedKey> all = allOpt.get();
            List<ImportedKey> result = new ArrayList<>();
            for (ImportedKey k : all) {
                if (k.foreignKeyColumn().table().isPresent()
                        && tableName.equals(k.foreignKeyColumn().table().get().name())) {
                    result.add(k);
                }
            }
            return List.copyOf(result);
        }
        return getImportedKeys(connection.getMetaData(), catalog, schema, tableName);
    }

    @Override
    public List<ImportedKey> getExportedKeys(Connection connection, MetadataProvider provider, String catalog,
            String schema, String tableName) throws SQLException {
        Optional<List<ImportedKey>> perTable = provider.getExportedKeys(connection, catalog, schema, tableName);
        if (perTable.isPresent()) {
            return perTable.get();
        }
        Optional<List<ImportedKey>> allOpt = provider.getAllExportedKeys(connection, catalog, schema);
        if (allOpt.isPresent()) {
            List<ImportedKey> all = allOpt.get();
            List<ImportedKey> result = new ArrayList<>();
            for (ImportedKey k : all) {
                if (k.primaryKeyColumn().table().isPresent()
                        && tableName.equals(k.primaryKeyColumn().table().get().name())) {
                    result.add(k);
                }
            }
            return List.copyOf(result);
        }
        return getExportedKeys(connection.getMetaData(), catalog, schema, tableName);
    }

    @Override
    public List<ImportedKey> getCrossReference(Connection connection, MetadataProvider provider, String parentCatalog,
            String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable)
            throws SQLException {
        return provider.getCrossReference(connection, parentCatalog, parentSchema, parentTable,
                        foreignCatalog, foreignSchema, foreignTable)
                .orElseGet(() -> invokeJdbc(() -> getCrossReference(connection.getMetaData(),
                        parentCatalog, parentSchema, parentTable,
                        foreignCatalog, foreignSchema, foreignTable)));
    }

    @Override
    public List<Procedure> getProcedures(Connection connection, MetadataProvider provider) throws SQLException {
        return getProcedures(connection.getMetaData());
    }

    @Override
    public List<Procedure> getProcedures(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String procedureNamePattern) throws SQLException {
        return getProcedures(connection.getMetaData(), catalog, schemaPattern, procedureNamePattern);
    }

    @Override
    public List<Function> getFunctions(Connection connection, MetadataProvider provider) throws SQLException {
        return getFunctions(connection.getMetaData());
    }

    @Override
    public List<Function> getFunctions(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String functionNamePattern) throws SQLException {
        return getFunctions(connection.getMetaData(), catalog, schemaPattern, functionNamePattern);
    }

    @Override
    public List<UserDefinedType> getUDTs(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String typeNamePattern, int[] types) throws SQLException {
        return provider.getAllUDTs(connection, catalog, schemaPattern, typeNamePattern, types)
                .orElseGet(() -> invokeJdbc(() -> getUDTs(connection.getMetaData(),
                        catalog, schemaPattern, typeNamePattern, types)));
    }

    @Override
    public List<BestRowIdentifier> getBestRowIdentifier(Connection connection, MetadataProvider provider,
            TableReference table, int scope, boolean nullable) throws SQLException {
        String schema = table.schema().map(SchemaReference::name).orElse(null);
        String catalog = table.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse(null);
        return provider.getBestRowIdentifier(connection, catalog, schema, table.name(), scope, nullable)
                .orElseGet(() -> invokeJdbc(() -> getBestRowIdentifier(connection.getMetaData(), table, scope, nullable)));
    }

    @Override
    public List<VersionColumn> getVersionColumns(Connection connection, MetadataProvider provider, TableReference table)
            throws SQLException {
        String schema = table.schema().map(SchemaReference::name).orElse(null);
        String catalog = table.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse(null);
        return provider.getVersionColumns(connection, catalog, schema, table.name())
                .orElseGet(() -> invokeJdbc(() -> getVersionColumns(connection.getMetaData(), table)));
    }

    @Override
    public List<PseudoColumn> getPseudoColumns(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        return provider.getAllPseudoColumns(connection, catalog, schemaPattern, tableNamePattern, columnNamePattern)
                .orElseGet(() -> invokeJdbc(() -> getPseudoColumns(connection.getMetaData(),
                        catalog, schemaPattern, tableNamePattern, columnNamePattern)));
    }

    @Override
    public List<TablePrivilege> getTablePrivileges(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String tableNamePattern) throws SQLException {
        return provider.getAllTablePrivileges(connection, catalog, schemaPattern, tableNamePattern)
                .orElseGet(() -> invokeJdbc(() -> getTablePrivileges(connection.getMetaData(),
                        catalog, schemaPattern, tableNamePattern)));
    }

    @Override
    public List<ColumnPrivilege> getColumnPrivileges(Connection connection, MetadataProvider provider,
            TableReference table, String columnNamePattern) throws SQLException {
        String schema = table.schema().map(SchemaReference::name).orElse(null);
        String catalog = table.schema().flatMap(SchemaReference::catalog).map(CatalogReference::name).orElse(null);
        return provider.getColumnPrivileges(connection, catalog, schema, table.name(), columnNamePattern)
                .orElseGet(() -> invokeJdbc(() -> getColumnPrivileges(connection.getMetaData(), table, columnNamePattern)));
    }

    @Override
    public List<SuperType> getSuperTypes(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String typeNamePattern) throws SQLException {
        return provider.getAllSuperTypes(connection, catalog, schemaPattern, typeNamePattern)
                .orElseGet(() -> invokeJdbc(() -> getSuperTypes(connection.getMetaData(),
                        catalog, schemaPattern, typeNamePattern)));
    }

    @Override
    public List<SuperTable> getSuperTables(Connection connection, MetadataProvider provider, String catalog,
            String schemaPattern, String tableNamePattern) throws SQLException {
        return provider.getAllSuperTables(connection, catalog, schemaPattern, tableNamePattern)
                .orElseGet(() -> invokeJdbc(() -> getSuperTables(connection.getMetaData(),
                        catalog, schemaPattern, tableNamePattern)));
    }

    private static <T> T invokeJdbc(SqlSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

}
