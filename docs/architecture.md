# Architecture

`StarRocksDatabase` integrates with Liquibase's database factory and JDBC
connection, with capability declarations and StarRocks version discovery.
Service registrations under `META-INF/services/` discover database, lock,
SQL generator, data type, native change, snapshot and configuration providers.

- `database/`: recognition, catalog behavior, quoting and supported objects.
- `sqlgenerator/`: changelog/lock SQL, checksums, tag and rollback bookkeeping.
- `lockservice/`: core lifecycle adaptations, explicit recovery and reset state.
- `datatype/`: StarRocks scalar mappings and parameter validation.
- `change/`: explicit OLAP table change using Liquibase columns and inverse DDL.
- `snapshot/`: PRIMARY key discovery from server DDL.
- `configuration/`: namespaced Liquibase configuration for metadata creation.

Production Kotlin compiles against Liquibase 5.0.3 and targets JVM 17. Unit
compilation uses 4.23.0 so old-runtime coverage cannot accidentally call newer
APIs. `-PtestLiquibaseVersion` chooses the runtime. The official harness has an
isolated classpath with its own 5.0.3 API and never enters the published artifact.

CLI integration consumes one exact JAR. CI records its hash and Docker image
digest for each candidate tuple. Release signing does not rebuild or transform
JAR contents; artifact hashes connect test evidence to the prepared release.
