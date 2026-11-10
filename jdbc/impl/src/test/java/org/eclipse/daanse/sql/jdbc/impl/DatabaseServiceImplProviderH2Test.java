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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DatabaseServiceImplProviderH2Test {

    private static Connection connection;
    private static H2Dialect dialect;
    private static MetaInfo metaInfo;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:coreProviderTest;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = connection.createStatement()) {
            // Sequences
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

            // Triggers (H2 uses CALL "class.name" -- TestAuditTrigger is in dialect module)
            // Note: We use a simple Java class name that can be resolved from classpath
            stmt.execute("""
                    CREATE TRIGGER TRG_EMP_AUDIT AFTER INSERT ON EMPLOYEES
                    FOR EACH ROW CALL "org.eclipse.daanse.sql.jdbc.impl.CoreTestAuditTrigger"
                    """);

            // Functions (H2 uses CREATE ALIAS)
            stmt.execute("""
                    CREATE ALIAS CALC_BONUS AS $$
                    double calcBonus(double salary, int years) {
                        return salary * years * 0.01;
                    }
                    $$
                    """);
        }

        dialect = new H2Dialect(org.eclipse.daanse.sql.dialect.api.DialectInitData.fromConnection(connection));
        DatabaseServiceImpl service = new DatabaseServiceImpl();
        metaInfo = service.createMetaInfo(connection, new org.eclipse.daanse.sql.jdbc.metadata.H2MetadataProvider());
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
    void structureInfo_containsTriggers() {
        StructureInfo si = metaInfo.structureInfo();
        assertThat(si.triggers()).isNotEmpty();
        assertThat(si.triggers()).anyMatch(t -> "TRG_EMP_AUDIT".equals(t.name()));
    }

    @Test
    void structureInfo_containsSequences() {
        StructureInfo si = metaInfo.structureInfo();
        assertThat(si.sequences()).isNotEmpty();
        assertThat(si.sequences()).anyMatch(s -> "SEQ_ORDER_ID".equals(s.name()));
        assertThat(si.sequences()).anyMatch(s -> "SEQ_AUDIT_ID".equals(s.name()));
    }

    @Test
    void structureInfo_containsCheckConstraints() {
        StructureInfo si = metaInfo.structureInfo();
        assertThat(si.checkConstraints()).isNotEmpty();
        assertThat(si.checkConstraints()).anyMatch(c -> "CK_SALARY_POSITIVE".equals(c.name()));
        assertThat(si.checkConstraints()).anyMatch(c -> "CK_STATUS".equals(c.name()));
    }

    @Test
    void structureInfo_containsUniqueConstraints() {
        StructureInfo si = metaInfo.structureInfo();
        assertThat(si.uniqueConstraints()).isNotEmpty();
        assertThat(si.uniqueConstraints()).anyMatch(c -> "UQ_DEPT_NAME".equals(c.name()));
        assertThat(si.uniqueConstraints()).anyMatch(c -> "UQ_EMP_EMAIL".equals(c.name()));
        assertThat(si.uniqueConstraints()).anyMatch(c -> "UQ_AUDIT_UNIQUE".equals(c.name()));
    }

    @Test
    void structureInfo_containsViewDefinitions() {
        StructureInfo si = metaInfo.structureInfo();
        assertThat(si.viewDefinitions()).isNotEmpty();
        assertThat(si.viewDefinitions()).anyMatch(v -> "V_EMP_DEPT".equals(v.view().name()));
        assertThat(si.viewDefinitions()).anyMatch(v -> "V_ORDER_SUMMARY".equals(v.view().name()));
    }

    @Test
    void primaryKeys_notEmpty() {
        StructureInfo si = metaInfo.structureInfo();
        assertThat(si.primaryKeys()).isNotEmpty();
        assertThat(si.primaryKeys()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void importedKeys_notEmpty() {
        StructureInfo si = metaInfo.structureInfo();
        assertThat(si.importedKeys()).isNotEmpty();
        assertThat(si.importedKeys()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void indexInfo_notEmpty() {
        assertThat(metaInfo.indexInfos()).isNotEmpty();
    }

    @Test
    void standardMetadata_populated() {
        assertThat(metaInfo.databaseInfo()).isNotNull();
        assertThat(metaInfo.databaseInfo().databaseProductName()).isEqualTo("H2");
        assertThat(metaInfo.identifierInfo()).isNotNull();
        assertThat(metaInfo.typeInfos()).isNotEmpty();
    }

    @Test
    void structureInfo_containsFunctions() {
        StructureInfo si = metaInfo.structureInfo();
        assertThat(si.functions()).isNotEmpty();
        assertThat(si.functions()).anyMatch(f -> "CALC_BONUS".equals(f.reference().name()));
    }

    @Test
    void structureInfo_proceduresNotNull() {
        StructureInfo si = metaInfo.structureInfo();
        assertThat(si.procedures()).isNotNull();
    }

    @Test
    void userDefinedTypes_empty() {
        StructureInfo si = metaInfo.structureInfo();
        assertThat(si.userDefinedTypes()).isEmpty();
    }
}
