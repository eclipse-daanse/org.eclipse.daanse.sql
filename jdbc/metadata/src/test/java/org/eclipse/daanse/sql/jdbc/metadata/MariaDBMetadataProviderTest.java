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
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.Partition;
import org.eclipse.daanse.sql.jdbc.api.schema.PartitionMethod;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class MariaDBMetadataProviderTest {

    private static final String DATABASE = "testdb";
    private static final String USER = "root";
    private static final String PASSWORD = "test";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> MARIADB = new GenericContainer<>("mariadb:10.11")
            .withEnv("MARIADB_ROOT_PASSWORD", PASSWORD).withEnv("MARIADB_DATABASE", DATABASE).withExposedPorts(3306)
            .waitingFor(
                    Wait.forLogMessage(".*ready for connections.*\\n", 2).withStartupTimeout(Duration.ofMinutes(2)));

    private static Connection connection;
    private static MariaDbMetadataProvider provider;

    @BeforeAll
    static void setUp() throws Exception {
        MARIADB.start();

        String host = MARIADB.getHost();
        int port = MARIADB.getMappedPort(3306);
        String jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + DATABASE;

        Class.forName("org.mariadb.jdbc.Driver");
        connection = DriverManager.getConnection(jdbcUrl, USER, PASSWORD);

        try (Statement stmt = connection.createStatement()) {
            // Sequences (MariaDB 10.3+)
            stmt.execute("CREATE SEQUENCE SEQ_ORDER_ID START WITH 1000 INCREMENT BY 1");
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
                        CONSTRAINT CK_DEPT_NAME_LEN CHECK (CHAR_LENGTH(DEPT_NAME) >= 2)
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
                                CONCAT(E.FIRST_NAME, ' ', E.LAST_NAME) AS EMP_NAME
                        FROM ORDERS O JOIN EMPLOYEES E ON O.EMP_ID = E.EMP_ID
                    """);

            // Triggers (MariaDB syntax)
            stmt.execute("""
                    CREATE TRIGGER TRG_EMP_AUDIT AFTER INSERT ON EMPLOYEES
                    FOR EACH ROW
                    INSERT INTO AUDIT_LOG (LOG_ID, TABLE_NAME, ACTION_TYPE, USER_NAME)
                    VALUES (NEW.EMP_ID, 'EMPLOYEES', 'INSERT', CURRENT_USER())
                    """);
            stmt.execute("""
                    CREATE TRIGGER TRG_ORD_AUDIT BEFORE UPDATE ON ORDERS
                    FOR EACH ROW
                    INSERT INTO AUDIT_LOG (LOG_ID, TABLE_NAME, ACTION_TYPE, USER_NAME)
                    VALUES (NEW.ORDER_ID, 'ORDERS', 'UPDATE', CURRENT_USER())
                    """);

            // Functions
            stmt.execute("""
                    CREATE FUNCTION TO_UPPER(s VARCHAR(255))
                    RETURNS VARCHAR(255) DETERMINISTIC
                    RETURN UPPER(s)
                    """);
            stmt.execute("""
                    CREATE FUNCTION ADD_NUMBERS(a INT, b INT)
                    RETURNS INT DETERMINISTIC
                    RETURN a + b
                    """);

            // Procedures
            stmt.execute("""
                    CREATE PROCEDURE INSERT_DEPT(IN p_id INT, IN p_name VARCHAR(100))
                    INSERT INTO DEPARTMENTS(DEPT_ID, DEPT_NAME) VALUES (p_id, p_name)
                    """);

            // Partitioned table — RANGE partitioning by year-from-date
            stmt.execute("""
                    CREATE TABLE SALES_BY_YEAR (
                        SALE_ID INT NOT NULL,
                        SALE_DATE DATE NOT NULL,
                        AMOUNT DECIMAL(10,2) NOT NULL,
                        PRIMARY KEY (SALE_ID, SALE_DATE)
                    )
                    PARTITION BY RANGE (YEAR(SALE_DATE)) (
                        PARTITION p2022 VALUES LESS THAN (2023),
                        PARTITION p2023 VALUES LESS THAN (2024),
                        PARTITION p2024 VALUES LESS THAN (2025),
                        PARTITION pmax  VALUES LESS THAN MAXVALUE
                    )
                    """);
        }
        provider = new MariaDbMetadataProvider();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        MARIADB.stop();
    }

    @Test
    void getAllTriggers_returnsTwoTriggers() throws SQLException {
        List<Trigger> triggers = provider.getAllTriggers(connection, null, DATABASE);
        assertThat(triggers).hasSize(2);
    }

    @Test
    void getAllTriggers_correctTimingAndEvent() throws SQLException {
        List<Trigger> triggers = provider.getAllTriggers(connection, null, DATABASE);

        Trigger empTrigger = findTrigger(triggers, "TRG_EMP_AUDIT");
        assertThat(empTrigger.timing()).isEqualTo(TriggerTiming.AFTER);
        assertThat(empTrigger.event()).isEqualTo(TriggerEvent.INSERT);

        Trigger ordTrigger = findTrigger(triggers, "TRG_ORD_AUDIT");
        assertThat(ordTrigger.timing()).isEqualTo(TriggerTiming.BEFORE);
        assertThat(ordTrigger.event()).isEqualTo(TriggerEvent.UPDATE);
    }

    @Test
    void getAllTriggers_correctTable() throws SQLException {
        List<Trigger> triggers = provider.getAllTriggers(connection, null, DATABASE);

        assertThat(findTrigger(triggers, "TRG_EMP_AUDIT").table().name()).isEqualToIgnoringCase("EMPLOYEES");
        assertThat(findTrigger(triggers, "TRG_ORD_AUDIT").table().name()).isEqualToIgnoringCase("ORDERS");
    }

    @Test
    void getAllTriggers_bodyContainsAction() throws SQLException {
        List<Trigger> triggers = provider.getAllTriggers(connection, null, DATABASE);

        for (Trigger trigger : triggers) {
            assertThat(trigger.body()).isPresent();
            assertThat(trigger.body().get()).containsIgnoringCase("AUDIT_LOG");
        }
    }

    @Test
    void getAllTriggers_orientationIsRow() throws SQLException {
        List<Trigger> triggers = provider.getAllTriggers(connection, null, DATABASE);

        for (Trigger trigger : triggers) {
            assertThat(trigger.orientation()).isPresent();
            assertThat(trigger.orientation().get()).isEqualToIgnoringCase("ROW");
        }
    }

    @Test
    void getTriggers_filtersByTable() throws SQLException {
        List<Trigger> empTriggers = provider.getTriggers(connection, null, DATABASE, "EMPLOYEES");
        assertThat(empTriggers).hasSize(1);
        assertThat(empTriggers.get(0).name()).isEqualToIgnoringCase("TRG_EMP_AUDIT");

        List<Trigger> ordTriggers = provider.getTriggers(connection, null, DATABASE, "ORDERS");
        assertThat(ordTriggers).hasSize(1);
        assertThat(ordTriggers.get(0).name()).isEqualToIgnoringCase("TRG_ORD_AUDIT");
    }

    @Test
    void getTriggers_emptyForTableWithNoTriggers() throws SQLException {
        List<Trigger> triggers = provider.getTriggers(connection, null, DATABASE, "DEPARTMENTS");
        assertThat(triggers).isEmpty();
    }

    @Test
    void getAllSequences_returnsAtLeastTwo() throws SQLException {
        List<Sequence> sequences = provider.getAllSequences(connection, null, DATABASE);
        assertThat(sequences).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void getAllSequences_seqOrderIdProperties() throws SQLException {
        List<Sequence> sequences = provider.getAllSequences(connection, null, DATABASE);
        Sequence seq = findSequence(sequences, "SEQ_ORDER_ID");

        assertThat(seq.startValue()).isEqualTo(1000L);
        assertThat(seq.incrementBy()).isEqualTo(1L);
        assertThat(seq.cycle()).isFalse();
    }

    @Test
    void getAllSequences_seqAuditIdProperties() throws SQLException {
        List<Sequence> sequences = provider.getAllSequences(connection, null, DATABASE);
        Sequence seq = findSequence(sequences, "SEQ_AUDIT_ID");

        assertThat(seq.startValue()).isEqualTo(1L);
        assertThat(seq.incrementBy()).isEqualTo(5L);
        assertThat(seq.minValue()).isPresent().hasValue(1L);
        assertThat(seq.maxValue()).isPresent().hasValue(999999L);
        assertThat(seq.cycle()).isTrue();
        assertThat(seq.cacheSize()).isPresent().hasValue(10L);
    }

    @Test
    void getAllSequences_schemaIsDatabase() throws SQLException {
        List<Sequence> sequences = provider.getAllSequences(connection, null, DATABASE);

        for (Sequence seq : sequences) {
            assertThat(seq.schema()).isPresent();
            assertThat(seq.schema().get().name()).isEqualTo(DATABASE);
        }
    }

    @Test
    void getAllCheckConstraints_returnsNamedConstraints() throws SQLException {
        List<CheckConstraint> constraints = provider.getAllCheckConstraints(connection, null, DATABASE);

        List<String> namedConstraints = constraints.stream().map(CheckConstraint::name)
                .filter(name -> name.startsWith("CK_")).toList();

        assertThat(namedConstraints).containsExactlyInAnyOrder("CK_DEPT_NAME_LEN", "CK_SALARY_POSITIVE", "CK_STATUS",
                "CK_QUANTITY", "CK_AMOUNT_POSITIVE");
    }

    @Test
    void getAllCheckConstraints_checkClauseContent() throws SQLException {
        List<CheckConstraint> constraints = provider.getAllCheckConstraints(connection, null, DATABASE);

        CheckConstraint salaryCheck = constraints.stream().filter(c -> "CK_SALARY_POSITIVE".equals(c.name()))
                .findFirst().orElseThrow();
        assertThat(salaryCheck.checkClause()).containsIgnoringCase("SALARY");

        CheckConstraint statusCheck = constraints.stream().filter(c -> "CK_STATUS".equals(c.name())).findFirst()
                .orElseThrow();
        assertThat(statusCheck.checkClause()).containsIgnoringCase("STATUS");
    }

    @Test
    void getAllCheckConstraints_tableAssociation() throws SQLException {
        List<CheckConstraint> constraints = provider.getAllCheckConstraints(connection, null, DATABASE);

        CheckConstraint salaryCheck = constraints.stream().filter(c -> "CK_SALARY_POSITIVE".equals(c.name()))
                .findFirst().orElseThrow();
        assertThat(salaryCheck.table().name()).isEqualToIgnoringCase("EMPLOYEES");

        CheckConstraint statusCheck = constraints.stream().filter(c -> "CK_STATUS".equals(c.name())).findFirst()
                .orElseThrow();
        assertThat(statusCheck.table().name()).isEqualToIgnoringCase("ORDERS");
    }

    @Test
    void getCheckConstraints_filtersByTable() throws SQLException {
        List<CheckConstraint> empConstraints = provider.getCheckConstraints(connection, null, DATABASE, "EMPLOYEES");

        List<String> namedEmp = empConstraints.stream().map(CheckConstraint::name)
                .filter(name -> name.startsWith("CK_")).toList();
        assertThat(namedEmp).containsExactly("CK_SALARY_POSITIVE");

        List<CheckConstraint> orderItemConstraints = provider.getCheckConstraints(connection, null, DATABASE,
                "ORDER_ITEMS");

        List<String> namedOI = orderItemConstraints.stream().map(CheckConstraint::name)
                .filter(name -> name.startsWith("CK_")).toList();
        assertThat(namedOI).containsExactlyInAnyOrder("CK_QUANTITY", "CK_AMOUNT_POSITIVE");
    }

    @Test
    void getAllUniqueConstraints_returnsAtLeastThree() throws SQLException {
        List<UniqueConstraint> constraints = provider.getAllUniqueConstraints(connection, null, DATABASE);
        assertThat(constraints).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void getAllUniqueConstraints_singleColumn() throws SQLException {
        List<UniqueConstraint> constraints = provider.getAllUniqueConstraints(connection, null, DATABASE);

        UniqueConstraint uqDeptName = constraints.stream().filter(c -> "UQ_DEPT_NAME".equals(c.name())).findFirst()
                .orElseThrow();
        assertThat(uqDeptName.table().name()).isEqualToIgnoringCase("DEPARTMENTS");
        assertThat(uqDeptName.columns()).hasSize(1);
        assertThat(uqDeptName.columns().get(0).name()).isEqualToIgnoringCase("DEPT_NAME");
    }

    @Test
    void getAllUniqueConstraints_multiColumn() throws SQLException {
        List<UniqueConstraint> constraints = provider.getAllUniqueConstraints(connection, null, DATABASE);

        UniqueConstraint uqAudit = constraints.stream().filter(c -> "UQ_AUDIT_UNIQUE".equals(c.name())).findFirst()
                .orElseThrow();
        assertThat(uqAudit.table().name()).isEqualToIgnoringCase("AUDIT_LOG");
        assertThat(uqAudit.columns()).hasSize(3);

        List<String> columnNames = uqAudit.columns().stream().map(ColumnReference::name).map(String::toUpperCase)
                .toList();
        assertThat(columnNames).containsExactly("TABLE_NAME", "ACTION_TYPE", "ACTION_TIMESTAMP");
    }

    @Test
    void getUniqueConstraints_filtersByTable() throws SQLException {
        List<UniqueConstraint> deptConstraints = provider.getUniqueConstraints(connection, null, DATABASE,
                "DEPARTMENTS");
        assertThat(deptConstraints).hasSize(1);
        assertThat(deptConstraints.get(0).name()).isEqualTo("UQ_DEPT_NAME");

        // In MariaDB, a UNIQUE INDEX is also represented as a unique constraint in
        // information_schema.TABLE_CONSTRAINTS, so ORDERS carries the IDX_ORD_EMP_DATE
        // unique index as a single unique constraint.
        List<UniqueConstraint> orderConstraints = provider.getUniqueConstraints(connection, null, DATABASE, "ORDERS");
        assertThat(orderConstraints).hasSize(1);
        assertThat(orderConstraints.get(0).name()).isEqualTo("IDX_ORD_EMP_DATE");
    }

    @Test
    void getAllPrimaryKeys_isPresent() throws SQLException {
        Optional<List<PrimaryKey>> result = provider.getAllPrimaryKeys(connection, null, DATABASE);
        assertThat(result).isPresent();
    }

    @Test
    void getAllPrimaryKeys_returnsSix() throws SQLException {
        List<PrimaryKey> primaryKeys = provider.getAllPrimaryKeys(connection, null, DATABASE).orElseThrow();
        assertThat(primaryKeys).hasSize(6);
    }

    @Test
    void getAllPrimaryKeys_simplePrimaryKey() throws SQLException {
        List<PrimaryKey> primaryKeys = provider.getAllPrimaryKeys(connection, null, DATABASE).orElseThrow();

        PrimaryKey deptPk = primaryKeys.stream().filter(pk -> "DEPARTMENTS".equalsIgnoreCase(pk.table().name()))
                .findFirst().orElseThrow();
        assertThat(deptPk.columns()).hasSize(1);
        assertThat(deptPk.columns().get(0).name()).isEqualToIgnoringCase("DEPT_ID");
    }

    @Test
    void getAllPrimaryKeys_compositePrimaryKey() throws SQLException {
        List<PrimaryKey> primaryKeys = provider.getAllPrimaryKeys(connection, null, DATABASE).orElseThrow();

        PrimaryKey oiPk = primaryKeys.stream().filter(pk -> "ORDER_ITEMS".equalsIgnoreCase(pk.table().name()))
                .findFirst().orElseThrow();
        assertThat(oiPk.columns()).hasSize(2);

        List<String> columnNames = oiPk.columns().stream().map(ColumnReference::name).map(String::toUpperCase).toList();
        assertThat(columnNames).containsExactly("ORDER_ID", "ITEM_SEQ");
    }

    @Test
    void getAllImportedKeys_isPresent() throws SQLException {
        Optional<List<ImportedKey>> result = provider.getAllImportedKeys(connection, null, DATABASE);
        assertThat(result).isPresent();
    }

    @Test
    void getAllImportedKeys_returnsThree() throws SQLException {
        List<ImportedKey> importedKeys = provider.getAllImportedKeys(connection, null, DATABASE).orElseThrow();
        assertThat(importedKeys).hasSize(3);
    }

    @Test
    void getAllImportedKeys_fkEmpDeptDetails() throws SQLException {
        List<ImportedKey> importedKeys = provider.getAllImportedKeys(connection, null, DATABASE).orElseThrow();

        ImportedKey fkEmpDept = importedKeys.stream().filter(fk -> "FK_EMP_DEPT".equals(fk.name())).findFirst()
                .orElseThrow();

        assertThat(fkEmpDept.foreignKeyColumn().name()).isEqualToIgnoringCase("DEPT_ID");
        assertThat(fkEmpDept.foreignKeyColumn().table()).isPresent();
        assertThat(fkEmpDept.foreignKeyColumn().table().get().name()).isEqualToIgnoringCase("EMPLOYEES");

        assertThat(fkEmpDept.primaryKeyColumn().name()).isEqualToIgnoringCase("DEPT_ID");
        assertThat(fkEmpDept.primaryKeyColumn().table()).isPresent();
        assertThat(fkEmpDept.primaryKeyColumn().table().get().name()).isEqualToIgnoringCase("DEPARTMENTS");

        assertThat(fkEmpDept.deleteRule()).isEqualTo(ImportedKey.ReferentialAction.SET_NULL);
        assertThat(fkEmpDept.updateRule()).isEqualTo(ImportedKey.ReferentialAction.CASCADE);
    }

    @Test
    void getAllImportedKeys_keySequence() throws SQLException {
        List<ImportedKey> importedKeys = provider.getAllImportedKeys(connection, null, DATABASE).orElseThrow();

        for (ImportedKey fk : importedKeys) {
            assertThat(fk.keySequence()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void getAllIndexInfo_isPresent() throws SQLException {
        Optional<List<IndexInfo>> result = provider.getAllIndexInfo(connection, null, DATABASE);
        assertThat(result).isPresent();
    }

    @Test
    void getAllIndexInfo_containsUserIndexes() throws SQLException {
        List<IndexInfo> indexInfos = provider.getAllIndexInfo(connection, null, DATABASE).orElseThrow();

        List<String> allIndexNames = indexInfos.stream().flatMap(ii -> ii.indexInfoItems().stream())
                .map(IndexInfoItem::indexName).filter(Optional::isPresent).map(Optional::get).toList();

        assertThat(allIndexNames).contains("IDX_EMP_LAST_NAME", "IDX_EMP_DEPT", "IDX_ORD_DATE", "IDX_ORD_EMP_DATE");
    }

    @Test
    void getAllIndexInfo_nonUniqueIndex() throws SQLException {
        List<IndexInfo> indexInfos = provider.getAllIndexInfo(connection, null, DATABASE).orElseThrow();

        IndexInfoItem lastNameIdx = findIndexItem(indexInfos, "IDX_EMP_LAST_NAME");
        assertThat(lastNameIdx.unique()).isFalse();
        assertThat(lastNameIdx.column()).isPresent();
        assertThat(lastNameIdx.column().get().name()).isEqualToIgnoringCase("LAST_NAME");
    }

    @Test
    void getAllIndexInfo_uniqueIndex() throws SQLException {
        List<IndexInfo> indexInfos = provider.getAllIndexInfo(connection, null, DATABASE).orElseThrow();

        List<IndexInfoItem> empDateItems = indexInfos.stream().flatMap(ii -> ii.indexInfoItems().stream())
                .filter(item -> item.indexName().isPresent() && "IDX_ORD_EMP_DATE".equals(item.indexName().get()))
                .toList();

        assertThat(empDateItems).isNotEmpty();
        assertThat(empDateItems.get(0).unique()).isTrue();
    }

    @Test
    void getAllIndexInfo_multiColumnIndex() throws SQLException {
        List<IndexInfo> indexInfos = provider.getAllIndexInfo(connection, null, DATABASE).orElseThrow();

        List<IndexInfoItem> empDateItems = indexInfos.stream().flatMap(ii -> ii.indexInfoItems().stream())
                .filter(item -> item.indexName().isPresent() && "IDX_ORD_EMP_DATE".equals(item.indexName().get()))
                .toList();

        assertThat(empDateItems).hasSize(2);

        List<String> columnNames = empDateItems.stream().map(item -> item.column().map(c -> c.name()).orElse(""))
                .map(String::toUpperCase).toList();
        assertThat(columnNames).containsExactly("EMP_ID", "ORDER_DATE");
    }

    @Test
    void getAllViewDefinitions_returnsTwo() throws SQLException {
        List<ViewDefinition> views = provider.getAllViewDefinitions(connection, null, DATABASE);
        assertThat(views).hasSize(2);
    }

    @Test
    void getAllViewDefinitions_bodyIsPresent() throws SQLException {
        List<ViewDefinition> views = provider.getAllViewDefinitions(connection, null, DATABASE);

        for (ViewDefinition view : views) {
            assertThat(view.viewBody()).isPresent();
            assertThat(view.viewBody().get()).isNotBlank();
        }
    }

    @Test
    void getAllViewDefinitions_bodyContainsSelect() throws SQLException {
        List<ViewDefinition> views = provider.getAllViewDefinitions(connection, null, DATABASE);

        for (ViewDefinition view : views) {
            assertThat(view.viewBody().get()).containsIgnoringCase("SELECT");
        }
    }

    @Test
    void getAllViewDefinitions_viewType() throws SQLException {
        List<ViewDefinition> views = provider.getAllViewDefinitions(connection, null, DATABASE);

        List<String> viewNames = views.stream().map(v -> v.view().name().toUpperCase()).toList();
        assertThat(viewNames).containsExactlyInAnyOrder("V_EMP_DEPT", "V_ORDER_SUMMARY");

        for (ViewDefinition view : views) {
            assertThat(view.view().type()).isEqualTo("VIEW");
        }
    }

    @Test
    void getAllProcedures_returnsInsertDept() throws SQLException {
        List<Procedure> procedures = provider.getAllProcedures(connection, null, DATABASE);
        assertThat(procedures).anyMatch(p -> "INSERT_DEPT".equalsIgnoreCase(p.reference().name()));
    }

    @Test
    void getAllProcedures_bodyIsPresent() throws SQLException {
        List<Procedure> procedures = provider.getAllProcedures(connection, null, DATABASE);
        Procedure insertDept = procedures.stream().filter(p -> "INSERT_DEPT".equalsIgnoreCase(p.reference().name()))
                .findFirst().orElseThrow();
        assertThat(insertDept.body()).isPresent();
        assertThat(insertDept.body().get()).containsIgnoringCase("DEPARTMENTS");
    }

    @Test
    void getAllProcedures_fullDefinitionIsPresent() throws SQLException {
        List<Procedure> procedures = provider.getAllProcedures(connection, null, DATABASE);
        Procedure insertDept = procedures.stream().filter(p -> "INSERT_DEPT".equalsIgnoreCase(p.reference().name()))
                .findFirst().orElseThrow();
        assertThat(insertDept.fullDefinition()).isPresent();
        assertThat(insertDept.fullDefinition().get()).containsIgnoringCase("CREATE");
    }

    @Test
    void getAllFunctions_containsDefinedFunctions() throws SQLException {
        List<Function> functions = provider.getAllFunctions(connection, null, DATABASE);
        List<String> names = functions.stream().map(f -> f.reference().name().toUpperCase()).toList();
        assertThat(names).contains("TO_UPPER", "ADD_NUMBERS");
    }

    @Test
    void getAllFunctions_addNumbersHasParameters() throws SQLException {
        List<Function> functions = provider.getAllFunctions(connection, null, DATABASE);
        Function addNumbers = functions.stream().filter(f -> "ADD_NUMBERS".equalsIgnoreCase(f.reference().name()))
                .findFirst().orElseThrow();
        assertThat(addNumbers.columns()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void getAllFunctions_bodyIsPresent() throws SQLException {
        List<Function> functions = provider.getAllFunctions(connection, null, DATABASE);
        Function addNumbers = functions.stream().filter(f -> "ADD_NUMBERS".equalsIgnoreCase(f.reference().name()))
                .findFirst().orElseThrow();
        assertThat(addNumbers.body()).isPresent();
        assertThat(addNumbers.body().get()).containsIgnoringCase("RETURN");
    }

    @Test
    void getAllUserDefinedTypes_returnsEmpty() throws SQLException {
        List<UserDefinedType> udts = provider.getAllUserDefinedTypes(connection, null, DATABASE);
        assertThat(udts).isEmpty();
    }

    @Test
    void getAllPartitions_returnsFourPartitions() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, DATABASE);
        assertThat(partitions).hasSize(4);
        assertThat(partitions).allMatch(p -> "SALES_BY_YEAR".equalsIgnoreCase(p.table().name()));
    }

    @Test
    void getAllPartitions_methodIsRange() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, DATABASE);
        assertThat(partitions).allMatch(p -> p.method() == PartitionMethod.RANGE);
    }

    @Test
    void getAllPartitions_orderingByOrdinalPosition() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, DATABASE);
        assertThat(partitions).extracting(Partition::name).containsExactly("p2022", "p2023", "p2024", "pmax");
    }

    @Test
    void getAllPartitions_descriptionsCarryBoundaries() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, DATABASE);
        // MariaDB reports boundaries as "2023", "2024", "2025", "MAXVALUE" respectively
        assertThat(findPartition(partitions, "p2022").description()).contains("2023");
        assertThat(findPartition(partitions, "pmax").description()).contains("MAXVALUE");
    }

    @Test
    void getAllPartitions_expressionIsPartitionKey() throws SQLException {
        List<Partition> partitions = provider.getAllPartitions(connection, null, DATABASE);
        // every partition reports the same partitioning expression
        assertThat(partitions).allMatch(p -> p.expression().isPresent()
                && p.expression().get().toUpperCase(java.util.Locale.ROOT).contains("YEAR"));
    }

    @Test
    void getPartitions_perTableMatchesAll() throws SQLException {
        List<Partition> all = provider.getAllPartitions(connection, null, DATABASE);
        List<Partition> sales = provider.getPartitions(connection, null, DATABASE, "SALES_BY_YEAR");
        assertThat(sales).hasSameSizeAs(all);
    }

    @Test
    void getPartitions_returnsEmptyForUnpartitionedTable() throws SQLException {
        List<Partition> employees = provider.getPartitions(connection, null, DATABASE, "EMPLOYEES");
        assertThat(employees).isEmpty();
    }

    private static Partition findPartition(List<Partition> partitions, String name) {
        return partitions.stream().filter(p -> name.equalsIgnoreCase(p.name())).findFirst()
                .orElseThrow(() -> new AssertionError("Partition not found: " + name));
    }

    private static Trigger findTrigger(List<Trigger> triggers, String name) {
        return triggers.stream().filter(t -> name.equalsIgnoreCase(t.name())).findFirst()
                .orElseThrow(() -> new AssertionError("Trigger not found: " + name));
    }

    private static Sequence findSequence(List<Sequence> sequences, String name) {
        return sequences.stream().filter(s -> name.equalsIgnoreCase(s.name())).findFirst()
                .orElseThrow(() -> new AssertionError("Sequence not found: " + name));
    }

    private static IndexInfoItem findIndexItem(List<IndexInfo> indexInfos, String indexName) {
        return indexInfos.stream().flatMap(ii -> ii.indexInfoItems().stream())
                .filter(item -> item.indexName().isPresent() && indexName.equalsIgnoreCase(item.indexName().get()))
                .findFirst().orElseThrow(() -> new AssertionError("Index not found: " + indexName));
    }
}
