# Compatibility and validation

## 0.3.0 release

The candidate passed the [seven-job CI matrix](https://github.com/infocusmodereal/liquibase-starrocks/actions/runs/34289842299)
on 2026-09-08. All seven tested JARs are byte-identical to the independently
verified, signed 0.3.0 candidate. Version 0.3.0 was published to Maven Central and GitHub on 2026-09-09 UTC.
All four public Maven artifacts (plugin, sources, Javadoc and POM) were downloaded
and their SHA-256 hashes matched the signed bundle. Public GitHub artifacts
were also checked. See the [release](https://github.com/infocusmodereal/liquibase-starrocks/releases/tag/v0.3.0).

| Liquibase runtime | StarRocks | Java | Architecture | Unit tests | Capability suite |
| --- | --- | --- | --- | --- | --- |
| 4.23.0 | 3.1.7 | 17 | amd64 | 24 passed | Passed |
| 4.29.1 | 4.1.1 | 21 | amd64 | 24 passed | Passed |
| 5.0.3 | 4.1.1 | 21 | amd64 | 24 passed | Passed |
| 5.0.4 | 3.5.21 | 21 | amd64 | 24 passed | Passed |
| 5.0.4 | 4.0.14 | 21 | amd64 | 24 passed | Passed |
| 5.0.4 | 4.1.3 | 21 | amd64 | 24 passed | Passed |
| 5.0.4 | 4.1.3 | 21 | arm64 | 24 passed | Passed |

Every row uses Linux, MySQL Connector/J 8.4.0 and a single-node all-in-one
server. The official Liquibase Test Harness 1.0.12 additionally passed its
native PRIMARY creation, expected SQL, snapshot and rollback case on StarRocks
4.1.3 for both architectures, using its isolated Liquibase 5.0.3 runtime.
The exact signed JAR separately passed the full suite locally on Linux arm64,
Liquibase 5.0.4, StarRocks 4.1.3 and Java 21. A clean checkout without maintainer
files passed 24 tests and produced the same JAR bytes.

The [version registry](compatibility/releases.json) names the scenarios and
records actual source commits, dates, image digests and artifact hashes. The
[release verification record](compatibility/0.3.0-verification.json) also records
the signed artifacts, bundle, clean build and publication status. CI checks out
a PR merge commit; its source identity is preserved separately from the local
artifact build commit. Byte comparison connects that evidence to the candidate.

The suite covers commands, native PRIMARY/DUPLICATE tables, rollback, escaped
metadata/changeset identity, repeated changes, failure/recovery, tag ties and
two concurrent CLI processes. See the [capability contract](docs/capabilities.md)
for boundaries. StarRocks 3.1.7 rejects rename and hyphenated metadata names;
the tests assert those differences. Multi-node failover, shared-data deployments
and lossless diff replay are unverified. Earlier development code passed ten
two-process contention rounds; the final candidate repeats contention in every
matrix row and in its local signed-artifact test.

Read the [upgrade guide](docs/upgrading-0.3.md) before rollout, especially the
new CREATE VIEW/DROP VIEW permissions and rejection of silent type changes.

## Historical 0.1.3 validation

Validation on 2026-09-08 for the released 0.1.3 changes. These results
do not apply retroactively to the published 0.1.2 artifact.

| Liquibase runtime | StarRocks | Integration JDK | Unit tests | Integration |
| --- | --- | --- | --- | --- |
| 4.23.0 | 3.1.7 | 17 | 11 passed | Passed |
| 4.29.1 | 4.1.1 | 21 | 11 passed | Passed |
| 5.0.3 | 4.1.1 | 21 | 11 passed | Passed |

Unit tests ran locally on OpenJDK 17 against each selected Liquibase runtime.
Integration ran in native ARM64 Docker containers with Connector/J 8.4.0.
The CI workflow repeats the matrix on Linux using the JDK in each row.

Integration covers a fresh migration, two no-op updates, one changelog row,
`clear-checksums` followed by recalculation and validation, explicit recovery
of an abandoned lock from a new process, and an update after recovery. Unit
tests additionally cover plugin/type discovery, failed acquisition, release
errors, affected-row validation, metadata key/distribution SQL, and escaped
stored changelog identity during checksum updates.

This is bounded evidence, not full compatibility certification. Rollbacks,
diff/snapshot generation, every change type, multi-node failover and concurrent
distributed migrations are outside this matrix. Lock ownership isolation was
tested at the executor boundary, not under a multi-process stress workload.

## Runtime versus compile dependency in 0.1.3

Version 0.1.3 compiles against Liquibase 4.23.0. Tests and CLI
integration successfully ran that compiled extension with 5.0.3, but directly
changing the compile dependency to 5.0.3 fails because
`hasDatabaseChangeLogLockTable` no longer overrides a superclass method.
Version 0.2.0 addresses that source/API migration by removing the obsolete
override and using core lock-table discovery.
Do not interpret the 0.1.3 matrix as approval to simply replace its compile
dependency version.

## 0.2.0 release

The compile API and default test runtime are now Liquibase 5.0.3. The test
compile API remains 4.23.0 to retain tests that also exercise the oldest
supported runtime; `-PtestLiquibaseVersion` selects the actual test runtime.
The CI matrix builds the new source against 5.0.3 and exercises all three
runtimes with real StarRocks. All three combinations passed on the merged
0.2.0 source in [GitHub Actions](https://github.com/infocusmodereal/liquibase-starrocks/actions/runs/34263979486).
The exact signed release JAR additionally passed local integration with
Liquibase 5.0.3, StarRocks 4.1.1 and JDK 21. The local matrix above records
the initial 0.1.3 validation; the same matrix was repeated in CI for 0.2.0.

[Liquibase 5.0.3 release notes](https://github.com/liquibase/liquibase/releases/tag/v5.0.3)
and its [build configuration](https://github.com/liquibase/liquibase/blob/v5.0.3/pom.xml)
were reviewed. The release targets Java 17 bytecode; integration here uses 21.

## Regressions demonstrated

- Published extension 0.1.2 with Liquibase 4.29.1 / StarRocks 4.1.1 / JDK 21:
  the second update produced `SEVERE: Could not release lock` despite a success
  exit code. The harness failed as intended with explicit INFO logging.
- Before the lock fix, three lock lifecycle unit tests failed. With only the
  PR #5 guard, two recovery tests failed. The completed fix passes all seven
  lock lifecycle tests, including propagation of real failures.
- The checksum regression test failed on the previous literal `8:new_checksum`
  and passes with a real checksum and the escaped stored changeset identity.

The exact StarRocks 4.1.0 image from #6 failed during BE startup, on both
attempted architectures. StarRocks documents this image problem and its fix
in 4.1.1 in the [4.1 release notes](https://docs.starrocks.io/releasenotes/release-4.1/).
Therefore the database-backed reproduction uses 4.1.1, not the exact reported
database version. Both published and patched extensions used that same server
version for the comparison.

## Evidence and release status

Detailed local logs and before/after reports are under ignored
`integration/.cache/review/`. CI uploads test reports and integration logs.
No publishing credentials are required for these checks.

Version 0.1.3 was published to Maven Central on 2026-09-08. The public plugin
JAR, sources JAR, Javadoc JAR and POM were downloaded and their SHA-256 hashes
matched the signed release bundle. The exact signed plugin JAR also passed
the migration, checksum and abandoned-lock recovery integration test before
publication. See the [0.1.3 release](https://github.com/infocusmodereal/liquibase-starrocks/releases/tag/v0.1.3).

Version 0.2.0 was also published to Maven Central and GitHub on 2026-09-08.
Its four public artifacts were downloaded and matched the SHA-256 hashes of
the signed bundle. See the [0.2.0 release](https://github.com/infocusmodereal/liquibase-starrocks/releases/tag/v0.2.0)
and its attached verification record for the source commit and artifact hashes.

Local development builds on the 0.3 branch default to 0.3.0-SNAPSHOT.
Published 0.2.0 is the separate signed release, built with
`-PbaseVersion=0.2.0 -PisRelease=true`.

## Versioned compatibility registry

[compatibility/releases.json](compatibility/releases.json) records the exact
0.2.0 and 0.3.0 combinations, including whether evidence applies to
source-built code or a signed artifact. Earlier 0.1.x evidence remains in this
document; the registry does not reconstruct missing historical provenance.

A passing row covers only its named scenarios, runtime, driver, JDK,
architecture and deployment topology. It does not imply support for every
patch in a version family. A source-built CI pass is not an artifact test.
Missing image digests and local reports are identified explicitly.

The [0.3 roadmap](docs/roadmap-0.3.md) retains the original proposal and records
its implementation. Candidate versions become verified only after actual results
are recorded. The 0.3.0 publication record above includes verification of the public files.
