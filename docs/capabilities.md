# 0.3 capabilities

This is the intended contract exercised by `integration/run-capabilities.sh`
and the upstream harness. A combination becomes verified only when its exact
result is recorded in [COMPATIBILITY.md](../COMPATIBILITY.md).

| Operation | 0.3 behavior and boundary |
| --- | --- |
| `validate`, `status`, `history`, `update` | Exercised with real StarRocks and repeated no-op updates. |
| `update-sql` | Produces a preview; tests verify that user tables and changelog rows are unchanged. |
| `tag`, `clear-checksums` | Exercise tags, checksum regeneration and changeset identities containing apostrophes. |
| `rollback-count` | Native table creation has a drop-table inverse; raw SQL requires explicit rollback SQL. No atomic DDL rollback. |
| `list-locks`, `release-locks` | Explicit recovery, repeated recovery, failed changes and bounded waiting are exercised. Never automatically force-release on timeout. |
| Contexts, labels, includes, preconditions | XML included from YAML, SQL precondition and filtered execution are exercised. |
| `runAlways`, `runOnChange` | Repeated execution and a parameter-driven view change are verified against database state. |
| `createStarRocksTable` | PRIMARY or DUPLICATE OLAP table, explicit keys/distribution, column types/nullability, buckets and replication. |
| Core `createTable` | Use native `createStarRocksTable` or raw SQL for explicit OLAP layout. Generic creation is not advertised. |
| `modifyDataType` | Rejected during validation and SQL generation, including metadata alterations. Use reviewed SQL and wait for asynchronous schema completion. |
| `addColumn`, `dropColumn`, `renameColumn` | ADD COLUMN adapts the core generator; add/drop and rename/reverse are exercised. Rename is unavailable on 3.1.7. Add/drop can be asynchronous: wait for completion before dependent statements. |
| `snapshot` | Tables, columns and PRIMARY keys. StarRocks primary keys come from `SHOW CREATE TABLE`, because JDBC key metadata is empty. DUPLICATE keys are not relational primary keys. |
| `generate-changelog`, `diff` | Experimental inspection. Standard snapshots omit distribution, buckets and properties, so generated changelogs are not lossless or ready to replay. |
| Foreign keys, unique constraints, relational indexes, sequences | Not represented as supported database objects. StarRocks-specific indexes require SQL. |

Lock acquisition first creates a reserved catalog view named
`<databaseChangeLogLockTableName>_MUTEX`, then uses the usual Liquibase lock row.
This is necessary because a conditional StarRocks UPDATE is not sufficient for
mutual exclusion: repeated two-process testing reproduced dual ownership.
Creating the same view without `IF NOT EXISTS` admits one creator; SQL error 1050 is treated as contention, as is the exact duplicate-view
message returned with code 1064 by StarRocks 3.1. Other errors remain failures.

Initialization uses the same reservation, and ordinary release drops it only
after releasing the row. A crash or release failure retains the reservation;
`release-locks` explicitly recovers it, even if LOCKED is false. There is no lease
expiry or automatic lock stealing. Both CREATE VIEW and DROP VIEW privileges
are required on the metadata database. Do not run 0.2 and 0.3 migrations together.

The integration test starts two CLI processes against a fresh database and checks
one migration record, one event and a released lock. Multi-node failover and
shared-data deployments remain outside the matrix. Exact release evidence is
required before extending concurrency claims to other topologies.
