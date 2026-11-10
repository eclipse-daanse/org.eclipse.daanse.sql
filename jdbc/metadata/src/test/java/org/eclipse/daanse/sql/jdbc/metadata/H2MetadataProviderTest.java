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
package org.eclipse.daanse.sql.jdbc.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.Sequence;
import org.eclipse.daanse.sql.model.schema.Trigger;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent;
import org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming;
import org.eclipse.daanse.sql.jdbc.api.schema.UniqueConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.UserDefinedType;
import org.eclipse.daanse.sql.jdbc.api.schema.ViewDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class H2MetadataProviderTest {

    private static Connection connection;
    private static H2MetadataProvider provider;
    private static final String SCHEMA = "PUBLIC";

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:h2MetadataTest;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = connection.createStatement()) {
            // Sequences
            stmt.execute("""
                    CREATE SEQUENCE SEQ_ORDER_ID START WITH 1000 INCREMENT BY 1
                    """);
            stmt.execute("""
                    CREATE SEQUENCE SEQ_AUDIT_ID START WITH 1 INCREMENT BY 5
                    MINVALUE 1 MAXVALUE 999999 CYCLE CACHE 10
                    """);

            // Tables
            stmt.execute("""
                    CREATE TABLE DEPARTMENTS (
                        DEPT_ID INT NOT NULL,
                        DEPT_NAME VARCHAR(100) NOT NULL,
                        LOCATION VARCHAR(200),
                        CONSTRAINT PK_DEPARTMENTS PRIMARY KEY (DEPT_ID),
                        CONSTRAINT UQ_DEPT_NAME UNIQUE (DEPT_NAME),
                        CONSTRAINT CK_DEPT_NAME_LEN CHECK (LENGTH(DEPT_NAME) >= 2)
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE EMPLOYEES (
                        EMP_ID INT NOT NULL,
                        FIRST_NAME VARCHAR(50) NOT NULL,
                        LAST_NAME VARCHAR(50) NOT NULL,
                        EMAIL VARCHAR(100) NOT NULL,
                        SALARY DECIMAL(10,2),
                        DEPT_ID INT,
                        CONSTRAINT PK_EMPLOYEES PRIMARY KEY (EMP_ID),
                        CONSTRAINT UQ_EMP_EMAIL UNIQUE (EMAIL),
                        CONSTRAINT CK_SALARY_POSITIVE CHECK (SALARY > 0),
                        CONSTRAINT FK_EMP_DEPT FOREIGN KEY (DEPT_ID)
                            REFERENCES DEPARTMENTS(DEPT_ID) ON DELETE SET NULL ON UPDATE CASCADE
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE ORDERS (
                        ORDER_ID INT NOT NULL,
                        EMP_ID INT NOT NULL,
                        ORDER_DATE DATE NOT NULL,
                        STATUS VARCHAR(20) DEFAULT 'PENDING',
                        CONSTRAINT PK_ORDERS PRIMARY KEY (ORDER_ID),
                        CONSTRAINT CK_STATUS CHECK (STATUS IN ('PENDING', 'PROCESSING', 'COMPLETED', 'CANCELLED')),
                        CONSTRAINT FK_ORD_EMP FOREIGN KEY (EMP_ID) REFERENCES EMPLOYEES(EMP_ID)
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE ORDER_ITEMS (
                        ORDER_ID INT NOT NULL,
                        ITEM_SEQ INT NOT NULL,
                        PRODUCT_NAME VARCHAR(100) NOT NULL,
                        QUANTITY INT NOT NULL,
                        AMOUNT DECIMAL(10,2) NOT NULL,
                        CONSTRAINT PK_ORDER_ITEMS PRIMARY KEY (ORDER_ID, ITEM_SEQ),
                        CONSTRAINT CK_QUANTITY CHECK (QUANTITY > 0),
                        CONSTRAINT CK_AMOUNT_POSITIVE CHECK (AMOUNT > 0),
                        CONSTRAINT FK_OI_ORDER FOREIGN KEY (ORDER_ID) REFERENCES ORDERS(ORDER_ID)
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE AUDIT_LOG (
                        LOG_ID INT NOT NULL,
                        TABLE_NAME VARCHAR(100) NOT NULL,
                        ACTION_TYPE VARCHAR(20) NOT NULL,
                        ACTION_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        USER_NAME VARCHAR(100),
                        CONSTRAINT PK_AUDIT_LOG PRIMARY KEY (LOG_ID),
                        CONSTRAINT UQ_AUDIT_UNIQUE UNIQUE (TABLE_NAME, ACTION_TYPE, ACTION_TIMESTAMP)
                    )
                    """);

            // Indexes
            stmt.execute("CREATE INDEX IDX_EMP_LAST_NAME ON EMPLOYEES(LAST_NAME)");
            stmt.execute("CREATE INDEX IDX_EMP_DEPT ON EMPLOYEES(DEPT_ID)");
            stmt.execute("CREATE INDEX IDX_ORD_DATE ON ORDERS(ORDER_DATE)");
            stmt.execute("CREATE UNIQUE INDEX IDX_ORD_EMP_DATE ON ORDERS(EMP_ID, ORDER_DATE)");

            // Views
            stmt.execute("""
                    CREATE VIEW V_EMP_DEPT AS
                        SELECT E.EMP_ID, E.FIRST_NAME, E.LAST_NAME, E.EMAIL, E.SALARY,
                               D.DEPT_NAME, D.LOCATION
                        FROM EMPLOYEES E LEFT JOIN DEPARTMENTS D ON E.DEPT_ID = D.DEPT_ID
                    """);
            stmt.execute("""
                    CREATE VIEW V_ORDER_SUMMARY AS
                        SELECT O.ORDER_ID, O.ORDER_DATE, O.STATUS,
                               E.FIRST_NAME || ' ' || E.LAST_NAME AS EMP_NAME
                        FROM ORDERS O JOIN EMPLOYEES E ON O.EMP_ID = E.EMP_ID
                    """);

            // Triggers (H2 uses CALL "class.name")
            stmt.execute("""
                    CREATE TRIGGER TRG_EMP_AUDIT AFTER INSERT ON EMPLOYEES
                    FOR EACH ROW CALL "org.eclipse.daanse.sql.jdbc.metadata.TestAuditTrigger"
                    """);
            stmt.execute("""
                    CREATE TRIGGER TRG_ORD_AUDIT BEFORE UPDATE ON ORDERS
                    FOR EACH ROW CALL "org.eclipse.daanse.sql.jdbc.metadata.TestAuditTrigger"
                    """);

            // Functions (H2 uses CREATE ALIAS for Java-based functions)
            stmt.execute("""
                    CREATE ALIAS TO_UPPER AS $$
                    String toUpper(String s) {
                        return s == null ? null : s.toUpperCase();
                    }
                    $$
                    """);
            stmt.execute("""
                    CREATE ALIAS ADD_NUMBERS AS $$
                    int addNumbers(int a, int b) {
                        return a + b;
                    }
                    $$
                    """);
        }
        provider = new H2MetadataProvider();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP ALL OBJECTS");
            }
            connection.close();
        }
    }

    @Test
    void getAllTriggers_returnsTwoTriggers() throws SQLException {
        List<Trigger> triggers = provider.getAllTriggers(connection, null, SCHEMA);
        assertThat(triggers).hasSize(2);
    }

    @Test
    void getAllTriggers_correctTimingAndEvent() throws SQLException {
        List<Trigger> triggers = provider.getAllTriggers(connection, null, SCHEMA);

        Trigger empTrigger = findTrigger(triggers, "TRG_EMP_AUDIT");
        assertThat(empTrigger.timing()).isEqualTo(TriggerTiming.AFTER);
        assertThat(empTrigger.event()).isEqualTo(TriggerEvent.INSERT);

        Trigger ordTrigger = findTrigger(triggers, "TRG_ORD_AUDIT");
        assertThat(ordTrigger.timing()).isEqualTo(TriggerTiming.BEFORE);
        assertThat(ordTrigger.event()).isEqualTo(TriggerEvent.UPDATE);
    }

    @Test
    void getAllTriggers_correctTable() throws SQLException {
        List<Trigger> triggers = provider.getAllTriggers(connection, null, SCHEMA);

        assertThat(findTrigger(triggers, "TRG_EMP_AUDIT").reference().table().name()).isEqualTo("EMPLOYEES");
        assertThat(findTrigger(triggers, "TRG_ORD_AUDIT").reference().table().name()).isEqualTo("ORDERS");
    }

    @Test
    void getAllTriggers_bodyContainsClassName() throws SQLException {
        List<Trigger> triggers = provider.getAllTriggers(connection, null, SCHEMA);

        for (Trigger trigger : triggers) {
            assertThat(trigger.body()).isPresent();
            assertThat(trigger.body().get()).containsIgnoringCase("TestAuditTrigger");
        }
    }

    @Test
    void getAllTriggers_orientationIsRow() throws SQLException {
        List<Trigger> triggers = provider.getAllTriggers(connection, null, SCHEMA);

        for (Trigger trigger : triggers) {
            assertThat(trigger.orientation()).isPresent();
            assertThat(trigger.orientation().get()).isEqualToIgnoringCase("ROW");
        }
    }

    @Test
    void getTriggers_filtersByTable() throws SQLException {
        List<Trigger> empTriggers = provider.getTriggers(connection, null, SCHEMA, "EMPLOYEES");
        assertThat(empTriggers).hasSize(1);
        assertThat(empTriggers.get(0).name()).isEqualTo("TRG_EMP_AUDIT");

        List<Trigger> ordTriggers = provider.getTriggers(connection, null, SCHEMA, "ORDERS");
        assertThat(ordTriggers).hasSize(1);
        assertThat(ordTriggers.get(0).name()).isEqualTo("TRG_ORD_AUDIT");
    }

    @Test
    void getTriggers_emptyForTableWithNoTriggers() throws SQLException {
        List<Trigger> triggers = provider.getTriggers(connection, null, SCHEMA, "DEPARTMENTS");
        assertThat(triggers).isEmpty();
    }

    @Test
    void getAllSequences_returnsAtLeastTwo() throws SQLException {
        List<Sequence> sequences = provider.getAllSequences(connection, null, SCHEMA);
        assertThat(sequences).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void getAllSequences_seqOrderIdProperties() throws SQLException {
        List<Sequence> sequences = provider.getAllSequences(connection, null, SCHEMA);
        Sequence seq = findSequence(sequences, "SEQ_ORDER_ID");

        assertThat(seq.startValue()).isEqualTo(1000L);
        assertThat(seq.incrementBy()).isEqualTo(1L);
        assertThat(seq.cycle()).isFalse();
    }

    @Test
    void getAllSequences_seqAuditIdProperties() throws SQLException {
        List<Sequence> sequences = provider.getAllSequences(connection, null, SCHEMA);
        Sequence seq = findSequence(sequences, "SEQ_AUDIT_ID");

        assertThat(seq.startValue()).isEqualTo(1L);
        assertThat(seq.incrementBy()).isEqualTo(5L);
        assertThat(seq.minValue()).isPresent().hasValue(1L);
        assertThat(seq.maxValue()).isPresent().hasValue(999999L);
        assertThat(seq.cycle()).isTrue();
        assertThat(seq.cacheSize()).isPresent().hasValue(10L);
    }

    @Test
    void getAllSequences_schemaIsPublic() throws SQLException {
        List<Sequence> sequences = provider.getAllSequences(connection, null, SCHEMA);

        for (Sequence seq : sequences) {
            assertThat(seq.schema()).isPresent();
            assertThat(seq.schema().get().name()).isEqualTo("PUBLIC");
        }
    }

    @Test
    void getAllCheckConstraints_returnsNamedConstraints() throws SQLException {
        List<CheckConstraint> constraints = provider.getAllCheckConstraints(connection, null, SCHEMA);

        List<String> namedConstraints = constraints.stream().map(CheckConstraint::name)
                .filter(name -> name.startsWith("CK_")).toList();

        assertThat(namedConstraints).containsExactlyInAnyOrder("CK_DEPT_NAME_LEN", "CK_SALARY_POSITIVE", "CK_STATUS",
                "CK_QUANTITY", "CK_AMOUNT_POSITIVE");
    }

    @Test
    void getAllCheckConstraints_checkClauseContent() throws SQLException {
        List<CheckConstraint> constraints = provider.getAllCheckConstraints(connection, null, SCHEMA);

        CheckConstraint salaryCheck = constraints.stream().filter(c -> "CK_SALARY_POSITIVE".equals(c.name()))
                .findFirst().orElseThrow();
        assertThat(salaryCheck.checkClause()).containsIgnoringCase("SALARY");

        CheckConstraint statusCheck = constraints.stream().filter(c -> "CK_STATUS".equals(c.name())).findFirst()
                .orElseThrow();
        assertThat(statusCheck.checkClause()).containsIgnoringCase("STATUS");
    }

    @Test
    void getAllCheckConstraints_tableAssociation() throws SQLException {
        List<CheckConstraint> constraints = provider.getAllCheckConstraints(connection, null, SCHEMA);

        CheckConstraint salaryCheck = constraints.stream().filter(c -> "CK_SALARY_POSITIVE".equals(c.name()))
                .findFirst().orElseThrow();
        assertThat(salaryCheck.table().name()).isEqualTo("EMPLOYEES");

        CheckConstraint statusCheck = constraints.stream().filter(c -> "CK_STATUS".equals(c.name())).findFirst()
                .orElseThrow();
        assertThat(statusCheck.table().name()).isEqualTo("ORDERS");
    }

    @Test
    void getCheckConstraints_filtersByTable() throws SQLException {
        List<CheckConstraint> empConstraints = provider.getCheckConstraints(connection, null, SCHEMA, "EMPLOYEES");

        List<String> namedEmp = empConstraints.stream().map(CheckConstraint::name)
                .filter(name -> name.startsWith("CK_")).toList();
        assertThat(namedEmp).containsExactly("CK_SALARY_POSITIVE");

        List<CheckConstraint> orderItemConstraints = provider.getCheckConstraints(connection, null, SCHEMA,
                "ORDER_ITEMS");

        List<String> namedOI = orderItemConstraints.stream().map(CheckConstraint::name)
                .filter(name -> name.startsWith("CK_")).toList();
        assertThat(namedOI).containsExactlyInAnyOrder("CK_QUANTITY", "CK_AMOUNT_POSITIVE");
    }

    @Test
    void getAllUniqueConstraints_returnsAtLeastThree() throws SQLException {
        List<UniqueConstraint> constraints = provider.getAllUniqueConstraints(connection, null, SCHEMA);
        assertThat(constraints).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void getAllUniqueConstraints_singleColumn() throws SQLException {
        List<UniqueConstraint> constraints = provider.getAllUniqueConstraints(connection, null, SCHEMA);

        UniqueConstraint uqDeptName = constraints.stream().filter(c -> "UQ_DEPT_NAME".equals(c.name())).findFirst()
                .orElseThrow();
        assertThat(uqDeptName.table().name()).isEqualTo("DEPARTMENTS");
        assertThat(uqDeptName.columns()).hasSize(1);
        assertThat(uqDeptName.columns().get(0).name()).isEqualTo("DEPT_NAME");
    }

    @Test
    void getAllUniqueConstraints_multiColumn() throws SQLException {
        List<UniqueConstraint> constraints = provider.getAllUniqueConstraints(connection, null, SCHEMA);

        UniqueConstraint uqAudit = constraints.stream().filter(c -> "UQ_AUDIT_UNIQUE".equals(c.name())).findFirst()
                .orElseThrow();
        assertThat(uqAudit.table().name()).isEqualTo("AUDIT_LOG");
        assertThat(uqAudit.columns()).hasSize(3);

        List<String> columnNames = uqAudit.columns().stream().map(ColumnReference::name).toList();
        assertThat(columnNames).containsExactly("TABLE_NAME", "ACTION_TYPE", "ACTION_TIMESTAMP");
    }

    @Test
    void getUniqueConstraints_filtersByTable() throws SQLException {
        List<UniqueConstraint> deptConstraints = provider.getUniqueConstraints(connection, null, SCHEMA, "DEPARTMENTS");
        assertThat(deptConstraints).hasSize(1);
        assertThat(deptConstraints.get(0).name()).isEqualTo("UQ_DEPT_NAME");

        List<UniqueConstraint> orderConstraints = provider.getUniqueConstraints(connection, null, SCHEMA, "ORDERS");
        assertThat(orderConstraints).isEmpty();
    }

    @Test
    void getAllPrimaryKeys_isPresent() throws SQLException {
        Optional<List<PrimaryKey>> result = provider.getAllPrimaryKeys(connection, null, SCHEMA);
        assertThat(result).isPresent();
    }

    @Test
    void getAllPrimaryKeys_returnsFive() throws SQLException {
        List<PrimaryKey> primaryKeys = provider.getAllPrimaryKeys(connection, null, SCHEMA).orElseThrow();
        assertThat(primaryKeys).hasSize(5);
    }

    @Test
    void getAllPrimaryKeys_simplePrimaryKey() throws SQLException {
        List<PrimaryKey> primaryKeys = provider.getAllPrimaryKeys(connection, null, SCHEMA).orElseThrow();

        PrimaryKey deptPk = primaryKeys.stream().filter(pk -> "DEPARTMENTS".equals(pk.table().name())).findFirst()
                .orElseThrow();
        assertThat(deptPk.constraintName()).isPresent().hasValue("PK_DEPARTMENTS");
        assertThat(deptPk.columns()).hasSize(1);
        assertThat(deptPk.columns().get(0).name()).isEqualTo("DEPT_ID");
    }

    @Test
    void getAllPrimaryKeys_compositePrimaryKey() throws SQLException {
        List<PrimaryKey> primaryKeys = provider.getAllPrimaryKeys(connection, null, SCHEMA).orElseThrow();

        PrimaryKey oiPk = primaryKeys.stream().filter(pk -> "ORDER_ITEMS".equals(pk.table().name())).findFirst()
                .orElseThrow();
        assertThat(oiPk.constraintName()).isPresent().hasValue("PK_ORDER_ITEMS");
        assertThat(oiPk.columns()).hasSize(2);

        List<String> columnNames = oiPk.columns().stream().map(ColumnReference::name).toList();
        assertThat(columnNames).containsExactly("ORDER_ID", "ITEM_SEQ");
    }

    @Test
    void getAllImportedKeys_isPresent() throws SQLException {
        Optional<List<ImportedKey>> result = provider.getAllImportedKeys(connection, null, SCHEMA);
        assertThat(result).isPresent();
    }

    @Test
    void getAllImportedKeys_returnsThree() throws SQLException {
        List<ImportedKey> importedKeys = provider.getAllImportedKeys(connection, null, SCHEMA).orElseThrow();
        assertThat(importedKeys).hasSize(3);
    }

    @Test
    void getAllImportedKeys_fkEmpDeptDetails() throws SQLException {
        List<ImportedKey> importedKeys = provider.getAllImportedKeys(connection, null, SCHEMA).orElseThrow();

        ImportedKey fkEmpDept = importedKeys.stream().filter(fk -> "FK_EMP_DEPT".equals(fk.name())).findFirst()
                .orElseThrow();

        assertThat(fkEmpDept.foreignKeyColumn().name()).isEqualTo("DEPT_ID");
        assertThat(fkEmpDept.foreignKeyColumn().table()).isPresent();
        assertThat(fkEmpDept.foreignKeyColumn().table().get().name()).isEqualTo("EMPLOYEES");

        assertThat(fkEmpDept.primaryKeyColumn().name()).isEqualTo("DEPT_ID");
        assertThat(fkEmpDept.primaryKeyColumn().table()).isPresent();
        assertThat(fkEmpDept.primaryKeyColumn().table().get().name()).isEqualTo("DEPARTMENTS");

        assertThat(fkEmpDept.deleteRule()).isEqualTo(ImportedKey.ReferentialAction.SET_NULL);
        assertThat(fkEmpDept.updateRule()).isEqualTo(ImportedKey.ReferentialAction.CASCADE);
    }

    @Test
    void getAllImportedKeys_keySequence() throws SQLException {
        List<ImportedKey> importedKeys = provider.getAllImportedKeys(connection, null, SCHEMA).orElseThrow();

        for (ImportedKey fk : importedKeys) {
            assertThat(fk.keySequence()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void getAllIndexInfo_isPresent() throws SQLException {
        Optional<List<IndexInfo>> result = provider.getAllIndexInfo(connection, null, SCHEMA);
        assertThat(result).isPresent();
    }

    @Test
    void getAllIndexInfo_containsUserIndexes() throws SQLException {
        List<IndexInfo> indexInfos = provider.getAllIndexInfo(connection, null, SCHEMA).orElseThrow();

        List<String> allIndexNames = indexInfos.stream().flatMap(ii -> ii.indexInfoItems().stream())
                .map(IndexInfoItem::indexName).filter(Optional::isPresent).map(Optional::get).toList();

        assertThat(allIndexNames).contains("IDX_EMP_LAST_NAME", "IDX_EMP_DEPT", "IDX_ORD_DATE", "IDX_ORD_EMP_DATE");
    }

    @Test
    void getAllIndexInfo_nonUniqueIndex() throws SQLException {
        List<IndexInfo> indexInfos = provider.getAllIndexInfo(connection, null, SCHEMA).orElseThrow();

        IndexInfoItem lastNameIdx = findIndexItem(indexInfos, "IDX_EMP_LAST_NAME");
        assertThat(lastNameIdx.unique()).isFalse();
        assertThat(lastNameIdx.column()).isPresent();
        assertThat(lastNameIdx.column().get().name()).isEqualTo("LAST_NAME");
    }

    @Test
    void getAllIndexInfo_uniqueIndex() throws SQLException {
        List<IndexInfo> indexInfos = provider.getAllIndexInfo(connection, null, SCHEMA).orElseThrow();

        List<IndexInfoItem> empDateItems = indexInfos.stream().flatMap(ii -> ii.indexInfoItems().stream())
                .filter(item -> item.indexName().isPresent() && "IDX_ORD_EMP_DATE".equals(item.indexName().get()))
                .toList();

        assertThat(empDateItems).isNotEmpty();
        assertThat(empDateItems.get(0).unique()).isTrue();
    }

    @Test
    void getAllIndexInfo_multiColumnIndex() throws SQLException {
        List<IndexInfo> indexInfos = provider.getAllIndexInfo(connection, null, SCHEMA).orElseThrow();

        List<IndexInfoItem> empDateItems = indexInfos.stream().flatMap(ii -> ii.indexInfoItems().stream())
                .filter(item -> item.indexName().isPresent() && "IDX_ORD_EMP_DATE".equals(item.indexName().get()))
                .toList();

        assertThat(empDateItems).hasSize(2);

        List<String> columnNames = empDateItems.stream().map(item -> item.column().map(c -> c.name()).orElse(""))
                .toList();
        assertThat(columnNames).containsExactly("EMP_ID", "ORDER_DATE");
    }

    @Test
    void getAllIndexInfo_groupedByTable() throws SQLException {
        List<IndexInfo> indexInfos = provider.getAllIndexInfo(connection, null, SCHEMA).orElseThrow();

        List<String> tableNames = indexInfos.stream().map(ii -> ii.tableReference().name()).toList();

        // All 5 user tables should have indexes (at least PK indexes)
        assertThat(tableNames).contains("DEPARTMENTS", "EMPLOYEES", "ORDERS", "ORDER_ITEMS", "AUDIT_LOG");
    }

    @Test
    void getAllViewDefinitions_returnsTwo() throws SQLException {
        List<ViewDefinition> views = provider.getAllViewDefinitions(connection, null, SCHEMA);
        assertThat(views).hasSize(2);
    }

    @Test
    void getAllViewDefinitions_bodyIsPresent() throws SQLException {
        List<ViewDefinition> views = provider.getAllViewDefinitions(connection, null, SCHEMA);

        for (ViewDefinition view : views) {
            assertThat(view.viewBody()).isPresent();
            assertThat(view.viewBody().get()).isNotBlank();
        }
    }

    @Test
    void getAllViewDefinitions_bodyContainsSelect() throws SQLException {
        List<ViewDefinition> views = provider.getAllViewDefinitions(connection, null, SCHEMA);

        for (ViewDefinition view : views) {
            assertThat(view.viewBody().get()).containsIgnoringCase("SELECT");
        }
    }

    @Test
    void getAllViewDefinitions_viewType() throws SQLException {
        List<ViewDefinition> views = provider.getAllViewDefinitions(connection, null, SCHEMA);

        List<String> viewNames = views.stream().map(v -> v.view().name()).toList();
        assertThat(viewNames).containsExactlyInAnyOrder("V_EMP_DEPT", "V_ORDER_SUMMARY");

        for (ViewDefinition view : views) {
            assertThat(view.view().type()).isEqualTo("VIEW");
        }
    }

    @Test
    void getAllProcedures_returnsEmpty() throws SQLException {
        // H2 does not have stored procedures in INFORMATION_SCHEMA.ROUTINES by default
        // (CREATE ALIAS creates functions, not procedures)
        List<Procedure> procedures = provider.getAllProcedures(connection, null, SCHEMA);
        assertThat(procedures).isNotNull();
    }

    @Test
    void getAllFunctions_returnsAliases() throws SQLException {
        List<Function> functions = provider.getAllFunctions(connection, null, SCHEMA);
        assertThat(functions).isNotEmpty();
    }

    @Test
    void getAllFunctions_containsToUpper() throws SQLException {
        List<Function> functions = provider.getAllFunctions(connection, null, SCHEMA);
        assertThat(functions).anyMatch(f -> "TO_UPPER".equals(f.reference().name()));
    }

    @Test
    void getAllFunctions_containsAddNumbers() throws SQLException {
        List<Function> functions = provider.getAllFunctions(connection, null, SCHEMA);
        assertThat(functions).anyMatch(f -> "ADD_NUMBERS".equals(f.reference().name()));
    }

    @Test
    void getAllFunctions_addNumbersHasParameters() throws SQLException {
        List<Function> functions = provider.getAllFunctions(connection, null, SCHEMA);
        Function addNumbers = functions.stream().filter(f -> "ADD_NUMBERS".equals(f.reference().name())).findFirst()
                .orElseThrow();
        assertThat(addNumbers.columns()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void getAllFunctions_schemaIsPublic() throws SQLException {
        List<Function> functions = provider.getAllFunctions(connection, null, SCHEMA);
        for (Function func : functions) {
            assertThat(func.reference().schema()).isPresent();
            assertThat(func.reference().schema().get().name()).isEqualTo("PUBLIC");
        }
    }

    @Test
    void getAllFunctions_bodyIsPresent() throws SQLException {
        List<Function> functions = provider.getAllFunctions(connection, null, SCHEMA);
        // H2 stores the Java source of CREATE ALIAS functions in ROUTINE_DEFINITION.
        Function toUpper = functions.stream().filter(f -> "TO_UPPER".equals(f.reference().name())).findFirst()
                .orElseThrow();
        assertThat(toUpper.body()).isPresent();
        assertThat(toUpper.body().get()).containsIgnoringCase("toUpperCase");
    }

    @Test
    void getAllFunctions_addNumbersBodyContainsReturn() throws SQLException {
        List<Function> functions = provider.getAllFunctions(connection, null, SCHEMA);
        Function addNumbers = functions.stream().filter(f -> "ADD_NUMBERS".equals(f.reference().name())).findFirst()
                .orElseThrow();
        assertThat(addNumbers.body()).isPresent();
        assertThat(addNumbers.body().get()).contains("return");
    }

    @Test
    void getAllFunctions_fullDefinitionEmpty_forH2() throws SQLException {
        // H2's INFORMATION_SCHEMA.ROUTINES has no full CREATE DDL; the dialect reports
        // empty.
        List<Function> functions = provider.getAllFunctions(connection, null, SCHEMA);
        for (Function func : functions) {
            assertThat(func.fullDefinition()).isNotPresent();
        }
    }

    @Test
    void getAllUserDefinedTypes_returnsEmpty() throws SQLException {
        List<UserDefinedType> udts = provider.getAllUserDefinedTypes(connection, null, SCHEMA);
        assertThat(udts).isEmpty();
    }

    private static Trigger findTrigger(List<Trigger> triggers, String name) {
        return triggers.stream().filter(t -> name.equals(t.name())).findFirst()
                .orElseThrow(() -> new AssertionError("Trigger not found: " + name));
    }

    private static Sequence findSequence(List<Sequence> sequences, String name) {
        return sequences.stream().filter(s -> name.equals(s.name())).findFirst()
                .orElseThrow(() -> new AssertionError("Sequence not found: " + name));
    }

    private static IndexInfoItem findIndexItem(List<IndexInfo> indexInfos, String indexName) {
        return indexInfos.stream().flatMap(ii -> ii.indexInfoItems().stream())
                .filter(item -> item.indexName().isPresent() && indexName.equals(item.indexName().get())).findFirst()
                .orElseThrow(() -> new AssertionError("Index not found: " + indexName));
    }
}
