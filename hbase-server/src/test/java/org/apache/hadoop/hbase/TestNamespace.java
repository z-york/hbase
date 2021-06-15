/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.master.HMaster;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.collect.Sets;

@Category({MiscTests.class, MediumTests.class})
public class TestNamespace {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
      HBaseClassTestRule.forClass(TestNamespace.class);

  private static final Logger LOG = LoggerFactory.getLogger(TestNamespace.class);
  private static HMaster master;
  protected final static int NUM_SLAVES_BASE = 4;
  private static HBaseTestingUtility TEST_UTIL;
  protected static Admin admin;
  protected static HBaseCluster cluster;
  private String prefix = "TestNamespace";

  @Rule
  public TestName name = new TestName();

  @BeforeClass
  public static void setUp() throws Exception {
    TEST_UTIL = new HBaseTestingUtility();
    TEST_UTIL.startMiniCluster(NUM_SLAVES_BASE);
    admin = TEST_UTIL.getAdmin();
    cluster = TEST_UTIL.getHBaseCluster();
    master = ((MiniHBaseCluster)cluster).getMaster();
    LOG.info("Done initializing cluster");
  }

  @AfterClass
  public static void tearDown() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void beforeMethod() throws IOException {
    for (TableDescriptor desc : admin.listTableDescriptors(Pattern.compile(prefix + ".*"))) {
      admin.disableTable(desc.getTableName());
      admin.deleteTable(desc.getTableName());
    }
    for (NamespaceDescriptor ns : admin.listNamespaceDescriptors()) {
      if (ns.getName().startsWith(prefix)) {
        admin.deleteNamespace(ns.getName());
      }
    }
  }

  @Test
  public void verifyReservedNS() throws IOException {
    //verify existence of reserved namespaces
    NamespaceDescriptor ns =
        admin.getNamespaceDescriptor(NamespaceDescriptor.DEFAULT_NAMESPACE.getName());
    assertNotNull(ns);
    assertEquals(ns.getName(), NamespaceDescriptor.DEFAULT_NAMESPACE.getName());

    ns = admin.getNamespaceDescriptor(NamespaceDescriptor.SYSTEM_NAMESPACE.getName());
    assertNotNull(ns);
    assertEquals(ns.getName(), NamespaceDescriptor.SYSTEM_NAMESPACE.getName());

    assertEquals(2, admin.listNamespaces().length);
    assertEquals(2, admin.listNamespaceDescriptors().length);

    //verify existence of system tables
    Set<TableName> systemTables = Sets.newHashSet(TableName.META_TABLE_NAME);
    List<TableDescriptor> descs = admin.listTableDescriptorsByNamespace(
      Bytes.toBytes(NamespaceDescriptor.SYSTEM_NAMESPACE.getName()));
    assertEquals(systemTables.size(), descs.size());
    for (TableDescriptor desc : descs) {
      assertTrue(systemTables.contains(desc.getTableName()));
    }
    //verify system tables aren't listed
    assertEquals(0, admin.listTableDescriptors().size());

    //Try creating default and system namespaces.
    boolean exceptionCaught = false;
    try {
      admin.createNamespace(NamespaceDescriptor.DEFAULT_NAMESPACE);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }

    exceptionCaught = false;
    try {
      admin.createNamespace(NamespaceDescriptor.SYSTEM_NAMESPACE);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }
  }

  @Test
  public void testDeleteReservedNS() throws Exception {
    boolean exceptionCaught = false;
    try {
      admin.deleteNamespace(NamespaceDescriptor.DEFAULT_NAMESPACE_NAME_STR);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }

    try {
      admin.deleteNamespace(NamespaceDescriptor.SYSTEM_NAMESPACE_NAME_STR);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }

    try {
      admin.deleteNamespace(NamespaceDescriptor.MASTER_NAMESPACE_NAME_STR);
    } catch (IOException exp) {
      LOG.warn(exp.toString(), exp);
      exceptionCaught = true;
    } finally {
      assertTrue(exceptionCaught);
    }
  }

  @Test
  public void createRemoveTest() throws Exception {
    String nsName = prefix + "_" + name.getMethodName();
    LOG.info(name.getMethodName());

    //create namespace and verify
    admin.createNamespace(NamespaceDescriptor.create(nsName).build());
    assertEquals(3, admin.listNamespaces().length);
    assertEquals(3, admin.listNamespaceDescriptors().length);
    //remove namespace and verify
    admin.deleteNamespace(nsName);
    assertEquals(2, admin.listNamespaces().length);
    assertEquals(2, admin.listNamespaceDescriptors().length);
  }

  @Test
  public void createDoubleTest() throws IOException, InterruptedException {
    String nsName = prefix + "_" + name.getMethodName();
    LOG.info(name.getMethodName());

    final TableName tableName = TableName.valueOf(name.getMethodName());
    final TableName tableNameFoo = TableName.valueOf(nsName + ":" + name.getMethodName());
    //create namespace and verify
    admin.createNamespace(NamespaceDescriptor.create(nsName).build());
    TEST_UTIL.createTable(tableName, Bytes.toBytes(nsName));
    TEST_UTIL.createTable(tableNameFoo,Bytes.toBytes(nsName));
    assertEquals(2, admin.listTableDescriptors().size());
    assertNotNull(admin
        .getDescriptor(tableName));
    assertNotNull(admin
        .getDescriptor(tableNameFoo));
    //remove namespace and verify
    admin.disableTable(tableName);
    admin.deleteTable(tableName);
    assertEquals(1, admin.listTableDescriptors().size());
  }

  @Test
  public void createTableTest() throws IOException, InterruptedException {
    String nsName = prefix + "_" + name.getMethodName();
    LOG.info(name.getMethodName());

    TableDescriptorBuilder tableDescriptorBuilder =
      TableDescriptorBuilder.newBuilder(TableName.valueOf(nsName + ":" + name.getMethodName()));
    ColumnFamilyDescriptor columnFamilyDescriptor =
      ColumnFamilyDescriptorBuilder.newBuilder(Bytes.toBytes("my_cf")).build();
    tableDescriptorBuilder.setColumnFamily(columnFamilyDescriptor);
    TableDescriptor tableDescriptor = tableDescriptorBuilder.build();
    try {
      admin.createTable(tableDescriptor);
      fail("Expected no namespace exists exception");
    } catch (NamespaceNotFoundException ex) {
    }
    //create table and in new namespace
    admin.createNamespace(NamespaceDescriptor.create(nsName).build());
    admin.createTable(tableDescriptor);
    TEST_UTIL.waitTableAvailable(tableDescriptor.getTableName().getName(), 10000);
    FileSystem fs = FileSystem.get(TEST_UTIL.getConfiguration());
    assertTrue(fs.exists(
        new Path(master.getMasterFileSystem().getRootDir(),
            new Path(HConstants.BASE_NAMESPACE_DIR,
                new Path(nsName, tableDescriptor.getTableName().getQualifierAsString())))));
    assertEquals(1, admin.listTableDescriptors().size());

    //verify non-empty namespace can't be removed
    try {
      admin.deleteNamespace(nsName);
      fail("Expected non-empty namespace constraint exception");
    } catch (Exception ex) {
      LOG.info("Caught expected exception: " + ex);
    }

    //sanity check try to write and read from table
    Table table = TEST_UTIL.getConnection().getTable(tableDescriptor.getTableName());
    Put p = new Put(Bytes.toBytes("row1"));
    p.addColumn(Bytes.toBytes("my_cf"), Bytes.toBytes("my_col"), Bytes.toBytes("value1"));
    table.put(p);
    //flush and read from disk to make sure directory changes are working
    admin.flush(tableDescriptor.getTableName());
    Get g = new Get(Bytes.toBytes("row1"));
    assertTrue(table.exists(g));

    //normal case of removing namespace
    TEST_UTIL.deleteTable(tableDescriptor.getTableName());
    admin.deleteNamespace(nsName);
  }

  @Test
  public void createTableInDefaultNamespace() throws Exception {
    TableDescriptorBuilder tableDescriptorBuilder =
      TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()));
    ColumnFamilyDescriptor columnFamilyDescriptor =
      ColumnFamilyDescriptorBuilder.newBuilder(Bytes.toBytes("cf1")).build();
    tableDescriptorBuilder.setColumnFamily(columnFamilyDescriptor);
    TableDescriptor tableDescriptor = tableDescriptorBuilder.build();
    admin.createTable(tableDescriptor);
    assertTrue(admin.listTableDescriptors().size() == 1);
    admin.disableTable(tableDescriptor.getTableName());
    admin.deleteTable(tableDescriptor.getTableName());
  }

  @Test
  public void createTableInSystemNamespace() throws Exception {
    final TableName tableName = TableName.valueOf("hbase:" + name.getMethodName());
    TableDescriptorBuilder tableDescriptorBuilder =
      TableDescriptorBuilder.newBuilder(tableName);
    ColumnFamilyDescriptor columnFamilyDescriptor =
      ColumnFamilyDescriptorBuilder.newBuilder(Bytes.toBytes("cf1")).build();
    tableDescriptorBuilder.setColumnFamily(columnFamilyDescriptor);
    TableDescriptor tableDescriptor = tableDescriptorBuilder.build();
    admin.createTable(tableDescriptor);
    assertEquals(0, admin.listTableDescriptors().size());
    assertTrue(admin.tableExists(tableName));
    admin.disableTable(tableDescriptor.getTableName());
    admin.deleteTable(tableDescriptor.getTableName());
  }

  @Test
  public void testNamespaceOperations() throws IOException {
    admin.createNamespace(NamespaceDescriptor.create(prefix + "ns1").build());
    admin.createNamespace(NamespaceDescriptor.create(prefix + "ns2").build());

    // create namespace that already exists
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.createNamespace(NamespaceDescriptor.create(prefix + "ns1").build());
        return null;
      }
    }, NamespaceExistException.class);

    // create a table in non-existing namespace
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        TableDescriptorBuilder tableDescriptorBuilder = TableDescriptorBuilder
          .newBuilder(TableName.valueOf("non_existing_namespace", name.getMethodName()));
        ColumnFamilyDescriptor columnFamilyDescriptor =
          ColumnFamilyDescriptorBuilder.newBuilder(Bytes.toBytes("family1")).build();
        tableDescriptorBuilder.setColumnFamily(columnFamilyDescriptor);
        admin.createTable(tableDescriptorBuilder.build());
        return null;
      }
    }, NamespaceNotFoundException.class);

    // get descriptor for existing namespace
    admin.getNamespaceDescriptor(prefix + "ns1");

    // get descriptor for non-existing namespace
    runWithExpectedException(new Callable<NamespaceDescriptor>() {
      @Override
      public NamespaceDescriptor call() throws Exception {
        return admin.getNamespaceDescriptor("non_existing_namespace");
      }
    }, NamespaceNotFoundException.class);

    // delete descriptor for existing namespace
    admin.deleteNamespace(prefix + "ns2");

    // delete descriptor for non-existing namespace
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.deleteNamespace("non_existing_namespace");
        return null;
      }
    }, NamespaceNotFoundException.class);

    // modify namespace descriptor for existing namespace
    NamespaceDescriptor ns1 = admin.getNamespaceDescriptor(prefix + "ns1");
    ns1.setConfiguration("foo", "bar");
    admin.modifyNamespace(ns1);

    // modify namespace descriptor for non-existing namespace
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.modifyNamespace(NamespaceDescriptor.create("non_existing_namespace").build());
        return null;
      }
    }, NamespaceNotFoundException.class);

    // get table descriptors for existing namespace
    TableDescriptorBuilder tableDescriptorBuilder =
      TableDescriptorBuilder.newBuilder(TableName.valueOf(prefix + "ns1", name.getMethodName()));
    ColumnFamilyDescriptor columnFamilyDescriptor =
      ColumnFamilyDescriptorBuilder.newBuilder(Bytes.toBytes("family1")).build();
    tableDescriptorBuilder.setColumnFamily(columnFamilyDescriptor);
    admin.createTable(tableDescriptorBuilder.build());
    List<TableDescriptor> htds =
      admin.listTableDescriptorsByNamespace(Bytes.toBytes(prefix + "ns1"));
    assertNotNull("Should have not returned null", htds);
    assertEquals("Should have returned non-empty array", 1, htds.size());

    // get table descriptors for non-existing namespace
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.listTableDescriptorsByNamespace(Bytes.toBytes("non_existant_namespace"));
        return null;
      }
    }, NamespaceNotFoundException.class);

    // get table names for existing namespace
    TableName[] tableNames = admin.listTableNamesByNamespace(prefix + "ns1");
    assertNotNull("Should have not returned null", tableNames);
    assertEquals("Should have returned non-empty array", 1, tableNames.length);

    // get table names for non-existing namespace
    runWithExpectedException(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        admin.listTableNamesByNamespace("non_existing_namespace");
        return null;
      }
    }, NamespaceNotFoundException.class);

  }

  private static <V, E> void runWithExpectedException(Callable<V> callable, Class<E> exceptionClass) {
    try {
      callable.call();
    } catch(Exception ex) {
      Assert.assertEquals(exceptionClass, ex.getClass());
      return;
    }
    fail("Should have thrown exception " + exceptionClass);
  }
}
