# Troubleshooting

| Symptom | Check and next action |
| --- | --- |
| Liquibase selects MySQL | Confirm the extension JAR is loaded and `SELECT current_version()` works through the actual endpoint. Use the documented explicit databaseClass only for a known StarRocks server. |
| Missing Kotlin classes | Install the distribution JAR, not the `-thin.jar`, and remove duplicate extension versions. |
| `modifyDataType is not supported` | Inspect the actual column and use an explicit, reviewed SQL migration. See the upgrade guide for historical 0.2.0 silent changes. |
| Migration waits for a lock | Check `list-locks` and active migration jobs. Force-release only after confirming the owner is gone. A timeout does not release another process's lock. An orphan `_MUTEX` view can also block acquisition while LOCKED is false; explicit `release-locks` recovers both after you verify that no owner is active. |
| `replication_num` rejected | Check available backends and desired layout. Configuration affects new tables only. |
| Container 4.1.0 does not start | Use the documented fixed version 4.1.1 or another tested tuple; keep the failed image version in the report. |
| A schema alteration reports success but dependent SQL fails | StarRocks ALTER may be asynchronous. Inspect `SHOW ALTER TABLE` and wait for completion before subsequent changes. |
| Generated changelog lacks table properties | Snapshot/diff are limited inspection tools. Retain explicit StarRocks layout in your source changelog. |

When reporting a defect, include connector/Liquibase/StarRocks/Java/JDBC versions,
architecture, deployment topology, a minimal sanitized changelog and exact
expected/actual results. Use INFO logs to expose severe errors even when an
older extension exits successfully. Remove credentials and personal connection
details before attaching logs.
