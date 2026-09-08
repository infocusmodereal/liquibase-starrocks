# Upgrading from 0.2.0 to 0.3.0

0.3 is a behavior-changing minor release. Test your changelogs on the exact
server/runtime combination you deploy before replacing the extension JAR.

1. Inventory `modifyDataType` changes. In 0.2.0 they could be recorded as
   `EXECUTED` while the column remained unchanged. Compare actual schema state;
   do not assume historical records prove the type changed. Add a new, reviewed
   corrective SQL changeset where needed. Do not rewrite executed changesets.
2. 0.3 rejects `modifyDataType`. For SQL alternatives preserve full column
   attributes and wait for StarRocks's asynchronous schema change to finish.
   Old metadata tables requiring automatic column resizing also fail visibly;
   upgrade them explicitly before retrying.
3. Keep only one extension JAR in the CLI/application classpath. Liquibase core
   and Connector/J are separate dependencies. Development `-thin.jar` files are
   not the CLI distribution.
4. Server detection now checks the product/version and `current_version()`.
   A misleading hostname or port no longer selects StarRocks. Check proxy access.
5. Stop all older migration jobs before upgrading. 0.3 adds a reserved
   `<lock-table-name>_MUTEX` view and requires CREATE VIEW/DROP VIEW privileges
   in the metadata database. Mixed 0.2/0.3 concurrent writers are unsupported.
6. Existing metadata tables remain in place with their current replication.
   Custom locations and names use Liquibase's catalog settings consistently.
7. Validate, preview, update, repeat update and test your rollback/recovery
   procedure on a disposable copy before production rollout.

This release does not automatically repair historical silent changes or modify
existing metadata replication. See [capabilities](capabilities.md) for unsupported
objects and snapshot limitations.
