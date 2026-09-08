# Configuration and examples for 0.3

## Connection and metadata

Use a `jdbc:mysql:` URL and MySQL Connector/J 8.4.0. StarRocks recognition probes
`SELECT current_version()` when JDBC does not identify the product. The reported
version is the actual StarRocks version. Hostnames and port 9030 do not determine
database identity. The driver does not accept a `jdbc:starrocks:` alias.

For a proxy that prevents probing, explicitly select
`liquibase.ext.starrocks.database.StarRocksDatabase` using Liquibase's
`databaseClass` connection setting. Version queries still require
`current_version()`; explicit selection is not a substitute for server access.

Liquibase's `--liquibase-catalog-name` selects the database for metadata tables.
`--database-changelog-table-name` and `--database-changelog-lock-table-name`
select their names. Create the metadata database beforehand and grant the
migration account access. The application database remains the JDBC URL database.

`liquibase.starrocks.metadataReplication` is a positive integer, default **1**.
For example, set `JAVA_OPTS=-Dliquibase.starrocks.metadataReplication=3` before
launching the CLI. StarRocks must have enough eligible backends. This setting
only affects newly created metadata tables; it does not alter existing tables.
Changing metadata placement or replication on an existing installation requires
an explicit, backed-up migration and a window with no active Liquibase processes.

The migration account needs access to its application/metadata databases,
metadata queries, DDL required by its changelog, and SELECT/INSERT/UPDATE/DELETE
on the two Liquibase metadata tables. Integration uses root only on disposable
containers. Production credentials belong in your secret management system.

## Native table change

The executable YAML example is [integration/changelogs/native.yaml](../integration/changelogs/native.yaml).
The [XML harness example](../src/harness/resources/liquibase/harness/change/changelogs/starrocks/createStarRocksTable.xml)
uses the Liquibase extension namespace. Both use standard nested `column` entries.

```yaml
- createStarRocksTable:
    tableName: events
    keyModel: PRIMARY
    keyColumns: id
    distributionColumns: id
    buckets: 1
    replicationNum: 1
    columns:
      - column:
          name: id
          type: INT
      - column:
          name: name
          type: VARCHAR(255)
```

Key columns must be a unique prefix of the column list. PRIMARY key columns
are non-null, and PRIMARY distribution columns must belong to the key. Both
bucket and replication counts must be positive. The current change intentionally
supports only column name/type/nullability; use raw SQL for defaults, comments,
partitioning, aggregation and other table properties. Validate before applying.

Rollback of this change drops the table and its data. Type modifications require
explicit SQL preserving attributes such as nullability/defaults and a bounded
`SHOW ALTER TABLE` completion check before dependent migrations.

Contexts, labels, includes, `runAlways` and `runOnChange` examples are in
[flags.yaml](../integration/changelogs/flags.yaml) and its included XML file.
