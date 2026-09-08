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
| Other column alterations | Use explicit SQL and verify completion. Declarative add/drop/rename coverage is not yet a support claim. |
| `snapshot` | Tables, columns and PRIMARY keys. StarRocks primary keys come from `SHOW CREATE TABLE`, because JDBC key metadata is empty. DUPLICATE keys are not relational primary keys. |
| `generate-changelog`, `diff` | Experimental inspection. Standard snapshots omit distribution, buckets and properties, so generated changelogs are not lossless or ready to replay. |
| Foreign keys, unique constraints, relational indexes, sequences | Not represented as supported database objects. StarRocks-specific indexes require SQL. |

The lock test starts two CLI processes against a fresh database and checks
one migration record, one event and a released lock. This is bounded
single-node evidence, not a proof of distributed mutual exclusion under every
failure mode. Multi-node failover and shared-data deployments are outside the
current release matrix. Use a single migration deployment job per database
when operating outside the tested conditions.
