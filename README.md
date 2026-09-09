# Liquibase StarRocks

Manage StarRocks migrations with Liquibase. The extension provides database
recognition, changelog tracking, lock lifecycle handling and StarRocks SQL
adaptations. Published release: **0.3.0**.

## Install the released extension

Download the [0.3.0 JAR](https://github.com/infocusmodereal/liquibase-starrocks/releases/tag/v0.3.0)
and MySQL Connector/J **8.4.0**, and place both in your Liquibase CLI `lib/`
directory. Java 17 is the minimum for the extension; use the exact Java and
Liquibase versions in the [compatibility matrix](COMPATIBILITY.md).

For an embedded application, add the Maven dependency
`io.github.infocusmodereal:liquibase-starrocks:0.3.0` alongside Liquibase core.
The CLI JAR includes Kotlin, and excludes Liquibase core and the JDBC driver.

## Run a migration

Create `liquibase.properties` using a dedicated migration account:

```properties
url=jdbc:mysql://localhost:9030/your_database
driver=com.mysql.cj.jdbc.Driver
username=your_username
password=your_password
changeLogFile=changelog.yaml
```

Create `changelog.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: create-events
      author: example
      changes:
        - sql:
            sql: |
              CREATE TABLE events (id INT NOT NULL, name VARCHAR(255))
              ENGINE = OLAP PRIMARY KEY(id)
              DISTRIBUTED BY HASH(id) BUCKETS 1
              PROPERTIES ('replication_num'='1');
      rollback:
        - sql:
            sql: DROP TABLE events;
```

Run `liquibase validate`, `liquibase update-sql` and `liquibase update`.
The database must already exist. The migration account needs CREATE VIEW and
DROP VIEW permissions on the metadata database for the lock reservation, in
addition to the permissions required by its migrations and metadata tables.
Replication 1 is suitable for a single-node
example; choose a production layout for your cluster. Rollback is an explicit
reverse migration, not a transactional undo of StarRocks DDL.

## What's new in 0.3

0.3 adds an explicit `createStarRocksTable` change, version probing, metadata
configuration and broader validation. It rejects `modifyDataType` instead of
silently recording a change without altering a column. Review the
[upgrade notes](docs/upgrading-0.3.md) before testing existing changelogs.

- [Capabilities and limitations](docs/capabilities.md)
- [Configuration and native table examples](docs/configuration.md)
- [Exact compatibility evidence](COMPATIBILITY.md) and [candidate matrix](compatibility/candidates.json)
- [Troubleshooting](docs/troubleshooting.md)
- [Contributing](CONTRIBUTING.md) and [local development](DEVELOPMENT.md)
- [Architecture](docs/architecture.md) and [0.3 plan](docs/roadmap-0.3.md)
- [Release process](PUBLISHING.md), [security reporting](SECURITY.md), [support](SUPPORT.md)

## Development

```bash
./scripts/dev test prepareIntegrationJar -PbaseVersion=0.3.0
python3 scripts/verify-project.py --jar build/integration/liquibase-starrocks.jar
```

The helper selects JDK 17 on macOS and disables release signing. See
[DEVELOPMENT.md](DEVELOPMENT.md) for Docker tests and the official Liquibase
Test Harness. Tests use disposable databases and never need publishing credentials.

## License

[Apache License 2.0](LICENSE). See [dependency notes](docs/dependencies.md) for
host dependencies and the separate upstream test harness.
