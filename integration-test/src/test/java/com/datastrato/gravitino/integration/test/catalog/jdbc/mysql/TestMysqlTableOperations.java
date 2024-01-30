/*
 * Copyright 2023 Datastrato Pvt Ltd.
 * This software is licensed under the Apache License version 2.
 */
package com.datastrato.gravitino.integration.test.catalog.jdbc.mysql;

import static com.datastrato.gravitino.catalog.mysql.MysqlTablePropertiesMetadata.MYSQL_AUTO_INCREMENT_OFFSET_KEY;
import static com.datastrato.gravitino.catalog.mysql.MysqlTablePropertiesMetadata.MYSQL_ENGINE_KEY;

import com.datastrato.gravitino.catalog.jdbc.JdbcColumn;
import com.datastrato.gravitino.catalog.jdbc.JdbcTable;
import com.datastrato.gravitino.catalog.mysql.operation.MysqlTableOperations;
import com.datastrato.gravitino.exceptions.GravitinoRuntimeException;
import com.datastrato.gravitino.exceptions.NoSuchTableException;
import com.datastrato.gravitino.integration.test.util.GravitinoITUtils;
import com.datastrato.gravitino.rel.TableChange;
import com.datastrato.gravitino.rel.indexes.Index;
import com.datastrato.gravitino.rel.indexes.Indexes;
import com.datastrato.gravitino.rel.types.Type;
import com.datastrato.gravitino.rel.types.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("gravitino-docker-it")
public class TestMysqlTableOperations extends TestMysqlAbstractIT {

  private static Type VARCHAR = Types.VarCharType.of(255);
  private static Type INT = Types.IntegerType.get();

  @Test
  public void testOperationTable() {
    String tableName = RandomUtils.nextInt(10000) + "_op_table";
    String tableComment = "test_comment";
    List<JdbcColumn> columns = new ArrayList<>();
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_1")
            .withType(VARCHAR)
            .withComment("test_comment")
            .withNullable(true)
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_2")
            .withType(INT)
            .withNullable(false)
            .withComment("set primary key")
            .build());
    columns.add(
        new JdbcColumn.Builder().withName("col_3").withType(INT).withNullable(true).build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_4")
            .withType(VARCHAR)
            // TODO: uncomment this after supporting default values
            // .withDefaultValue("hello world")
            .withNullable(false)
            .build());
    Map<String, String> properties = new HashMap<>();
    properties.put(MYSQL_AUTO_INCREMENT_OFFSET_KEY, "10");

    Index[] indexes = new Index[] {Indexes.unique("test", new String[][] {{"col_1"}, {"col_2"}})};
    // create table
    TABLE_OPERATIONS.create(
        TEST_DB_NAME,
        tableName,
        columns.toArray(new JdbcColumn[0]),
        tableComment,
        properties,
        null,
        indexes);

    // list table
    List<String> tables = TABLE_OPERATIONS.listTables(TEST_DB_NAME);
    Assertions.assertTrue(tables.contains(tableName));

    // load table
    JdbcTable load = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);
    assertionsTableInfo(tableName, tableComment, columns, properties, indexes, load);

    // rename table
    String newName = "new_table";
    Assertions.assertDoesNotThrow(() -> TABLE_OPERATIONS.rename(TEST_DB_NAME, tableName, newName));
    Assertions.assertDoesNotThrow(() -> TABLE_OPERATIONS.load(TEST_DB_NAME, newName));

    // alter table
    JdbcColumn newColumn =
        new JdbcColumn.Builder()
            .withName("col_5")
            .withType(VARCHAR)
            .withComment("new_add")
            .withNullable(true)
            .build();
    TABLE_OPERATIONS.alterTable(
        TEST_DB_NAME,
        newName,
        TableChange.addColumn(
            new String[] {newColumn.name()},
            newColumn.dataType(),
            newColumn.comment(),
            TableChange.ColumnPosition.after("col_1")),
        TableChange.setProperty(MYSQL_ENGINE_KEY, "InnoDB"));
    properties.put(MYSQL_ENGINE_KEY, "InnoDB");
    load = TABLE_OPERATIONS.load(TEST_DB_NAME, newName);
    List<JdbcColumn> alterColumns =
        new ArrayList<JdbcColumn>() {
          {
            add(columns.get(0));
            add(newColumn);
            add(columns.get(1));
            add(columns.get(2));
            add(columns.get(3));
          }
        };
    assertionsTableInfo(newName, tableComment, alterColumns, properties, indexes, load);

    // Detect unsupported properties
    GravitinoRuntimeException gravitinoRuntimeException =
        Assertions.assertThrows(
            GravitinoRuntimeException.class,
            () ->
                TABLE_OPERATIONS.alterTable(
                    TEST_DB_NAME, newName, TableChange.setProperty(MYSQL_ENGINE_KEY, "ABC")));
    Assertions.assertTrue(
        StringUtils.contains(
            gravitinoRuntimeException.getMessage(), "Unknown storage engine 'ABC'"));

    // delete column
    TABLE_OPERATIONS.alterTable(
        TEST_DB_NAME, newName, TableChange.deleteColumn(new String[] {newColumn.name()}, true));
    load = TABLE_OPERATIONS.load(TEST_DB_NAME, newName);
    assertionsTableInfo(newName, tableComment, columns, properties, indexes, load);

    IllegalArgumentException illegalArgumentException =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () ->
                TABLE_OPERATIONS.alterTable(
                    TEST_DB_NAME,
                    newName,
                    TableChange.deleteColumn(new String[] {newColumn.name()}, false)));
    Assertions.assertEquals(
        "Delete column does not exist: " + newColumn.name(), illegalArgumentException.getMessage());
    Assertions.assertDoesNotThrow(
        () ->
            TABLE_OPERATIONS.alterTable(
                TEST_DB_NAME,
                newName,
                TableChange.deleteColumn(new String[] {newColumn.name()}, true)));

    TABLE_OPERATIONS.alterTable(
        TEST_DB_NAME, newName, TableChange.deleteColumn(new String[] {newColumn.name()}, true));
    Assertions.assertDoesNotThrow(() -> TABLE_OPERATIONS.drop(TEST_DB_NAME, newName));
    Assertions.assertThrows(
        NoSuchTableException.class, () -> TABLE_OPERATIONS.drop(TEST_DB_NAME, newName));
  }

  @Test
  public void testAlterTable() {
    String tableName = RandomUtils.nextInt(10000) + "_al_table";
    String tableComment = "test_comment";
    List<JdbcColumn> columns = new ArrayList<>();
    JdbcColumn col_1 =
        new JdbcColumn.Builder()
            .withName("col_1")
            .withType(INT)
            .withComment("id")
            .withNullable(false)
            .build();
    columns.add(col_1);
    JdbcColumn col_2 =
        new JdbcColumn.Builder()
            .withName("col_2")
            .withType(VARCHAR)
            .withComment("name")
            // TODO: uncomment this after supporting default values
            // .withDefaultValue("hello world")
            .withNullable(false)
            .build();
    columns.add(col_2);
    Map<String, String> properties = new HashMap<>();

    Index[] indexes =
        new Index[] {
          Indexes.createMysqlPrimaryKey(new String[][] {{"col_1"}, {"col_2"}}),
          Indexes.unique("uk_2", new String[][] {{"col_1"}, {"col_2"}})
        };
    // create table
    TABLE_OPERATIONS.create(
        TEST_DB_NAME,
        tableName,
        columns.toArray(new JdbcColumn[0]),
        tableComment,
        properties,
        null,
        indexes);
    JdbcTable load = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);
    assertionsTableInfo(tableName, tableComment, columns, properties, indexes, load);

    TABLE_OPERATIONS.alterTable(
        TEST_DB_NAME,
        tableName,
        TableChange.updateColumnType(new String[] {col_1.name()}, VARCHAR));

    load = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);

    // After modifying the type, some attributes of the corresponding column are not supported.
    columns.clear();
    col_1 =
        new JdbcColumn.Builder()
            .withName(col_1.name())
            .withType(VARCHAR)
            .withComment(col_1.comment())
            .withNullable(col_1.nullable())
            // TODO: uncomment this after supporting default values
            // .withDefaultValue(col_1.getDefaultValue())
            .build();
    columns.add(col_1);
    columns.add(col_2);
    assertionsTableInfo(tableName, tableComment, columns, properties, indexes, load);

    String newComment = "new_comment";
    // update table comment and column comment
    TABLE_OPERATIONS.alterTable(
        TEST_DB_NAME,
        tableName,
        TableChange.updateColumnType(new String[] {col_1.name()}, INT),
        TableChange.updateColumnComment(new String[] {col_2.name()}, newComment));
    load = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);

    columns.clear();
    col_1 =
        new JdbcColumn.Builder()
            .withName(col_1.name())
            .withType(INT)
            .withComment(col_1.comment())
            .withAutoIncrement(col_1.autoIncrement())
            .withNullable(col_1.nullable())
            // TODO: uncomment this after supporting default values
            // .withDefaultValue(col_1.getDefaultValue())
            .build();
    col_2 =
        new JdbcColumn.Builder()
            .withName(col_2.name())
            .withType(col_2.dataType())
            .withComment(newComment)
            .withAutoIncrement(col_2.autoIncrement())
            .withNullable(col_2.nullable())
            // TODO: uncomment this after supporting default values
            // .withDefaultValue(col_2.getDefaultValue())
            .build();
    columns.add(col_1);
    columns.add(col_2);
    assertionsTableInfo(tableName, tableComment, columns, properties, indexes, load);

    String newColName_1 = "new_col_1";
    String newColName_2 = "new_col_2";
    // rename column
    TABLE_OPERATIONS.alterTable(
        TEST_DB_NAME,
        tableName,
        TableChange.renameColumn(new String[] {col_1.name()}, newColName_1),
        TableChange.renameColumn(new String[] {col_2.name()}, newColName_2));

    load = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);

    columns.clear();
    col_1 =
        new JdbcColumn.Builder()
            .withName(newColName_1)
            .withType(col_1.dataType())
            .withComment(col_1.comment())
            .withAutoIncrement(col_1.autoIncrement())
            .withNullable(col_1.nullable())
            // TODO: uncomment this after supporting default values
            // .withDefaultValue(col_1.getDefaultValue())
            .build();
    col_2 =
        new JdbcColumn.Builder()
            .withName(newColName_2)
            .withType(col_2.dataType())
            .withComment(col_2.comment())
            .withAutoIncrement(col_2.autoIncrement())
            .withNullable(col_2.nullable())
            // TODO: uncomment this after supporting default values
            // .withDefaultValue(col_2.getDefaultValue())
            .build();
    columns.add(col_1);
    columns.add(col_2);
    assertionsTableInfo(tableName, tableComment, columns, properties, indexes, load);

    newComment = "txt3";
    String newCol2Comment = "xxx";
    // update column position 、comment and add column、set table properties
    TABLE_OPERATIONS.alterTable(
        TEST_DB_NAME,
        tableName,
        TableChange.updateColumnPosition(
            new String[] {newColName_1}, TableChange.ColumnPosition.after(newColName_2)),
        TableChange.updateComment(newComment),
        TableChange.addColumn(new String[] {"col_3"}, VARCHAR, "txt3"),
        TableChange.updateColumnComment(new String[] {newColName_2}, newCol2Comment));
    load = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);

    columns.clear();

    columns.add(
        new JdbcColumn.Builder()
            .withName(col_2.name())
            .withType(col_2.dataType())
            .withComment(newCol2Comment)
            .withAutoIncrement(col_2.autoIncrement())
            // TODO: uncomment this after supporting default values
            // .withDefaultValue(col_2.getDefaultValue())
            .withNullable(col_2.nullable())
            .build());
    columns.add(col_1);
    JdbcColumn col_3 =
        new JdbcColumn.Builder()
            .withName("col_3")
            .withType(VARCHAR)
            .withNullable(true)
            .withComment("txt3")
            .build();
    columns.add(
        new JdbcColumn.Builder().withName("col_3").withType(VARCHAR).withComment("txt3").build());
    assertionsTableInfo(tableName, newComment, columns, properties, indexes, load);

    TABLE_OPERATIONS.alterTable(
        TEST_DB_NAME,
        tableName,
        TableChange.updateColumnPosition(new String[] {columns.get(0).name()}, null),
        TableChange.updateColumnNullability(new String[] {col_3.name()}, !col_3.nullable()));

    load = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);
    col_2 = columns.remove(0);
    columns.clear();

    columns.add(col_1);
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_3")
            .withType(VARCHAR)
            .withNullable(false)
            .withComment("txt3")
            .build());
    columns.add(col_2);

    assertionsTableInfo(tableName, newComment, columns, properties, indexes, load);
  }

  @Test
  public void testCreateAndLoadTable() {
    String tableName = RandomUtils.nextInt(10000) + "_cl_table";
    String tableComment = "test_comment";
    List<JdbcColumn> columns = new ArrayList<>();
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_1")
            .withType(Types.DecimalType.of(10, 2))
            .withComment("test_decimal")
            .withNullable(false)
            // TODO: uncomment this after supporting default values
            // .withDefaultValue("0.00")
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_2")
            .withType(Types.LongType.get())
            .withNullable(false)
            // TODO: uncomment this after supporting default values
            // .withDefaultValue("0")
            .withComment("long type")
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_3")
            .withType(Types.TimestampType.withoutTimeZone())
            // MySQL 5.7 doesn't support nullable timestamp
            .withNullable(false)
            .withComment("timestamp")
            // TODO: uncomment this after supporting default values
            // .withDefaultValue("2013-01-01 00:00:00")
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_4")
            .withType(Types.DateType.get())
            .withNullable(true)
            .withComment("date")
            .build());
    Map<String, String> properties = new HashMap<>();

    Index[] indexes =
        new Index[] {
          Indexes.createMysqlPrimaryKey(new String[][] {{"col_2"}}),
          Indexes.unique("uk_col_4", new String[][] {{"col_4"}})
        };
    // create table
    TABLE_OPERATIONS.create(
        TEST_DB_NAME,
        tableName,
        columns.toArray(new JdbcColumn[0]),
        tableComment,
        properties,
        null,
        indexes);

    JdbcTable loaded = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);
    assertionsTableInfo(tableName, tableComment, columns, properties, indexes, loaded);
  }

  @Test
  public void testCreateAllTypeTable() {
    String tableName = GravitinoITUtils.genRandomName("type_table_");
    String tableComment = "test_comment";
    List<JdbcColumn> columns = new ArrayList<>();
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_1")
            .withType(Types.ByteType.get())
            .withNullable(false)
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_2")
            .withType(Types.ShortType.get())
            .withNullable(true)
            .build());
    columns.add(
        new JdbcColumn.Builder().withName("col_3").withType(INT).withNullable(false).build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_4")
            .withType(Types.LongType.get())
            .withNullable(false)
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_5")
            .withType(Types.FloatType.get())
            .withNullable(false)
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_6")
            .withType(Types.DoubleType.get())
            .withNullable(false)
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_7")
            .withType(Types.DateType.get())
            .withNullable(false)
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_8")
            .withType(Types.TimeType.get())
            .withNullable(false)
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_9")
            .withType(Types.TimestampType.withoutTimeZone())
            .withNullable(false)
            .build());
    columns.add(
        new JdbcColumn.Builder().withName("col_10").withType(Types.DecimalType.of(10, 2)).build());
    columns.add(
        new JdbcColumn.Builder().withName("col_11").withType(VARCHAR).withNullable(false).build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_12")
            .withType(Types.FixedCharType.of(10))
            .withNullable(false)
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_13")
            .withType(Types.StringType.get())
            .withNullable(false)
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_14")
            .withType(Types.BinaryType.get())
            .withNullable(false)
            .build());
    columns.add(
        new JdbcColumn.Builder()
            .withName("col_15")
            .withType(Types.FixedCharType.of(10))
            .withNullable(false)
            .build());

    // create table
    TABLE_OPERATIONS.create(
        TEST_DB_NAME,
        tableName,
        columns.toArray(new JdbcColumn[0]),
        tableComment,
        Collections.emptyMap(),
        null,
        Indexes.EMPTY_INDEXES);

    JdbcTable load = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);
    assertionsTableInfo(tableName, tableComment, columns, Collections.emptyMap(), null, load);
  }

  @Test
  public void testCreateNotSupportTypeTable() {
    String tableName = GravitinoITUtils.genRandomName("type_table_");
    String tableComment = "test_comment";
    List<JdbcColumn> columns = new ArrayList<>();
    List<Type> notSupportType =
        Arrays.asList(
            Types.BooleanType.get(),
            Types.FixedType.of(10),
            Types.IntervalDayType.get(),
            Types.IntervalYearType.get(),
            Types.TimestampType.withTimeZone(),
            Types.UUIDType.get(),
            Types.ListType.of(Types.DateType.get(), true),
            Types.MapType.of(Types.StringType.get(), Types.IntegerType.get(), true),
            Types.UnionType.of(Types.IntegerType.get()),
            Types.StructType.of(
                Types.StructType.Field.notNullField("col_1", Types.IntegerType.get())));

    for (Type type : notSupportType) {
      columns.clear();
      columns.add(
          new JdbcColumn.Builder().withName("col_1").withType(type).withNullable(false).build());

      IllegalArgumentException illegalArgumentException =
          Assertions.assertThrows(
              IllegalArgumentException.class,
              () ->
                  TABLE_OPERATIONS.create(
                      TEST_DB_NAME,
                      tableName,
                      columns.toArray(new JdbcColumn[0]),
                      tableComment,
                      Collections.emptyMap(),
                      null,
                      Indexes.EMPTY_INDEXES));
      Assertions.assertTrue(
          illegalArgumentException
              .getMessage()
              .contains("Not a supported type: " + type.toString()));
    }
  }

  @Test
  public void testCreateMultipleTables() {
    String test_table_1 = "test_table_1";
    TABLE_OPERATIONS.create(
        TEST_DB_NAME,
        test_table_1,
        new JdbcColumn[] {
          new JdbcColumn.Builder()
              .withName("col_1")
              .withType(Types.DecimalType.of(10, 2))
              .withComment("test_decimal")
              .withNullable(false)
              // TODO: uncomment this after supporting default values
              // .withDefaultValue("0.00")
              .build()
        },
        "test_comment",
        null,
        null,
        Indexes.EMPTY_INDEXES);

    String testDb = "test_db_2";

    DATABASE_OPERATIONS.create(testDb, null, null);
    List<String> tables = TABLE_OPERATIONS.listTables(testDb);
    Assertions.assertFalse(tables.contains(test_table_1));

    String test_table_2 = "test_table_2";
    TABLE_OPERATIONS.create(
        testDb,
        test_table_2,
        new JdbcColumn[] {
          new JdbcColumn.Builder()
              .withName("col_1")
              .withType(Types.DecimalType.of(10, 2))
              .withComment("test_decimal")
              .withNullable(false)
              // TODO: uncomment this after supporting default values
              // .withDefaultValue("0.00")
              .build()
        },
        "test_comment",
        null,
        null,
        Indexes.EMPTY_INDEXES);

    tables = TABLE_OPERATIONS.listTables(TEST_DB_NAME);
    Assertions.assertFalse(tables.contains(test_table_2));
  }

  @Test
  public void testLoadTableDefaultProperties() {
    String test_table_1 = GravitinoITUtils.genRandomName("properties_table_");
    TABLE_OPERATIONS.create(
        TEST_DB_NAME,
        test_table_1,
        new JdbcColumn[] {
          new JdbcColumn.Builder()
              .withName("col_1")
              .withType(Types.DecimalType.of(10, 2))
              .withComment("test_decimal")
              .withNullable(false)
              .build()
        },
        "test_comment",
        null,
        null,
        Indexes.EMPTY_INDEXES);
    JdbcTable load = TABLE_OPERATIONS.load(TEST_DB_NAME, test_table_1);
    Assertions.assertEquals("InnoDB", load.properties().get(MYSQL_ENGINE_KEY));
  }

  @Test
  public void testAppendIndexesBuilder() {
    Index[] indexes =
        new Index[] {
          Indexes.createMysqlPrimaryKey(new String[][] {{"col_2"}, {"col_1"}}),
          Indexes.unique("uk_col_4", new String[][] {{"col_4"}}),
          Indexes.unique("uk_col_5", new String[][] {{"col_4"}, {"col_5"}}),
          Indexes.unique("uk_col_6", new String[][] {{"col_4"}, {"col_5"}, {"col_6"}})
        };
    StringBuilder sql = new StringBuilder();
    MysqlTableOperations.appendIndexesSql(indexes, sql);
    String expectedStr =
        ",\n"
            + "CONSTRAINT PRIMARY KEY (`col_2`, `col_1`),\n"
            + "CONSTRAINT `uk_col_4` UNIQUE (`col_4`),\n"
            + "CONSTRAINT `uk_col_5` UNIQUE (`col_4`, `col_5`),\n"
            + "CONSTRAINT `uk_col_6` UNIQUE (`col_4`, `col_5`, `col_6`)";
    Assertions.assertEquals(expectedStr, sql.toString());

    indexes =
        new Index[] {
          Indexes.unique("uk_1", new String[][] {{"col_4"}}),
          Indexes.unique("uk_2", new String[][] {{"col_4"}, {"col_3"}}),
          Indexes.createMysqlPrimaryKey(new String[][] {{"col_2"}, {"col_1"}, {"col_3"}}),
          Indexes.unique("uk_3", new String[][] {{"col_4"}, {"col_5"}, {"col_6"}, {"col_7"}})
        };
    sql = new StringBuilder();
    MysqlTableOperations.appendIndexesSql(indexes, sql);
    expectedStr =
        ",\n"
            + "CONSTRAINT `uk_1` UNIQUE (`col_4`),\n"
            + "CONSTRAINT `uk_2` UNIQUE (`col_4`, `col_3`),\n"
            + "CONSTRAINT PRIMARY KEY (`col_2`, `col_1`, `col_3`),\n"
            + "CONSTRAINT `uk_3` UNIQUE (`col_4`, `col_5`, `col_6`, `col_7`)";
    Assertions.assertEquals(expectedStr, sql.toString());
  }

  @Test
  public void testAutoIncrement() {
    String tableName = "test_increment_table_1";
    String comment = "test_comment";
    Map<String, String> properties =
        new HashMap<String, String>() {
          {
            put(MYSQL_AUTO_INCREMENT_OFFSET_KEY, "10");
          }
        };
    JdbcColumn[] columns = {
      new JdbcColumn.Builder()
          .withName("col_1")
          .withType(Types.LongType.get())
          .withComment("id")
          .withAutoIncrement(true)
          .withNullable(false)
          .build(),
      new JdbcColumn.Builder()
          .withName("col_2")
          .withType(Types.VarCharType.of(255))
          .withComment("city")
          .withNullable(false)
          .build(),
      new JdbcColumn.Builder()
          .withName("col_3")
          .withType(Types.VarCharType.of(255))
          .withComment("name")
          .withNullable(false)
          .build()
    };
    // Test create increment key for unique index.
    Index[] indexes =
        new Index[] {
          Indexes.createMysqlPrimaryKey(new String[][] {{"col_2"}}),
          Indexes.unique("uk_1", new String[][] {{"col_1"}})
        };
    TABLE_OPERATIONS.create(TEST_DB_NAME, tableName, columns, comment, properties, null, indexes);

    JdbcTable table = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);
    assertionsTableInfo(
        tableName,
        comment,
        Arrays.stream(columns).collect(Collectors.toList()),
        properties,
        indexes,
        table);
    TABLE_OPERATIONS.drop(TEST_DB_NAME, tableName);

    // Test create increment key for primary index.
    indexes =
        new Index[] {
          Indexes.createMysqlPrimaryKey(new String[][] {{"col_1"}}),
          Indexes.unique("uk_2", new String[][] {{"col_2"}})
        };
    TABLE_OPERATIONS.create(TEST_DB_NAME, tableName, columns, comment, properties, null, indexes);

    table = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);
    assertionsTableInfo(
        tableName,
        comment,
        Arrays.stream(columns).collect(Collectors.toList()),
        properties,
        indexes,
        table);
    TABLE_OPERATIONS.drop(TEST_DB_NAME, tableName);

    // Test create increment key for col_1 + col_3 uk.
    indexes = new Index[] {Indexes.unique("uk_2_3", new String[][] {{"col_1"}, {"col_3"}})};
    TABLE_OPERATIONS.create(TEST_DB_NAME, tableName, columns, comment, properties, null, indexes);

    table = TABLE_OPERATIONS.load(TEST_DB_NAME, tableName);
    assertionsTableInfo(
        tableName,
        comment,
        Arrays.stream(columns).collect(Collectors.toList()),
        properties,
        indexes,
        table);
    TABLE_OPERATIONS.drop(TEST_DB_NAME, tableName);

    // Test create auto increment fail
    IllegalArgumentException exception =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () ->
                TABLE_OPERATIONS.create(
                    TEST_DB_NAME,
                    tableName,
                    columns,
                    comment,
                    properties,
                    null,
                    Indexes.EMPTY_INDEXES));
    Assertions.assertTrue(
        StringUtils.contains(
            exception.getMessage(),
            "Incorrect table definition; there can be only one auto column and it must be defined as a key"));

    // Test create many auto increment col
    JdbcColumn[] newColumns = {
      columns[0],
      columns[1],
      columns[2],
      new JdbcColumn.Builder()
          .withName("col_4")
          .withType(Types.IntegerType.get())
          .withComment("test_id")
          .withAutoIncrement(true)
          .withNullable(false)
          .build()
    };

    exception =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () ->
                TABLE_OPERATIONS.create(
                    TEST_DB_NAME,
                    tableName,
                    newColumns,
                    comment,
                    properties,
                    null,
                    new Index[] {
                      Indexes.createMysqlPrimaryKey(new String[][] {{"col_1"}, {"col_4"}})
                    }));
    Assertions.assertTrue(
        StringUtils.contains(
            exception.getMessage(),
            "Only one column can be auto-incremented. There are multiple auto-increment columns in your table: [col_1,col_4]"));
  }
}
