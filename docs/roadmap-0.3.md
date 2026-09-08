# 0.3 proposal: predictable migrations and verifiable compatibility

Status: implementation and release validation in progress, 2026-09-08. Branch: `feat/0.3`.
Baseline: released 0.2.0; reviewed main commit `bf47335`.
Development version: `0.3.0-SNAPSHOT`. This document does not claim that the
proposed functionality or version combinations have been implemented or tested.

## Release objective

Make the extension behave predictably through Liquibase's normal commands,
record exactly which database combinations work, and provide reproducible
instructions for users and contributors. The first priority is preventing
silent migration success when no schema change occurred.

## Findings from the current source

These are source-review findings and coverage gaps, not newly reproduced
database failures. The existing 11 tests and integration suite remain the
regression baseline documented in [COMPATIBILITY.md](../COMPATIBILITY.md).

| Priority | Evidence | Proposed outcome |
| --- | --- | --- |
| P0 | `ModifyDataTypeStarRocks` matches every StarRocks `ModifyDataTypeStatement` and returns no SQL, although its comment describes a metadata-only workaround. | Reproduce a user-column change reported as successful without changing its type. Implement supported alterations or reject them with a clear validation error. Restrict any metadata exception to a justified, tested case. |
| P0 | Several metadata generators interpolate schema/table names directly. `RemoveChangeSetRanStatusStarRocks` also interpolates changeset values and uses `filePath`, unlike the checksum fix. | Centralize Liquibase-aware identifier/literal handling; preserve stored/logical changelog identity across update, tag and rollback. |
| P1 | `StarRocksDatabase` accepts any URL containing `9030` or `starrocks`; only positive discovery is tested. | Detect real servers on custom ports and avoid claiming MySQL connections whose host, database or parameters happen to match. Document an explicit selection path for ambiguous proxies. |
| P1 | Both metadata tables hardcode `replication_num=1`; generators mix `defaultSchemaName` with Liquibase catalog/schema settings. | Respect metadata location and custom table names. Add validated metadata replication configuration and an upgrade policy for existing tables. |
| P1 | Lock recovery is tested, but concurrent startup, competing processes and reuse of a service instance after reset are not. Initialization uses DELETE followed by INSERT; initialization state is cached. | Prove one migration owner, bounded waiting, safe initialization and reconnect/reset behavior with real processes. Treat atomicity as unproven until exercised. |
| P1 | VARCHAR and DECIMAL mappings index parameter arrays directly; existing tests only use fully specified sizes. | Test omitted parameters, boundary sizes/precision, invalid combinations, nullability and actual database round trips. |
| P1 | The integration changelog creates its user table through raw SQL. No extension-specific user `createTable` generator or change is present. | Establish an explicit capability contract for native changes, SQL fallback, snapshots and rollback. |
| P2 | README starts with source layout and a 4.23.0 CLI download; there is no contributor guide or capability reference. | Lead with installation of the released artifact, a working migration and exact compatibility. Separate user, contributor and maintainer workflows. |

Paths above are under `src/main/kotlin/liquibase/ext/starrocks/`.

## Delivery sequence

Each step should be a small PR against `feat/0.3`, with a failing regression
before a behavior fix and a documented acceptance result. P0 items block release.

### 1. Prevent silent changes and incorrect metadata operations

- Reproduce the `modifyDataType` case through Liquibase, not only a generator
  assertion. An unsupported operation must fail before being recorded as ran.
- For supported alterations, verify the resulting column through the database.
  Account for asynchronous schema changes with bounded completion checks.
- Test quoted identifiers, apostrophes in author/id/path, `logicalFilePath`,
  custom changelog table names and an explicitly selected metadata database.
- Test tag selection and rollback bookkeeping with multiple changesets,
  repeated runs and equal execution timestamps.

Acceptance: real schema state and `DATABASECHANGELOG` agree after success,
failure and explicit rollback; the 0.2.0 regression matrix still passes.

### 2. Establish the Liquibase capability contract

Adopt a pinned, compatible version of the official
[Liquibase Test Harness](https://github.com/liquibase/liquibase-test-harness/blob/main/README.extensions.md)
in a dedicated integration task. First verify its dependency and runtime
requirements; do not upgrade the production API merely to satisfy a test tool.
Liquibase's [database extension guide](https://contribute.liquibase.com/extensions-integrations/extension-guides/add-a-database/milestone2-step1/)
recommends the harness to find gaps in inherited behavior.

Minimum 0.3 command coverage: `validate`, `status`, `update-sql`, `update`,
`history`, `tag`, explicit `rollback-count`, `clear-checksums`, `list-locks`
and `release-locks`. Exercise contexts, labels, preconditions, includes,
`runOnChange` and `runAlways`. Verify that SQL preview does not mutate data.

For declarative changes, test create/drop table and add/drop/rename/modify
column. Design a native StarRocks table change or generator with validated
key model, distribution and properties only after testing how those options
can be represented in Liquibase's changelog formats. Do not silently invent
keys or distribution for arbitrary user tables. Raw SQL remains documented.

Start snapshot coverage with tables, columns and primary keys. Probe `diff`
and `generate-changelog`, including key model, distribution and properties.
Document lossy output and unsupported objects; do not advertise full round-trip
support until an exported changelog recreates an equivalent schema.

Acceptance: a capability table states supported, SQL-only, unsupported or
experimental for each operation, with executable examples and evidence.
Include CLI and an embedded Java/CommandScope smoke test so plugin discovery
and behavior are checked beyond a single launch method.

### 3. Strengthen operational behavior

- Replace URL substring heuristics with tested discovery rules. Include a
  negative MySQL case and an actual StarRocks connection on a non-default port.
- Run two independent migration processes and simultaneous fresh-database
  initialization. Assert only one owner applies a changeset and no process
  resets another owner's lock. Exercise timeout, failed migration, recovery
  and service reset/reconnect. Never recover locks automatically on timeout.
- Add metadata replication settings using Liquibase's configuration facilities.
  Preserve existing installation defaults; validate against a suitable cluster
  and document how existing tables are changed. A single-node test cannot prove HA.
- Cover VARCHAR/DECIMAL defaults and limits, reserved names and database names.
- Audit the produced JAR and published POM: service registrations, duplicate
  dependencies and absence of bundled Liquibase core. Keep only justified
  dependencies and test artifact consumption outside the source build.
- Consolidate release tooling around in-memory signing and non-verbose,
  redacted publishing. Current legacy tasks pass signing/auth values through
  command arguments. Normal builds must work without publishing credentials.

Acceptance: failures remain visible, concurrency invariants are measured,
and packaging/installation work from a clean consumer environment.

### 4. Expand compatibility with a durable record

The initial [registry](../compatibility/releases.json) preserves existing
0.2.0 evidence. Add a schema validator and CI jobs that consume a separate
candidate matrix, then publish immutable results per release. Do not derive
new compatibility claims from older releases' successful rows.

Proposed exact combinations, all pending validation for 0.3:

| Role | Liquibase | StarRocks | JDK |
| --- | --- | --- | --- |
| Preserve old baseline | 4.23.0 | 3.1.7 | 17 |
| Preserve reported regression environment | 4.29.1 | 4.1.1 | 21 |
| Preserve 0.2.0 primary path | 5.0.3 | 4.1.1 | 21 |
| New primary candidate | 5.0.4 | 4.1.3 | 21 |
| Additional 4.0 line | 5.0.4 | 4.0.14 | 21 |
| Additional 3.5 line | 5.0.4 | 3.5.21 | 21 |

Version selection is based on upstream material checked on 2026-09-08:
[Liquibase 5.0.4](https://github.com/liquibase/liquibase/releases/tag/v5.0.4),
[StarRocks 3.5.21](https://github.com/StarRocks/starrocks/releases/tag/3.5.21),
[4.0.14](https://github.com/StarRocks/starrocks/releases/tag/4.0.14), and
[4.1.3 release notes](https://docs.starrocks.io/releasenotes/release-4.1/).
The recent GitHub releases list and 4.1 documentation differ: the list checked
contains 4.1.1 while the documentation lists 4.1.3. Verify exact image/tag
availability, provenance and CPU support before enabling the 4.1.3 row.
Image availability for all new candidates remains unchecked in this proposal.
Record an infrastructure-blocked result if an image cannot run; never replace
its version silently. Keep 4.1.0's known container limitation documented.

Keep Connector/J 8.4.0 fixed initially to isolate runtime/server changes.
Evaluate a driver update in its own PR with the same matrix. Preserve the
Liquibase 5.0.3 compile API initially; test 5.0.4 as a runtime first.

Every result must identify connector version, commit and JAR SHA-256;
Liquibase, StarRocks and JDBC versions; JDK, OS, architecture and topology;
container digest; scenario set, date, outcome and durable evidence URL.
Use separate records for source-built CI and signed release artifact tests.
Record failed, blocked and untested candidates separately from passing rows.

Run the six selected combinations on PRs, keeping jobs independent. Before
release, exercise the exact signed artifact on the advertised combinations
and verify public downloads. Cover Linux amd64 and the primary path on arm64;
record architecture-specific evidence instead of assuming equivalence.
Artifact names must include the full tuple to avoid collisions as coverage grows.

Proposed support policy: recommend only exact passing combinations for named
capabilities. Preserve older rows as regression coverage, without claiming
that upstream still maintains those versions. Unlisted combinations are
unverified. Announce future removals in release notes before dropping tests.

### 5. Make the project straightforward to use and contribute to

- README: released coordinates, CLI installation, working quickstart,
  compatibility summary, limitations, support and contribution links.
- `docs/`: configuration reference, capability table, SQL/YAML/XML examples,
  explicit rollback, troubleshooting, upgrade guide from 0.2 and architecture.
- `CONTRIBUTING.md`: clean clone setup without credentials, test tiers,
  formatting, a small PR walkthrough and how to add a compatibility case.
- `SECURITY.md`, support policy and issue/PR templates: reproducible reports
  should include exact versions, sanitized changelog, expected/actual behavior
  and logs. Publish only reporting channels that are actually configured.
- Release checklist: versioned matrix, migration notes, signed artifacts,
  hashes, dependency/license inventory and repeatable publishing instructions.
- Exercise documentation examples in CI; check links and ensure test counts
  and version tables cannot silently drift from recorded results.

Acceptance: a new contributor can build and run the documented quickstart
from a clean checkout without local maintainer files.

## Release boundary

Ship 0.3 when P0 fixes pass, the minimum command/capability contract is
documented and exercised, concurrency behavior is understood, and the exact
advertised version combinations have release-artifact evidence. If a candidate
fails, fix it or mark it unsupported and revise the release scope explicitly.

Full snapshot/diff round trips, all StarRocks key models, complex nested types,
materialized views, shared-data deployments and multi-node failover require
separate evidence. Keep them as follow-up work unless focused investigation
shows they fit without delaying the correctness work above.

Recommended first implementation PR: **fix silent modifyDataType success and
add a database-backed regression**. Follow with metadata identity/quoting,
the harness/capability contract, operational tests, version expansion and docs.


## Implementation record

The implementation is tracked in [PR #9](https://github.com/infocusmodereal/liquibase-starrocks/pull/9).
The original proposal above is retained for traceability; the current contract
is in [capabilities.md](capabilities.md).

- Reproduced the published 0.2.0 silent type change; validation now rejects it.
- Added native PRIMARY/DUPLICATE tables, core ADD COLUMN adaptation, catalog
  settings, real server versions, scalar validation and PRIMARY-key snapshots.
- Tested rollback identity/quoting, tag ties, repeated changes, preview, failed
  migrations and lock recovery. Added the official harness on an isolated runtime.
- Repeated contention tests exposed dual ownership with row-only locking.
  Catalog reservations passed ten local rounds with two processes per round.
  This adds documented CREATE VIEW/DROP VIEW privileges and forbids mixed
  0.2/0.3 concurrent writers. New CI runs revalidate the full version matrix.
- Added candidate-driven CI, artifact provenance, signed bundle preparation,
  independent signature verification, and user/contributor/upgrade documentation.

Release validation remains in progress until the final CI matrix and exact
signed-artifact checks are recorded. Broader diff reconstruction, additional key
models, shared-data deployment and multi-node failover remain follow-up scope.
Replication values above one are configurable, but the single-node matrix does
not establish multi-node availability behavior. Native async alterations require
explicit SQL/completion handling as described in the capability contract.
