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
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.DatabaseInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IdentifierInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatabaseServiceImplMocksTest {

    private DatabaseService databaseService = new DatabaseServiceImpl();

    @Mock
    DataSource ds;
    @Mock
    Connection connection;
    @Mock
    DatabaseMetaData databaseMetaData;
    @Mock
    ResultSet resultSet;

    @Mock
    ResultSetMetaData resultSetMetaData;

    @Test
    void createMetaDataTest() throws SQLException {

        when(ds.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);

        when(databaseMetaData.getIdentifierQuoteString()).thenReturn("\"");
        when(databaseMetaData.getDatabaseMajorVersion()).thenReturn(42);
        when(databaseMetaData.getDatabaseMinorVersion()).thenReturn(21);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MyDB");
        when(databaseMetaData.getDatabaseProductVersion()).thenReturn("a");
        when(databaseMetaData.getTypeInfo()).thenReturn(resultSet);
        when(databaseMetaData.getCatalogs()).thenReturn(resultSet);

        when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
        when(resultSetMetaData.getColumnCount()).thenReturn(6);

        when(resultSetMetaData.getColumnName(1)).thenReturn("TABLE_CAT");
        when(resultSetMetaData.getColumnName(2)).thenReturn("TABLE_SCHEM");
        when(resultSetMetaData.getColumnName(3)).thenReturn("TABLE_NAME");
        when(resultSetMetaData.getColumnName(4)).thenReturn("TABLE_TYPE");
        when(resultSetMetaData.getColumnName(5)).thenReturn("REMARKS");
        when(resultSetMetaData.getColumnName(6)).thenReturn("TYPE_CAT");
//        when(databaseMetaData.getSchemas()).thenReturn(resultSet);
        when(databaseMetaData.getSchemas(Mockito.any(), Mockito.any())).thenReturn(resultSet);
        when(databaseMetaData.getTables(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(resultSet);
        when(databaseMetaData.getColumns(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(resultSet);
//        when(databaseMetaData.getImportedKeys(Mockito.any(),Mockito.any(),Mockito.any())).thenReturn(resultSet);

        when(resultSet.next()).thenReturn(false);

        MetaInfo metaInfo = databaseService.createMetaInfo(ds);

        assertThat(metaInfo).isNotNull();

        DatabaseInfo databaseInfo = metaInfo.databaseInfo();
        assertThat(databaseInfo).isNotNull();
        assertThat(databaseInfo.databaseProductName()).isEqualTo("MyDB");
        assertThat(databaseInfo.databaseProductVersion()).isEqualTo("a");
        assertThat(databaseInfo.databaseMajorVersion()).isEqualTo(42);
        assertThat(databaseInfo.databaseMinorVersion()).isEqualTo(21);

        IdentifierInfo identifierInfo = metaInfo.identifierInfo();
        assertThat(identifierInfo).isNotNull();
        assertThat(identifierInfo.quoteString()).isEqualTo("\"");
    }

}
